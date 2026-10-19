from rest_framework import serializers
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
from accounts.authentication import CookieJWTAuthentication
from accounts.permissions import IsAdminRole
from .models import ExcludedPerson
from .services import ExclusionService


class ResponsibleView(APIView):
    authentication_classes = [CookieJWTAuthentication]
    permission_classes = [IsAuthenticated]


class DailyLimitInput(serializers.Serializer):
    amount = serializers.DecimalField(max_digits=12, decimal_places=2, min_value=0)


class DailyLimitView(ResponsibleView):
    def get(self, request):
        from .services import LimitService
        return Response(LimitService.summary(request.user))

    def post(self, request):
        from .services import LimitService
        from django.db import transaction
        from accounts.models import User
        serializer = DailyLimitInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        with transaction.atomic():
            user = User.objects.select_for_update().get(pk=request.user.pk)
            if request.auth:
                CookieJWTAuthentication().get_user(request.auth)
            return Response(LimitService.set_limit(user, serializer.validated_data["amount"]))


class SelfExclusionInput(serializers.Serializer):
    days = serializers.ChoiceField(choices=[7, 30, 90])


class SelfExcludeView(ResponsibleView):
    def post(self, request):
        from datetime import timedelta
        from django.conf import settings
        from django.db import transaction
        from django.utils import timezone
        from accounts.models import User, AuthSession
        from realtime.events import after_commit
        from .models import SelfExclusion
        serializer = SelfExclusionInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        with transaction.atomic():
            user = User.objects.select_for_update().get(pk=request.user.pk)
            if request.auth:
                CookieJWTAuthentication().get_user(request.auth)
            ExclusionService.ensure_user_allowed(user)
            user.self_excluded_until = timezone.now()+timedelta(days=serializer.validated_data["days"])
            user.auth_version += 1
            user.save(update_fields=["self_excluded_until", "auth_version"])
            SelfExclusion.objects.create(user=user, days=serializer.validated_data["days"], excluded_until=user.self_excluded_until)
            AuthSession.objects.filter(user=user, revoked_at__isnull=True).update(revoked_at=timezone.now())
            after_commit(f"user.{user.pk}", "session:revoked", {})
        response = Response({"self_excluded_until": user.self_excluded_until.isoformat(), "detail": "Autoexclusion confirmada. No se puede cancelar anticipadamente."})
        from accounts.sessions import clear_session_cookies
        clear_session_cookies(response)
        return response


class SessionSummaryView(ResponsibleView):
    def get(self, request):
        from django.utils import timezone
        from django.db.models import Sum
        from accounts.models import AuthSession
        from roulette.queries import settled_summary
        from rest_framework.exceptions import NotAuthenticated
        if not request.auth:
            raise NotAuthenticated("Inicia sesion para consultar el resumen.")
        from accounts.sessions import session_key
        session = AuthSession.objects.get(jti=session_key(request.auth), user=request.user)
        pending = session.bets.filter(payout__isnull=True)
        return Response({**settled_summary(session.bets.all()),
            "session_started_at": session.started_at.isoformat(),
            "duration_seconds": max(0, int((timezone.now()-session.started_at).total_seconds())),
            "pending_wagered": format(pending.aggregate(total=Sum("amount"))["total"] or 0, ".2f"),
            "pending_count": pending.count(), "reality_check_interval_seconds": 3600,
            "scope": "settled_session_bets"})


class PauseInput(serializers.Serializer):
    incident_id = serializers.IntegerField(min_value=1)


class PauseView(ResponsibleView):
    def post(self, request):
        from datetime import timedelta
        from django.db import transaction
        from django.shortcuts import get_object_or_404
        from django.utils import timezone
        from accounts.models import User
        from .models import RiskIncident
        serializer = PauseInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        with transaction.atomic():
            user = User.objects.select_for_update().get(pk=request.user.pk)
            if request.auth:
                CookieJWTAuthentication().get_user(request.auth)
            incident = get_object_or_404(RiskIncident, pk=serializer.validated_data["incident_id"], user=user)
            if incident.pause_started_at is None:
                incident.pause_started_at = timezone.now()
                incident.save(update_fields=["pause_started_at"])
                user.pause_until = max(user.pause_until or timezone.now(), timezone.now()+timedelta(minutes=10))
                user.save(update_fields=["pause_until"])
        return Response({"pause_until": user.pause_until.isoformat() if user.pause_until else None})


class RiskAlertsView(ResponsibleView):
    def get(self, request):
        from .models import RiskIncident
        return Response({"pause_until": request.user.pause_until.isoformat() if request.user.pause_until else None,
            "results": [{"incident_id": incident.pk, "created_at": incident.created_at.isoformat(),
                "pause_started_at": incident.pause_started_at.isoformat() if incident.pause_started_at else None,
                "suggested_pause_seconds": 600}
                for incident in RiskIncident.objects.filter(user=request.user).order_by("-id")[:20]]})


class ExcludedPersonInput(serializers.Serializer):
    dni = serializers.RegexField(r"\A[0-9]{8}\Z", trim_whitespace=False, write_only=True)
    source = serializers.CharField(max_length=120)
    reason = serializers.CharField(max_length=255, required=False, allow_blank=True)


class ExcludedPeopleView(ResponsibleView):
    permission_classes = [IsAuthenticated, IsAdminRole]

    def post(self, request):
        from django.db import transaction
        from accounts.models import User, AuthSession
        from django.utils import timezone
        from realtime.events import after_commit
        serializer = ExcludedPersonInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        with transaction.atomic():
            # Mismo candado que usa place_bet: una apuesta aceptada antes se liquida.
            users = list(User.objects.select_for_update().filter(dni=data["dni"]))
            excluded, created = ExcludedPerson.objects.get_or_create(
                dni_hash=ExclusionService.digest(data["dni"]),
                defaults={"source": data["source"], "reason": data.get("reason", "")})
            for user in users:
                AuthSession.objects.filter(user=user, revoked_at__isnull=True).update(revoked_at=timezone.now())
                after_commit(f"user.{user.pk}", "session:revoked", {})
        return Response({"id": excluded.pk, "created": created}, status=201 if created else 200)
