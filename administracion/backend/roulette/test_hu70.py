"""HU70: recomendaciones personalizadas por categorías jugadas, favoritos y popularidad."""
from datetime import timedelta
from decimal import Decimal
from uuid import uuid4

from django.utils import timezone
from rest_framework.test import APITestCase

from accounts.models import User
from casino.models import Play
from responsible.models import DailyLimit, DailySpending
from responsible.services import LimitService
from .game_activity import activity_by_game
from .models import Bet, FavoriteGame, Game, GameRound, Room

URL = "/api/v1/games/recommendations"


class HU70RecommendationTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="hu70@palacetec.pe", dni="70707070", birth_date="2000-01-01")
        self.other = User.objects.create_user(email="otro70@palacetec.pe", dni="70707071", birth_date="2000-01-01")
        self.client.force_authenticate(self.user)
        self.roulette = Game.objects.get(slug="ruleta-europea")
        self.blackjack = Game.objects.get(slug="blackjack")
        self.sports = Game.objects.get(slug="deportes-virtuales")
        self.room = Room.objects.filter(game=self.roulette).order_by("id").first()
        self.extra = []
        for index in range(3):
            game = Game.objects.create(slug=f"ruleta-extra-{index}", title=f"Ruleta Extra {index}", is_published=True)
            Room.objects.create(game=game, name=f"Mesa {index}")
            self.extra.append(game)

    def bet(self, user, room=None, amount="10.00", when=None):
        when = when or timezone.now()
        round = GameRound.objects.create(room=room or self.room, opened_at=when, closes_at=when + timedelta(seconds=15))
        bet = Bet.objects.create(user=user, round=round, kind="COLOR", selection="rojo", chips=[10], amount=Decimal(amount),
                                 reference=uuid4())
        if when != bet.created_at:
            Bet.objects.filter(pk=bet.pk).update(created_at=when)
        return bet

    def play(self, user, kind="BLACKJACK"):
        return Play.objects.create(user=user, kind=kind, amount=Decimal("10.00"), reference=uuid4())

    def get(self):
        response = self.client.get(URL)
        self.assertEqual(response.status_code, 200)
        return response.data

    def test_hu70_activity_counts_one_round_per_player_and_round(self):
        bet = self.bet(self.user)
        Bet.objects.create(user=self.user, round=bet.round, kind="PARITY", selection="par", chips=[10], amount=10, reference=uuid4())
        self.play(self.user)
        activity = activity_by_game(Bet.objects.all(), Play.objects.all())
        self.assertEqual((activity[self.roulette.pk].rounds, activity[self.roulette.pk].wagered), (1, Decimal("20.00")))
        self.assertEqual(activity[self.blackjack.pk].rounds, 1)

    def test_hu70_new_player_gets_most_popular_games(self):
        for _ in range(3):
            self.play(self.other, "SPORTS")
        self.bet(self.other)
        data = self.get()
        self.assertFalse(data["personalized"])
        self.assertEqual(len(data["results"]), 4)
        self.assertEqual([g["slug"] for g in data["results"][:2]], ["deportes-virtuales", "ruleta-europea"])
        self.assertEqual(data["results"][0]["reason"], "Popular en PalaceTec")

    def test_hu70_prioritizes_most_played_category(self):
        for _ in range(4):
            self.play(self.user, "BLACKJACK")
        self.bet(self.user)
        for _ in range(10):
            self.play(self.other, "SPORTS")  # muy popular, pero el jugador prefiere cartas
        data = self.get()
        first = data["results"][0]
        self.assertTrue(data["personalized"])
        self.assertEqual((first["slug"], first["reason_code"], first["reason"]), ("blackjack", "category", "Porque jugaste Blackjack 21"))
        roulette = next(g for g in data["results"] if g["slug"] == "ruleta-europea")
        self.assertEqual(roulette["play_room_id"], self.room.pk)

    def test_hu70_favorites_add_score_with_their_reason(self):
        FavoriteGame.objects.create(user=self.user, game=self.extra[2])
        first = self.get()["results"][0]
        self.assertEqual((first["slug"], first["reason"], first["is_favorite"]), ("ruleta-extra-2", "Está en tus favoritos", True))

    def test_hu70_excludes_deactivated_games_and_old_activity(self):
        for _ in range(5):
            self.play(self.user, "SPORTS")
        Game.objects.filter(pk=self.sports.pk).update(is_published=False, deleted_at=timezone.now())
        FavoriteGame.objects.create(user=self.user, game=self.sports)
        slugs = [g["slug"] for g in self.get()["results"]]
        self.assertNotIn("deportes-virtuales", slugs)
        self.assertLessEqual(len(slugs), 4)
        old = self.bet(self.user, room=Room.objects.get(game=self.extra[1]), when=timezone.now() - timedelta(days=40))
        self.assertTrue(Bet.objects.filter(pk=old.pk, created_at__lt=timezone.now() - timedelta(days=30)).exists())
        self.assertFalse(self.get()["personalized"])

    def test_hu70_self_excluded_player_gets_no_recommendations(self):
        self.play(self.user)
        User.objects.filter(pk=self.user.pk).update(self_excluded_until=timezone.now() + timedelta(days=7))
        self.user.refresh_from_db()
        self.client.force_authenticate(self.user)
        self.assertEqual(self.get(), {"results": [], "blocked": "self_excluded", "personalized": False})

    def test_hu70_daily_limit_reached_gets_no_recommendations(self):
        DailyLimit.objects.create(user=self.user, amount=Decimal("50.00"))
        DailySpending.objects.create(user=self.user, date=LimitService.day(), amount=Decimal("40.00"))
        self.assertEqual(len(self.get()["results"]), 4)
        DailySpending.objects.filter(user=self.user).update(amount=Decimal("50.00"))
        data = self.get()
        self.assertEqual((data["results"], data["blocked"]), ([], "daily_limit_reached"))

    def test_hu70_requires_authentication(self):
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get(URL).status_code, 401)
