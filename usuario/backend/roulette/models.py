from django.conf import settings
from django.db import models
from django.db.models import Q


class Room(models.Model):
    name = models.CharField(max_length=80)
    is_active = models.BooleanField(default=True)
    min_bet = models.DecimalField(max_digits=12, decimal_places=2, default=10)
    max_bet = models.DecimalField(max_digits=12, decimal_places=2, default=500)

    class Meta:
        db_table = "rooms"
        constraints = [models.CheckConstraint(condition=Q(min_bet__gt=0) & Q(max_bet__gte=models.F("min_bet")), name="room_valid_bet_limits")]


class GameRound(models.Model):
    class Status(models.TextChoices):
        OPEN = "OPEN", "Abierta"
        CLOSED = "CLOSED", "Cerrada"
        SETTLED = "SETTLED", "Liquidada"

    room = models.ForeignKey(Room, on_delete=models.PROTECT, related_name="rounds")
    status = models.CharField(max_length=7, choices=Status.choices, default=Status.OPEN)
    opened_at = models.DateTimeField()
    closes_at = models.DateTimeField()
    winning_number = models.PositiveSmallIntegerField(null=True, blank=True)
    settled_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "game_rounds"
        constraints = [
            models.UniqueConstraint(fields=["room"], condition=~Q(status="SETTLED"), name="one_active_round_per_room"),
            models.CheckConstraint(condition=Q(closes_at__gt=models.F("opened_at")), name="round_valid_deadline"),
            models.CheckConstraint(condition=Q(winning_number__isnull=True) | Q(winning_number__lte=36), name="round_number_range"),
            models.CheckConstraint(condition=(Q(status="SETTLED", winning_number__isnull=False, settled_at__isnull=False) | Q(status__in=["OPEN", "CLOSED"], winning_number__isnull=True, settled_at__isnull=True)), name="round_valid_result_state"),
        ]


class Bet(models.Model):
    class Kind(models.TextChoices):
        STRAIGHT_UP = "STRAIGHT_UP", "Pleno"
        COLOR = "COLOR", "Color"
        PARITY = "PARITY", "Paridad"
        DOZEN = "DOZEN", "Docena"
        COLUMN = "COLUMN", "Columna"

    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="bets")
    round = models.ForeignKey(GameRound, on_delete=models.PROTECT, related_name="bets")
    kind = models.CharField(max_length=11, choices=Kind.choices)
    selection = models.CharField(max_length=5)
    chips = models.JSONField()
    amount = models.DecimalField(max_digits=12, decimal_places=2)
    payout = models.DecimalField(max_digits=18, decimal_places=2, null=True)
    reference = models.UUIDField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "user_bets"
        constraints = [
            models.UniqueConstraint(fields=["user", "reference"], name="unique_user_bet_reference"),
            models.CheckConstraint(condition=Q(amount__gt=0), name="bet_positive_amount"),
            models.CheckConstraint(condition=Q(payout__isnull=True) | Q(payout__gte=0), name="bet_nonnegative_payout"),
        ]


class RoomConnection(models.Model):
    room = models.ForeignKey(Room, on_delete=models.CASCADE, related_name="connections")
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    channel_name = models.CharField(max_length=255, unique=True)
    expires_at = models.DateTimeField(db_index=True)

    class Meta:
        db_table = "room_connections"
