from accounts.models import User
from .helpers import StatsTestBase


class HU51WinRateTests(StatsTestBase):
    def test_hu51_counts_won_and_lost_and_computes_percentage(self):
        self.bet("10", "360")
        self.bet("10", "0")
        self.bet("20", "0")
        self.bet("50", "100")
        self.bet("10", None)  # pendiente: no cuenta
        other = User.objects.create_user(email="otro@palacetec.pe", dni="22222222", birth_date="2000-01-01")
        self.bet("10", "360", user=other)
        data = self.client.get("/api/v1/stats/win-rate").data
        self.assertEqual((data["won"], data["lost"], data["total"]), (2, 2, 4))
        self.assertEqual(data["win_rate"], 50.0)
        self.assertEqual(data["lost_rate"], 50.0)

    def test_hu51_exact_rounding_and_empty(self):
        self.assertEqual(self.client.get("/api/v1/stats/win-rate").data["win_rate"], 0.0)
        self.bet("10", "20")
        self.bet("10", "0")
        self.bet("10", "0")
        self.assertEqual(self.client.get("/api/v1/stats/win-rate").data["win_rate"], 33.33)

    def test_hu51_requires_login(self):
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get("/api/v1/stats/win-rate").status_code, 401)
