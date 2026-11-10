"""Estadísticas privadas del jugador (HU51, HU52 y HU53)."""
from datetime import datetime, time, timedelta
from decimal import Decimal
from zoneinfo import ZoneInfo

from django.db.models import Count, F, Q
from django.utils import timezone
from rest_framework import serializers
from rest_framework.response import Response

from casino.models import Play
from roulette.models import Bet
from roulette.queries import filter_dates
from roulette.views import AuthenticatedView
from wallet.models import WalletTransaction

LIMA = ZoneInfo("America/Lima")
ZERO = Decimal("0")


def day_bounds(day):
    start = datetime.combine(day, time.min, tzinfo=LIMA)
    return start, start + timedelta(days=1)


def outcome_counts(queryset):
    return queryset.filter(payout__isnull=False).aggregate(
        won=Count("id", filter=Q(payout__gt=F("amount"))),
        lost=Count("id", filter=Q(payout__lt=F("amount"))),
        push=Count("id", filter=Q(payout=F("amount"))))


class WinRateView(AuthenticatedView):
    """HU51: rondas ganadas y perdidas; win rate = ganadas / totales × 100."""

    def get(self, request):
        bets = outcome_counts(filter_dates(Bet.objects.filter(user=request.user), request.query_params))
        plays = outcome_counts(filter_dates(Play.objects.filter(user=request.user), request.query_params))
        won, lost, push = (bets[key] + plays[key] for key in ("won", "lost", "push"))
        total = won + lost + push
        rate = round(won * 100 / total, 2) if total else 0.0
        return Response({"won": won, "lost": lost, "push": push, "total": total, "win_rate": rate,
                         "lost_rate": round(lost * 100 / total, 2) if total else 0.0,
                         "scope": "settled_bets", "timezone": "America/Lima"})
