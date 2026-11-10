"""HU48: flujo diario de créditos apostados y pagados del último mes."""
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
