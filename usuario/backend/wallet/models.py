from django.conf import settings
from django.db import models


class Wallet(models.Model):
    user = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="wallet")
    balance = models.DecimalField(max_digits=18, decimal_places=2, default=0)

    class Meta:
        db_table = "wallets"
        constraints = [models.CheckConstraint(condition=models.Q(balance__gte=0), name="wallet_balance_nonnegative")]


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
