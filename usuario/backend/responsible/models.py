from django.db import models
from django.conf import settings
from django.db.models import Q


class ExcludedPerson(models.Model):
    dni_hash = models.CharField(max_length=64, unique=True)
    reason = models.CharField(max_length=255, blank=True)
    source = models.CharField(max_length=120)
    created_at = models.DateTimeField(auto_now_add=True)


class SelfExclusion(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    days = models.PositiveSmallIntegerField(choices=[(7, "7"), (30, "30"), (90, "90")])
    created_at = models.DateTimeField(auto_now_add=True)
    excluded_until = models.DateTimeField()

    class Meta:
        constraints = [models.CheckConstraint(condition=Q(days__in=[7, 30, 90]), name="self_exclusion_valid_days")]


class RiskIncident(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    round = models.ForeignKey("roulette.GameRound", on_delete=models.PROTECT)
    created_at = models.DateTimeField(auto_now_add=True)
    kind = models.CharField(max_length=40, default="LOSS_ESCALATION")
    evidence = models.JSONField(default=list)
    pause_started_at = models.DateTimeField(null=True)

    class Meta:
        constraints = [models.UniqueConstraint(fields=["user", "round", "kind"], name="unique_risk_incident_round")]


class DailyLimit(models.Model):
    user = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="daily_limit")
    amount = models.DecimalField(max_digits=12, decimal_places=2, null=True)
    pending_amount = models.DecimalField(max_digits=12, decimal_places=2, null=True)
    effective_at = models.DateTimeField(null=True)

    class Meta:
        constraints = [
            models.CheckConstraint(condition=Q(amount__isnull=True) | Q(amount__gte=0), name="daily_limit_nonnegative"),
            models.CheckConstraint(condition=(Q(pending_amount__isnull=True, effective_at__isnull=True)
                | Q(pending_amount__gte=0, effective_at__isnull=False)), name="daily_limit_pending_valid"),
        ]


class DailySpending(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    date = models.DateField()
    amount = models.DecimalField(max_digits=12, decimal_places=2, default=0)

    class Meta:
        constraints = [models.UniqueConstraint(fields=["user", "date"], name="unique_daily_spending"),
                       models.CheckConstraint(condition=Q(amount__gte=0), name="spending_nonnegative")]
