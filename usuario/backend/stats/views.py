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


class BiggestWinView(AuthenticatedView):
    """HU52: mayor ganancia neta obtenida en una sola ronda o jugada."""

    def get(self, request):
        candidates = []
        bet = (Bet.objects.filter(user=request.user, payout__gt=F("amount")).select_related("round__room__game")
               .annotate(profit=F("payout") - F("amount")).order_by("-profit", "created_at").first())
        if bet:
            candidates.append({"net": bet.profit, "payout": bet.payout, "amount": bet.amount, "source": "roulette",
                               "game": bet.round.room.game.title if bet.round.room.game_id else "Ruleta Europea",
                               "round_id": bet.round_id, "bet_id": bet.pk, "created_at": bet.created_at})
        play = (Play.objects.filter(user=request.user, payout__gt=F("amount"))
                .annotate(profit=F("payout") - F("amount")).order_by("-profit", "created_at").first())
        if play:
            candidates.append({"net": play.profit, "payout": play.payout, "amount": play.amount, "source": "casino",
                               "game": play.get_kind_display(), "round_id": None, "bet_id": play.pk,
                               "created_at": play.created_at})
        if not candidates:
            return Response({"record": None})
        best = max(candidates, key=lambda item: (item["net"], -item["created_at"].timestamp()))
        return Response({"record": {**best, "net": format(best["net"], ".2f"), "payout": format(best["payout"], ".2f"),
                                    "amount": format(best["amount"], ".2f"), "created_at": best["created_at"].isoformat()}})
