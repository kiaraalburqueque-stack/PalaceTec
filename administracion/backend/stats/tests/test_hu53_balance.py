from datetime import timedelta

from django.utils import timezone

from stats.views import LIMA
from wallet.models import WalletTransaction
from .helpers import StatsTestBase


class HU53BalanceHistoryTests(StatsTestBase):
    def tx(self, days_ago, balance, kind="WIN"):
        when = timezone.now().astimezone(LIMA).replace(hour=12) - timedelta(days=days_ago)
        entry = WalletTransaction.objects.create(wallet=self.wallet, type=kind, amount=10, balance_after=balance)
        WalletTransaction.objects.filter(pk=entry.pk).update(created_at=when)

    def test_hu53_series_is_ordered_and_carries_balance(self):
        self.tx(10, "1000.00", "WELCOME_BONUS")
        self.tx(5, "900.00", "BET")
        self.tx(2, "1250.00")
        self.tx(0, "1200.00", "BET")
        data = self.client.get("/api/v1/stats/balance-history?period=7").data
        dates = [point["date"] for point in data["points"]]
        self.assertEqual(len(dates), 7)
        self.assertEqual(dates, sorted(dates))
        self.assertEqual(data["opening_balance"], "1000.00")
        balances = [point["balance"] for point in data["points"]]
        self.assertEqual(balances, ["1000.00", "900.00", "900.00", "900.00", "1250.00", "1250.00", "1200.00"])
        self.assertEqual(data["closing_balance"], "1200.00")

    def test_hu53_thirty_days_with_correct_opening_balance(self):
        self.tx(40, "1000.00", "WELCOME_BONUS")
        self.tx(20, "800.00", "BET")
        data = self.client.get("/api/v1/stats/balance-history?period=30").data
        self.assertEqual(len(data["points"]), 30)
        self.assertEqual(data["opening_balance"], "1000.00")
        self.assertEqual(data["points"][0]["balance"], "1000.00")
        self.assertEqual(data["points"][-1]["balance"], "800.00")
        self.assertEqual(self.client.get("/api/v1/stats/balance-history?period=15").status_code, 400)

    def test_hu53_new_user_starts_at_zero(self):
        data = self.client.get("/api/v1/stats/balance-history").data
        self.assertEqual(data["opening_balance"], "0.00")
        self.assertTrue(all(point["balance"] == "0.00" for point in data["points"]))
