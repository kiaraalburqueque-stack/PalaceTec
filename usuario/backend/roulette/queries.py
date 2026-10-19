from datetime import datetime, time, timedelta
from zoneinfo import ZoneInfo
from rest_framework import serializers


class DateRangeInput(serializers.Serializer):
    startDate = serializers.DateField(required=False, input_formats=["%Y-%m-%d"])
    endDate = serializers.DateField(required=False, input_formats=["%Y-%m-%d"])

    def validate(self, attrs):
        if attrs.get("startDate") and attrs.get("endDate") and attrs["startDate"] > attrs["endDate"]:
            raise serializers.ValidationError({"endDate": "La fecha final debe ser igual o posterior al inicio."})
        if attrs.get("endDate") and attrs["endDate"].year == 9999:
            raise serializers.ValidationError({"endDate": "La fecha final excede el rango permitido."})
        return attrs


def filter_dates(queryset, params):
    serializer = DateRangeInput(data=params)
    serializer.is_valid(raise_exception=True)
    dates = serializer.validated_data
    lima = ZoneInfo("America/Lima")
    if "startDate" in dates:
        queryset = queryset.filter(created_at__gte=datetime.combine(dates["startDate"], time.min, tzinfo=lima))
    if "endDate" in dates:
        queryset = queryset.filter(created_at__lt=datetime.combine(dates["endDate"]+timedelta(days=1), time.min, tzinfo=lima))
    return queryset


def settled_summary(queryset):
    from decimal import Decimal
    from django.db.models import Sum
    values = queryset.filter(round__status="SETTLED", payout__isnull=False).aggregate(
        total_wagered=Sum("amount"), total_paid=Sum("payout"))
    wagered = values["total_wagered"] or Decimal("0")
    paid = values["total_paid"] or Decimal("0")
    return {"total_wagered": format(wagered, ".2f"), "total_paid": format(paid, ".2f"),
            "net": format(paid-wagered, ".2f")}
