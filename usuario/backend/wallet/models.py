from django.conf import settings
from django.db import models


class Wallet(models.Model):
    user = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="wallet")
    balance = models.DecimalField(max_digits=18, decimal_places=2, default=0)

    bonus_balance = models.DecimalField(max_digits=18, decimal_places=2, default=0)

    @property
    def winnings_balance(self):
        return self.balance - self.bonus_balance

    class Meta:
        db_table = "wallets"
        constraints = [models.CheckConstraint(condition=models.Q(balance__gte=0), name="wallet_balance_nonnegative"),
            models.CheckConstraint(condition=models.Q(bonus_balance__gte=0) & models.Q(bonus_balance__lte=models.F("balance")), name="wallet_bonus_within_balance"),
        ]


class WalletTransaction(models.Model):
    class Type(models.TextChoices):
        WELCOME_BONUS = "WELCOME_BONUS", "Bono de bienvenida"
        BET = "BET", "Apuesta"
        WIN = "WIN", "Premio"

    wallet = models.ForeignKey(Wallet, on_delete=models.CASCADE, related_name="transactions")
    type = models.CharField(max_length=13, choices=Type.choices)
    amount = models.DecimalField(max_digits=18, decimal_places=2)
    balance_after = models.DecimalField(max_digits=18, decimal_places=2)
    reference = models.CharField(max_length=255, unique=True, null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "wallet_transactions"


class WalletBalance(models.Model):
    wallet = models.OneToOneField(Wallet, primary_key=True, db_column="id", on_delete=models.DO_NOTHING)
    user_id = models.BigIntegerField()
    balance = models.DecimalField(max_digits=18, decimal_places=2)
    bonus_balance = models.DecimalField(max_digits=18, decimal_places=2)
    winnings_balance = models.DecimalField(max_digits=18, decimal_places=2)

    class Meta:
        managed = False
        db_table = "wallet_balances"
