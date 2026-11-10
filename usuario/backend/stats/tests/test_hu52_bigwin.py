from .helpers import StatsTestBase


class HU52BiggestWinTests(StatsTestBase):
    def test_hu52_returns_none_without_wins(self):
        self.bet("10", "0")
        self.assertIsNone(self.client.get("/api/v1/stats/biggest-win").data["record"])

    def test_hu52_updates_when_a_bigger_win_is_registered(self):
        self.bet("10", "20")
        record = self.client.get("/api/v1/stats/biggest-win").data["record"]
        self.assertEqual((record["net"], record["payout"]), ("10.00", "20.00"))
        bigger = self.bet("10", "360")
        record = self.client.get("/api/v1/stats/biggest-win").data["record"]
        self.assertEqual((record["net"], record["payout"], record["bet_id"]), ("350.00", "360.00", bigger.pk))
        self.assertEqual(record["round_id"], bigger.round_id)
