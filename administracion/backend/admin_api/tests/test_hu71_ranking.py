"""HU71: ranking de popularidad de juegos (rondas, jugadores únicos y créditos) en 7 o 30 días."""
from datetime import timedelta
from decimal import Decimal
from uuid import uuid4

from django.utils import timezone

from casino.models import Play
from roulette.models import Bet, Game, GameRound, Room
from .helpers import AdminTestBase, make_user

URL = "/api/v1/admin/reports/games-ranking"


class HU71RankingTests(AdminTestBase):
    def setUp(self):
        super().setUp()
        self.second = make_user("segundo@palacetec.pe", "30000000")
        self.room = Room.objects.filter(game__slug="ruleta-europea").first()

    def bet(self, user, amount, when=None, round=None):
        when = when or timezone.now()
        round = round or GameRound.objects.create(room=self.room, opened_at=when, closes_at=when + timedelta(seconds=15))
        bet = Bet.objects.create(user=user, round=round, kind="COLOR", selection="rojo", chips=[10], amount=Decimal(amount),
                                 reference=uuid4())
        Bet.objects.filter(pk=bet.pk).update(created_at=when)
        return bet

    def play(self, user, kind, amount, when=None):
        play = Play.objects.create(user=user, kind=kind, amount=Decimal(amount), reference=uuid4())
        if when:
            Play.objects.filter(pk=play.pk).update(created_at=when)

    def rows(self, days=None):
        response = self.client.get(URL, {"days": days} if days else {})
        self.assertEqual(response.status_code, 200)
        return response.data, {row["title"]: row for row in response.data["games"]}

    def test_hu71_sums_credits_rounds_and_unique_players_per_game(self):
        first = self.bet(self.player, "100.00")
        self.bet(self.player, "50.00", round=first.round)  # misma ronda y jugador: 1 ronda
        self.bet(self.second, "20.00", round=first.round)
        self.play(self.player, "BLACKJACK", "300.00")
        self.play(self.player, "BLACKJACK", "10.00")
        data, rows = self.rows()
        self.assertEqual((data["days"], data["timezone"]), (7, "America/Lima"))
        roulette, blackjack = rows["Ruleta Europea"], rows["Blackjack 21"]
        self.assertEqual((roulette["rounds"], roulette["unique_players"], roulette["wagered"]), (2, 2, "170.00"))
        self.assertEqual((blackjack["rounds"], blackjack["unique_players"], blackjack["wagered"]), (2, 1, "310.00"))
        self.assertEqual([row["title"] for row in data["games"][:2]], ["Blackjack 21", "Ruleta Europea"])
        self.assertEqual(rows["Deportes virtuales"]["wagered"], "0.00")
        self.assertEqual(data["totals"], {"rounds": 4, "wagered": "480.00", "unique_players": 2})
        self.assertAlmostEqual(blackjack["share_pct"], 64.58)

    def test_hu71_window_of_7_or_30_days(self):
        self.play(self.player, "SPORTS", "40.00", when=timezone.now() - timedelta(days=12))
        self.play(self.player, "SPORTS", "10.00")
        self.assertEqual(self.rows(7)[1]["Deportes virtuales"]["wagered"], "10.00")
        self.assertEqual(self.rows(30)[1]["Deportes virtuales"]["wagered"], "50.00")
        self.assertEqual(self.client.get(URL, {"days": 15}).status_code, 400)

    def test_hu71_deactivated_game_with_activity_stays_in_ranking(self):
        game = Game.objects.create(slug="ruleta-vieja", title="Ruleta Vieja", is_published=True)
        self.room = Room.objects.create(game=game, name="Mesa")
        self.bet(self.player, "25.00")
        Game.objects.filter(pk=game.pk).update(is_published=False, deleted_at=timezone.now())
        row = self.rows()[1]["Ruleta Vieja"]
        self.assertEqual((row["wagered"], row["active"]), ("25.00", False))

    def test_hu71_player_gets_403(self):
        self.as_player()
        self.assertEqual(self.client.get(URL).status_code, 403)
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get(URL).status_code, 401)
