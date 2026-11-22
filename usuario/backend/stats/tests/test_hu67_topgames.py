"""HU67: ranking de los 5 juegos más jugados del jugador."""
from decimal import Decimal
from uuid import uuid4

from casino.models import Play
from roulette.models import Bet, Game, Room
from .helpers import StatsTestBase
from accounts.models import User

URL = "/api/v1/stats/top-games"


class HU67TopGamesTests(StatsTestBase):
    def play(self, kind, amount="10.00", user=None):
        return Play.objects.create(user=user or self.user, kind=kind, amount=Decimal(amount), reference=uuid4())

    def test_hu67_empty_when_player_has_not_played(self):
        data = self.client.get(URL).data
        self.assertEqual((data["results"], data["total_rounds"]), ([], 0))

    def test_hu67_orders_by_rounds_then_wagered_and_counts_one_round_per_roulette_round(self):
        bet = self.bet("10.00", "0.00")
        Bet.objects.create(user=self.user, round=bet.round, kind="COLOR", selection="rojo", chips=[100], amount=Decimal("100"),
                           reference=uuid4())  # misma ronda: sigue siendo 1 ronda
        for _ in range(3):
            self.play("BLACKJACK")
        self.play("SPORTS", "200.00")
        results = self.client.get(URL).data["results"]
        self.assertEqual([(r["title"], r["rounds"], r["wagered"]) for r in results],
                         [("Blackjack 21", 3, "30.00"), ("Deportes virtuales", 1, "200.00"), ("Ruleta Europea", 1, "110.00")])

    def test_hu67_only_counts_own_bets_and_limits_to_five(self):
        other = User.objects.create_user(email="otro67@palacetec.pe", dni="67676767", birth_date="2000-01-01")
        for _ in range(9):
            self.play("SPORTS", user=other)
        for index in range(5):
            game = Game.objects.create(slug=f"ruleta-67-{index}", title=f"Ruleta {index}", is_published=True)
            room = Room.objects.create(game=game, name=f"Mesa {index}")
            self.room = room
            for _ in range(index + 1):
                self.bet("10.00", "0.00")
        self.play("BLACKJACK")
        data = self.client.get(URL).data
        self.assertEqual(len(data["results"]), 5)
        self.assertEqual(data["results"][0]["title"], "Ruleta 4")
        self.assertNotIn("Deportes virtuales", [r["title"] for r in data["results"]])
        self.assertEqual(data["total_rounds"], 16)

    def test_hu67_requires_authentication(self):
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get(URL).status_code, 401)
