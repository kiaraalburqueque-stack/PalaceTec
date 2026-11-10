"""HU47: indicadores del casino para el dashboard de administración."""
from datetime import datetime, time, timedelta
from decimal import Decimal
from zoneinfo import ZoneInfo

from django.core.cache import cache
from django.db.models import Sum
from django.utils import timezone
from rest_framework.response import Response

from accounts.models import AuthSession
from casino.models import Play
from roulette.models import Bet, RoomConnection
from .base import AdminView

LIMA = ZoneInfo("America/Lima")
CACHE_KEY = "admin:kpis"
CACHE_SECONDS = 60
ZERO = Decimal("0")


def day_bounds(day):
    start = datetime.combine(day, time.min, tzinfo=LIMA)
    return start, start + timedelta(days=1)


def money(value):
    return format(value or ZERO, ".2f")


def day_totals(day):
    """Créditos apostados (al registrar la apuesta) y pagados (al liquidarla) en un día de Lima."""
    start, end = day_bounds(day)
    wagered = ((Bet.objects.filter(created_at__gte=start, created_at__lt=end).aggregate(v=Sum("amount"))["v"] or ZERO)
               + (Play.objects.filter(created_at__gte=start, created_at__lt=end).aggregate(v=Sum("amount"))["v"] or ZERO))
    paid = ((Bet.objects.filter(round__settled_at__gte=start, round__settled_at__lt=end, payout__isnull=False)
             .aggregate(v=Sum("payout"))["v"] or ZERO)
            + (Play.objects.filter(settled_at__gte=start, settled_at__lt=end).aggregate(v=Sum("payout"))["v"] or ZERO))
    return wagered, paid


def change(today, yesterday):
    """Variación porcentual contra el día anterior; None si ayer fue cero."""
    if not yesterday:
        return None
    return float(round((Decimal(today) - Decimal(yesterday)) / abs(Decimal(yesterday)) * 100, 2))


def online_users(now):
    sessions = set(AuthSession.objects.filter(revoked_at__isnull=True, expires_at__gt=now).values_list("user_id", flat=True))
    tables = set(RoomConnection.objects.filter(expires_at__gt=now).values_list("user_id", flat=True))
    return len(sessions | tables)


def compute_kpis(now=None):
    now = now or timezone.now()
    today = now.astimezone(LIMA).date()
    wagered, paid = day_totals(today)
    prev_wagered, prev_paid = day_totals(today - timedelta(days=1))
    profit, prev_profit = wagered - paid, prev_wagered - prev_paid
    return {
        "date": today.isoformat(), "timezone": "America/Lima", "generated_at": now.isoformat(),
        "online_users": online_users(now),
        "wagered_today": money(wagered), "paid_today": money(paid), "profit_today": money(profit),
        "wagered_yesterday": money(prev_wagered), "paid_yesterday": money(prev_paid), "profit_yesterday": money(prev_profit),
        "wagered_change_pct": change(wagered, prev_wagered),
        "paid_change_pct": change(paid, prev_paid),
        "profit_change_pct": change(profit, prev_profit),
    }


class KpiView(AdminView):
    def get(self, request):
        data = cache.get(CACHE_KEY)
        cached = data is not None
        if not cached:
            data = compute_kpis()
            cache.set(CACHE_KEY, data, CACHE_SECONDS)
        return Response({**data, "cached": cached, "cache_seconds": CACHE_SECONDS})
