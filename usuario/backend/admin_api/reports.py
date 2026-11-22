"""Reportes del panel: flujo financiero (HU48) y ranking de popularidad de juegos (HU71)."""
from datetime import timedelta
from decimal import Decimal

from django.db.models import Sum
from django.db.models.functions import TruncDate
from django.utils import timezone
from rest_framework import serializers
from rest_framework.response import Response

from casino.models import Play
from roulette.models import Bet
from .base import AdminView
from .kpis import LIMA, day_bounds, money

ZERO = Decimal("0")


class FlowQuery(serializers.Serializer):
    days = serializers.IntegerField(min_value=1, max_value=90, required=False, default=30)


def grouped(queryset, amount_field):
    """Suma por día de Lima. Apuesta y pago se agrupan por la fecha de la apuesta para que ambas series se alineen."""
    rows = (queryset.annotate(day=TruncDate("created_at", tzinfo=LIMA)).values("day")
            .annotate(total=Sum(amount_field)).order_by("day"))
    return {row["day"]: row["total"] or ZERO for row in rows}


def financial_flow(days=30, now=None):
    now = now or timezone.now()
    last = now.astimezone(LIMA).date()
    first = last - timedelta(days=days - 1)
    start, _ = day_bounds(first)
    _, end = day_bounds(last)
    bets = Bet.objects.filter(created_at__gte=start, created_at__lt=end)
    plays = Play.objects.filter(created_at__gte=start, created_at__lt=end)
    wagered = [grouped(bets, "amount"), grouped(plays, "amount")]
    paid = [grouped(bets.filter(payout__isnull=False), "payout"), grouped(plays.filter(payout__isnull=False), "payout")]
    series = []
    for offset in range(days):
        day = first + timedelta(days=offset)
        w = sum((source.get(day, ZERO) for source in wagered), ZERO)
        p = sum((source.get(day, ZERO) for source in paid), ZERO)
        series.append({"date": day.isoformat(), "wagered": money(w), "paid": money(p), "net_margin": money(w - p)})
    totals = {key: money(sum((Decimal(item[key]) for item in series), ZERO)) for key in ("wagered", "paid", "net_margin")}
    return {"from": first.isoformat(), "to": last.isoformat(), "timezone": "America/Lima", "days": series, "totals": totals}


class FinancialFlowView(AdminView):
    def get(self, request):
        query = FlowQuery(data=request.query_params)
        query.is_valid(raise_exception=True)
        return Response(financial_flow(query.validated_data["days"]))


# HU71: ranking de popularidad de juegos.
class RankingQuery(serializers.Serializer):
    days = serializers.ChoiceField(choices=[7, 30], required=False, default=7)


def lima_range(days, now=None):
    """Últimos `days` días de Lima, incluido hoy: (primer día, último día, inicio, fin)."""
    now = now or timezone.now()
    last = now.astimezone(LIMA).date()
    first = last - timedelta(days=days - 1)
    return first, last, day_bounds(first)[0], day_bounds(last)[1]


def games_ranking(start, end):
    """Rondas jugadas, jugadores únicos y créditos apostados por juego entre start y end (fin excluido)."""
    from roulette.game_activity import activity_by_game
    from roulette.models import Game
    activity = activity_by_game(Bet.objects.filter(created_at__gte=start, created_at__lt=end),
                                Play.objects.filter(created_at__gte=start, created_at__lt=end))
    games = Game.objects.filter(pk__in=list(activity)) | Game.objects.filter(deleted_at__isnull=True)
    total = sum((item.wagered for item in activity.values()), ZERO)
    rows = []
    for game in games.distinct():
        item = activity.get(game.pk)
        wagered = item.wagered if item else ZERO
        rows.append({"game_id": game.pk, "title": game.title, "category": game.category,
                     "active": game.deleted_at is None and game.is_published,
                     "rounds": item.rounds if item else 0, "unique_players": len(item.players) if item else 0,
                     "wagered": money(wagered), "share_pct": float(round(wagered * 100 / total, 2)) if total else 0.0})
    rows.sort(key=lambda row: (-Decimal(row["wagered"]), -row["rounds"], row["title"]))
    return {"games": rows, "totals": {"rounds": sum(row["rounds"] for row in rows), "wagered": money(total),
                                      "unique_players": len(set().union(*(item.players for item in activity.values())))}}


class GamesRankingView(AdminView):
    def get(self, request):
        query = RankingQuery(data=request.query_params)
        query.is_valid(raise_exception=True)
        days = query.validated_data["days"]
        first, last, start, end = lima_range(days)
        return Response({"days": days, "from": first.isoformat(), "to": last.isoformat(), "timezone": "America/Lima",
                         **games_ranking(start, end)})
