from decimal import Decimal
from django.db import IntegrityError, transaction
from rest_framework.test import APITestCase
from accounts.models import User
from .models import Wallet
from .services import WalletService


class HU06HU07BonusTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="bonus@example.com", dni="12345678", birth_date="2000-01-01")
        self.wallet = Wallet.objects.create(user=self.user, balance=1100, bonus_balance=1000)
        self.client.force_authenticate(self.user)

    def test_bonus_consumed_before_winnings(self):
        WalletService.debit(self.user, 1050, "consume")
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.bonus_balance, 0)
        self.assertEqual(self.wallet.winnings_balance, 50)
        WalletService.credit(self.user, 200, "prize")
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.bonus_balance, 0)
        self.assertEqual(self.wallet.winnings_balance, 250)

    def test_partial_bonus_debit_is_idempotent(self):
        WalletService.debit(self.user, 100, "repeat")
        WalletService.debit(self.user, 100, "repeat")
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.bonus_balance, 900)
        self.assertEqual(self.wallet.balance, 1000)

    def test_summary_and_precheck(self):
        self.assertEqual(self.client.get("/api/v1/wallet/summary").data,
                         {"balance": "1100.00", "bonus_balance": "1000.00", "winnings_balance": "100.00"})
        for amount, expected in ((1100, True), (1101, False)):
            response = self.client.post("/api/v1/wallet/pre-bet-check", {"amount": amount})
            self.assertEqual(response.data["can_bet"], expected)
        self.assertEqual(self.client.post("/api/v1/wallet/pre-bet-check", {"amount": 0}).status_code, 400)

    def test_database_rejects_invalid_bonus(self):
        for amount in (-1, 1101):
            with self.assertRaises(IntegrityError), transaction.atomic():
                Wallet.objects.filter(pk=self.wallet.pk).update(bonus_balance=amount)
