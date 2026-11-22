from django.conf import settings
from django.db import models
from django.db.models import Q


class Play(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    session = models.ForeignKey("accounts.AuthSession", null=True, on_delete=models.SET_NULL, related_name="game_plays")
    kind = models.CharField(max_length=12, choices=[("BLACKJACK", "Blackjack"), ("SPORTS", "Deportes simulados")])
    amount = models.DecimalField(max_digits=12, decimal_places=2)
    payout = models.DecimalField(max_digits=18, decimal_places=2, null=True)
    reference = models.UUIDField()
    description = models.CharField(max_length=200, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    settled_at = models.DateTimeField(null=True)

    class Meta:
        constraints = [models.UniqueConstraint(fields=["user", "reference"], name="unique_casino_reference"),
            models.CheckConstraint(condition=Q(amount__gte=10, amount__lte=500), name="casino_stake_limits"),
            models.CheckConstraint(condition=Q(payout__isnull=True, settled_at__isnull=True) | Q(payout__gte=0, settled_at__isnull=False), name="casino_settlement_state")]


class BlackjackHand(models.Model):
    play = models.OneToOneField(Play, on_delete=models.PROTECT, related_name="blackjack")
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    deck = models.JSONField()
    player = models.JSONField(default=list)
    dealer = models.JSONField(default=list)
    active = models.BooleanField(default=True)
    revision = models.PositiveIntegerField(default=0)
    outcome = models.CharField(max_length=16, blank=True)
    last_action_at = models.DateTimeField()

    class Meta:
        constraints = [models.UniqueConstraint(fields=["user"], condition=Q(active=True), name="one_open_blackjack_hand")]


class DemoSchedule(models.Model):
    """Candado compartido para generar el calendario ficticio una sola vez."""
    id = models.PositiveSmallIntegerField(primary_key=True, default=1)


class SportEvent(models.Model):
    home = models.CharField(max_length=80)
    away = models.CharField(max_length=80)
    starts_at = models.DateTimeField(db_index=True)
    ends_at = models.DateTimeField()
    home_odds = models.DecimalField(max_digits=5, decimal_places=2, default="1.90")
    draw_odds = models.DecimalField(max_digits=5, decimal_places=2, default="3.20")
    away_odds = models.DecimalField(max_digits=5, decimal_places=2, default="2.40")
    home_score = models.PositiveSmallIntegerField(null=True)
    away_score = models.PositiveSmallIntegerField(null=True)
    settled_at = models.DateTimeField(null=True, db_index=True)

    class Meta:
        constraints = [models.CheckConstraint(condition=Q(ends_at__gt=models.F("starts_at")), name="sports_event_time_order")]


class SportTicket(models.Model):
    play = models.OneToOneField(Play, on_delete=models.PROTECT, related_name="ticket")
    event = models.ForeignKey(SportEvent, on_delete=models.PROTECT, related_name="tickets")
    selection = models.CharField(max_length=4, choices=[("HOME", "Local"), ("DRAW", "Empate"), ("AWAY", "Visitante")])
    odds = models.DecimalField(max_digits=5, decimal_places=2)
