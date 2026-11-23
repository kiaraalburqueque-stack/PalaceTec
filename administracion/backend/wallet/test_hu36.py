from decimal import Decimal
from rest_framework.test import APITestCase
from accounts.models import User
from .models import Wallet


class HU36LowBalanceTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email='low@example.com', dni='12345678', birth_date='2000-01-01')
        self.wallet = Wallet.objects.create(user=self.user, balance=10, bonus_balance=0)
        self.client.force_authenticate(self.user)

    def test_threshold_exactly_ten_and_below(self):
        for balance, low in [(Decimal('10'), False), (Decimal('9.99'), True), (Decimal('0'), True)]:
            self.wallet.balance = balance
            self.wallet.save()
            for url in ['/api/v1/wallet/balance', '/api/v1/wallet/summary']:
                response = self.client.get(url)
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.data['low_balance_threshold'], '10.00')
                self.assertEqual(response.data['is_low_balance'], low)
