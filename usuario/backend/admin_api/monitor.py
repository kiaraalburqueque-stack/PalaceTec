"""HU46: monitoreo de juego responsable para el administrador."""
from datetime import timedelta
from decimal import Decimal

from django.db.models import Count, DecimalField, Max, OuterRef, Q, Subquery, Value
from django.db.models.functions import Coalesce
from django.utils import timezone
from rest_framework import serializers

from accounts.models import User
from responsible.models import DailySpending
from responsible.services import LimitService
from .base import AdminPagination, AdminView

RISK_WINDOW = timedelta(days=30)
STATUS_LABELS = {"LIMIT_ACTIVE": "Límite activo", "SELF_EXCLUDED": "Autoexcluido", "RISK_ALERT": "Con alerta de riesgo"}


class MonitorQuery(serializers.Serializer):
    status = serializers.ChoiceField(choices=list(STATUS_LABELS), required=False)
    search = serializers.CharField(required=False, allow_blank=True, max_length=254)


def monitored_users(now=None):
    now = now or timezone.now()
    since = now - RISK_WINDOW
    spent_today = DailySpending.objects.filter(user=OuterRef("pk"), date=LimitService.day(now)).values("amount")[:1]
    return (User.objects
            .select_related("daily_limit")
            .annotate(
                risk_alerts=Count("riskincident", filter=Q(riskincident__created_at__gte=since), distinct=True),
                last_alert_at=Max("riskincident__created_at"),
                spent_today=Coalesce(Subquery(spent_today), Value(Decimal("0")), output_field=DecimalField(max_digits=12, decimal_places=2)))
            .filter(Q(daily_limit__amount__isnull=False) | Q(self_excluded_until__gt=now)
                    | Q(pause_until__gt=now) | Q(riskincident__created_at__gte=since))
            .distinct())


def user_statuses(user, now):
    statuses = []
    if getattr(user, "daily_limit", None) and user.daily_limit.amount is not None:
        statuses.append("LIMIT_ACTIVE")
    if user.self_excluded_until and user.self_excluded_until > now:
        statuses.append("SELF_EXCLUDED")
    if user.risk_alerts or (user.pause_until and user.pause_until > now):
        statuses.append("RISK_ALERT")
    return statuses


def row(user, now):
    limit = getattr(user, "daily_limit", None)
    amount = limit.amount if limit else None
    statuses = user_statuses(user, now)
    return {
        "id": user.pk, "email": user.email, "dni": user.dni, "nickname": user.nickname,
        "is_active": user.is_active,
        "daily_limit": format(amount, ".2f") if amount is not None else None,
        "spent_today": format(user.spent_today, ".2f"),
        "remaining_today": format(max(Decimal("0"), amount - user.spent_today), ".2f") if amount is not None else None,
        "self_excluded_until": user.self_excluded_until.isoformat() if user.self_excluded_until and user.self_excluded_until > now else None,
        "pause_until": user.pause_until.isoformat() if user.pause_until and user.pause_until > now else None,
        "risk_alerts": user.risk_alerts,
        "last_alert_at": user.last_alert_at.isoformat() if user.last_alert_at else None,
        "statuses": statuses,
        "badges": [STATUS_LABELS[status] for status in statuses],
    }


class ResponsibleMonitorView(AdminView):
    def get(self, request):
        query = MonitorQuery(data=request.query_params)
        query.is_valid(raise_exception=True)
        now = timezone.now()
        users = monitored_users(now)
        status = query.validated_data.get("status")
        if status == "LIMIT_ACTIVE":
            users = users.filter(daily_limit__amount__isnull=False)
        elif status == "SELF_EXCLUDED":
            users = users.filter(self_excluded_until__gt=now)
        elif status == "RISK_ALERT":
            users = users.filter(Q(risk_alerts__gt=0) | Q(pause_until__gt=now))
        search = (query.validated_data.get("search") or "").strip()
        if search:
            users = users.filter(Q(email__icontains=search) | Q(dni__startswith=search))
        pagination = AdminPagination()
        page = pagination.paginate_queryset(users.order_by("-last_alert_at", "email"), request, view=self)
        response = pagination.get_paginated_response([row(user, now) for user in page])
        response.data["summary"] = {
            "limit_active": monitored_users(now).filter(daily_limit__amount__isnull=False).count(),
            "self_excluded": User.objects.filter(self_excluded_until__gt=now).count(),
            "risk_alert": monitored_users(now).filter(Q(risk_alerts__gt=0) | Q(pause_until__gt=now)).count(),
        }
        return response
