from datetime import timedelta
from decimal import Decimal
from uuid import uuid4

from django.utils import timezone

from admin_api.kpis import LIMA
from admin_api.reports import financial_flow
from roulette.models import Bet, GameRound, Room
from .helpers import AdminTestBase

URL = "/api/v1/admin/reports/financial-flow"


class HU48FlowTests(AdminTestBase):
    def bet(self, when, amount, payout):
        round = GameRound.objects.create(room=Room.objects.first(), opened_at=when, closes_at=when + timedelta(seconds=15),
                                         status="SETTLED", winning_number=1, settled_at=when + timedelta(seconds=20))
        bet = Bet.objects.create(user=self.player, round=round, kind="STRAIGHT_UP", selection="1", chips=[10],
                                 amount=amount, payout=payout, reference=uuid4())
        Bet.objects.filter(pk=bet.pk).update(created_at=when)

    def test_hu48_series_are_aligned_by_date_with_daily_margin(self):
        now = timezone.now().astimezone(LIMA).replace(hour=10, minute=0, second=0, microsecond=0)
        self.bet(now, Decimal("100"), Decimal("0"))
        self.bet(now - timedelta(days=2), Decimal("50"), Decimal("80"))
        data = financial_flow(30, now)
        self.assertEqual(len(data["days"]), 30)
        dates = [item["date"] for item in data["days"]]
        self.assertEqual(dates, sorted(dates))
        self.assertEqual(dates[-1], now.date().isoformat())
        by_date = {item["date"]: item for item in data["days"]}
        self.assertEqual(by_date[now.date().isoformat()], {"date": now.date().isoformat(), "wagered": "100.00", "paid": "0.00", "net_margin": "100.00"})
        two_days = (now.date() - timedelta(days=2)).isoformat()
        self.assertEqual((by_date[two_days]["wagered"], by_date[two_days]["paid"], by_date[two_days]["net_margin"]), ("50.00", "80.00", "-30.00"))
        self.assertEqual(data["totals"], {"wagered": "150.00", "paid": "80.00", "net_margin": "70.00"})

    def test_hu48_empty_month_returns_zeros(self):
        response = self.client.get(URL)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data["days"]), 30)
        self.assertTrue(all(item["wagered"] == "0.00" and item["paid"] == "0.00" for item in response.data["days"]))
        self.assertEqual(self.client.get(URL + "?days=0").status_code, 400)

    def test_hu48_requires_admin(self):
        self.as_player()
        self.assertEqual(self.client.get(URL).status_code, 403)
