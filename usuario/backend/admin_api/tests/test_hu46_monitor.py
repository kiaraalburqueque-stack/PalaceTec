from datetime import timedelta

from django.utils import timezone

from responsible.models import DailyLimit, DailySpending, RiskIncident
from responsible.services import LimitService
from roulette.models import GameRound, Room
from .helpers import AdminTestBase, make_user

URL = "/api/v1/admin/responsible-gaming/users"


class HU46MonitorTests(AdminTestBase):
    def setUp(self):
        super().setUp()
        now = timezone.now()
        DailyLimit.objects.create(user=self.player, amount=200)
        DailySpending.objects.create(user=self.player, date=LimitService.day(now), amount=150)
        self.excluded = make_user("excluido@palacetec.pe", "50000000", self_excluded_until=now + timedelta(days=7))
        self.risky = make_user("riesgo@palacetec.pe", "60000000")
        round = GameRound.objects.create(room=Room.objects.first(), opened_at=now, closes_at=now + timedelta(seconds=15))
        RiskIncident.objects.create(user=self.risky, round=round, evidence=[{"amount": "100.00"}])
        make_user("tranquilo@palacetec.pe", "70000000")

    def test_hu46_lists_only_monitored_users_with_badges(self):
        response = self.client.get(URL)
        self.assertEqual(response.status_code, 200)
        rows = {row["email"]: row for row in response.data["results"]}
        self.assertEqual(set(rows), {"jugador@palacetec.pe", "excluido@palacetec.pe", "riesgo@palacetec.pe"})
        player = rows["jugador@palacetec.pe"]
        self.assertEqual((player["daily_limit"], player["spent_today"], player["remaining_today"]), ("200.00", "150.00", "50.00"))
        self.assertEqual(player["badges"], ["Límite activo"])
        self.assertEqual(rows["excluido@palacetec.pe"]["statuses"], ["SELF_EXCLUDED"])
        self.assertEqual(rows["riesgo@palacetec.pe"]["statuses"], ["RISK_ALERT"])
        self.assertEqual(rows["riesgo@palacetec.pe"]["risk_alerts"], 1)
        self.assertEqual(response.data["summary"], {"limit_active": 1, "self_excluded": 1, "risk_alert": 1})

    def test_hu46_filters_by_status(self):
        data = self.client.get(URL + "?status=SELF_EXCLUDED").data
        self.assertEqual([row["email"] for row in data["results"]], ["excluido@palacetec.pe"])
        data = self.client.get(URL + "?status=RISK_ALERT").data
        self.assertEqual([row["email"] for row in data["results"]], ["riesgo@palacetec.pe"])

    def test_hu46_requires_admin(self):
        self.as_player()
        self.assertEqual(self.client.get(URL).status_code, 403)
