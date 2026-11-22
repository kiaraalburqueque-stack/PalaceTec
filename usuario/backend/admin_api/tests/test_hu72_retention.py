"""HU72: usuarios activos por día (DAU) de 30 días e índice DAU/MAU."""
from datetime import datetime, time, timedelta
from decimal import Decimal
from uuid import uuid4

from django.core.cache import cache
from django.utils import timezone

from admin_api.kpis import LIMA
from admin_api.reports import retention
from casino.models import Play
from roulette.models import Bet, GameRound, Room
from .helpers import AdminTestBase, make_user

URL = "/api/v1/admin/reports/retention"


class HU72RetentionTests(AdminTestBase):
    def setUp(self):
        super().setUp()
        cache.clear()
        self.today = timezone.now().astimezone(LIMA).date()
        self.room = Room.objects.filter(game__slug="ruleta-europea").first()
        self.players = [self.player] + [make_user(f"activo{index}@palacetec.pe", f"4000000{index}") for index in range(3)]

    def at(self, days_ago, hour=12):
        return datetime.combine(self.today - timedelta(days=days_ago), time(hour), tzinfo=LIMA)

    def bet(self, user, when):
        round = GameRound.objects.create(room=self.room, opened_at=when, closes_at=when + timedelta(seconds=15),
                                         status="SETTLED", winning_number=1, settled_at=when + timedelta(seconds=20))
        bet = Bet.objects.create(user=user, round=round, kind="COLOR", selection="rojo", chips=[10], amount=Decimal("10"),
                                 reference=uuid4())
        Bet.objects.filter(pk=bet.pk).update(created_at=when)

    def play(self, user, when):
        play = Play.objects.create(user=user, kind="BLACKJACK", amount=Decimal("10"), reference=uuid4())
        Play.objects.filter(pk=play.pk).update(created_at=when)

    def seed(self):
        p0, p1, p2, p3 = self.players
        # p0 juega 3 veces hoy y cuenta como 1 activo.
        self.bet(p0, self.at(0))
        self.bet(p0, self.at(0, 13))
        self.play(p0, self.at(0))
        self.play(p1, self.at(0))
        self.bet(p2, self.at(1))
        self.bet(p0, self.at(29, 0))
        self.bet(p3, self.at(30))  # fuera de la ventana de 30 días

    def test_hu72_dau_series_and_dau_mau_index_from_known_players(self):
        self.seed()
        data = retention(self.today - timedelta(days=29), self.today)
        dau = {point["date"]: point["dau"] for point in data["days"]}
        self.assertEqual(len(data["days"]), 30)
        self.assertEqual(dau[self.today.isoformat()], 2)
        self.assertEqual(dau[(self.today - timedelta(days=1)).isoformat()], 1)
        self.assertEqual(dau[(self.today - timedelta(days=29)).isoformat()], 1)
        self.assertEqual(sum(dau.values()), 4)
        self.assertEqual((data["mau"], data["today_dau"], data["average_dau"]), (3, 2, 0.13))
        self.assertEqual(data["dau_mau_ratio"], round(4 / 30 * 100 / 3, 2))

    def test_hu72_endpoint_is_cached_60_seconds(self):
        self.seed()
        first = self.client.get(URL).data
        self.assertEqual((first["cached"], first["cache_seconds"], first["mau"]), (False, 60, 3))
        self.play(self.players[3], self.at(0))
        second = self.client.get(URL).data
        self.assertEqual((second["cached"], second["mau"]), (True, 3))
        cache.clear()
        self.assertEqual(self.client.get(URL).data["mau"], 4)

    def test_hu72_without_activity_index_is_zero(self):
        data = self.client.get(URL).data
        self.assertEqual((data["mau"], data["dau_mau_ratio"], data["today_dau"]), (0, 0.0, 0))

    def test_hu72_only_admins(self):
        self.as_player()
        self.assertEqual(self.client.get(URL).status_code, 403)
