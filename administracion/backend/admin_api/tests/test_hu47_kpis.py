import time
from datetime import timedelta
from decimal import Decimal
from uuid import uuid4

from django.core.cache import cache
from django.utils import timezone

from admin_api.kpis import LIMA, compute_kpis
from roulette.models import Bet, GameRound, Room
from .helpers import AdminTestBase

URL = "/api/v1/admin/kpis"


class HU47KpiTests(AdminTestBase):
    def setUp(self):
        super().setUp()
        cache.clear()
        self.room = Room.objects.first()

    def seed_round(self, when, amounts_and_payouts):
        round = GameRound.objects.create(room=self.room, opened_at=when, closes_at=when + timedelta(seconds=15),
                                         status="SETTLED", winning_number=7, settled_at=when + timedelta(seconds=20))
        GameRound.objects.filter(pk=round.pk).update(opened_at=when, settled_at=when + timedelta(seconds=20))
        for amount, payout in amounts_and_payouts:
            bet = Bet.objects.create(user=self.player, round=round, kind="COLOR", selection="rojo", chips=[int(amount)],
                                     amount=amount, payout=payout, reference=uuid4())
            Bet.objects.filter(pk=bet.pk).update(created_at=when)

    def test_hu47_kpis_match_seeded_data(self):
        now = timezone.now().astimezone(LIMA).replace(hour=12, minute=0, second=0, microsecond=0)
        self.seed_round(now, [(Decimal("100"), Decimal("200")), (Decimal("50"), Decimal("0"))])
        self.seed_round(now - timedelta(days=1), [(Decimal("100"), Decimal("0"))])
        data = compute_kpis(now + timedelta(hours=1))
        self.assertEqual((data["wagered_today"], data["paid_today"], data["profit_today"]), ("150.00", "200.00", "-50.00"))
        self.assertEqual((data["wagered_yesterday"], data["paid_yesterday"], data["profit_yesterday"]), ("100.00", "0.00", "100.00"))
        self.assertEqual(data["wagered_change_pct"], 50.0)
        self.assertIsNone(data["paid_change_pct"])
        self.assertEqual(data["profit_change_pct"], -150.0)

    def test_hu47_endpoint_counts_online_users_and_caches(self):
        self.client.logout()
        login = self.client.post("/api/v1/auth/login", {"email": self.player.email, "password": "Palace123!"}, format="json")
        self.assertEqual(login.status_code, 200)
        self.client.force_authenticate(self.admin)
        first = self.client.get(URL)
        self.assertEqual(first.status_code, 200)
        self.assertEqual(first.data["online_users"], 1)
        self.assertFalse(first.data["cached"])
        self.assertTrue(self.client.get(URL).data["cached"])

    def test_hu47_cached_response_under_300ms(self):
        self.client.get(URL)
        started = time.perf_counter()
        response = self.client.get(URL)
        elapsed = (time.perf_counter() - started) * 1000
        self.assertTrue(response.data["cached"])
        self.assertLess(elapsed, 300)

    def test_hu47_requires_admin(self):
        self.as_player()
        self.assertEqual(self.client.get(URL).status_code, 403)
