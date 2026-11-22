"""HU61: filtro del catálogo por categoría con contador por pestaña."""
from rest_framework.test import APITestCase

from accounts.models import User
from casino.models import Play
from .models import Game, Room


class HU61CategoryFilterTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="hu61@palacetec.pe", dni="61616161", birth_date="2000-01-01")
        self.client.force_authenticate(self.user)

    def test_hu61_blackjack_and_sports_are_seeded_and_linked_to_their_screens(self):
        blackjack = Game.objects.get(slug="blackjack")
        sports = Game.objects.get(slug="deportes-virtuales")
        self.assertEqual((blackjack.category, blackjack.play_kind, blackjack.play_path), ("CARTAS", "BLACKJACK", "/blackjack"))
        self.assertEqual((sports.category, sports.play_kind, sports.play_path), ("DEPORTES", "SPORTS", "/deportes"))
        self.assertTrue(blackjack.payout_table and sports.payout_table)

    def test_hu61_games_without_rooms_appear_in_catalog_with_their_path(self):
        data = {game["slug"]: game for game in self.client.get("/api/v1/games").data}
        self.assertEqual(data["blackjack"]["play_path"], "/blackjack")
        self.assertIsNone(data["blackjack"]["rooms_url"])
        self.assertEqual((data["blackjack"]["min_bet"], data["blackjack"]["max_bet"]), ("10.00", "500.00"))
        self.assertEqual(data["deportes-virtuales"]["category_slug"], "deportes")
        self.assertIsNone(data["ruleta-europea"]["play_path"])

    def test_hu61_filter_by_category_slug(self):
        cards = self.client.get("/api/v1/games?category=cartas")
        self.assertEqual(cards.status_code, 200)
        self.assertEqual([game["slug"] for game in cards.data], ["blackjack"])
        roulette = self.client.get("/api/v1/games?category=RULETA").data
        self.assertTrue(roulette and all(game["category"] == "RULETA" for game in roulette))
        self.assertEqual(len(self.client.get("/api/v1/games?category=").data), len(self.client.get("/api/v1/games").data))

    def test_hu61_invalid_category_is_rejected(self):
        response = self.client.get("/api/v1/games?category=tragamonedas")
        self.assertEqual(response.status_code, 400)
        self.assertIn("category", response.data)

    def test_hu61_counts_per_category_only_available_games(self):
        extra = Game.objects.create(slug="ruleta-vip", title="Ruleta VIP", is_published=True)
        Room.objects.create(game=extra, name="VIP")
        hidden = Game.objects.create(slug="poker-borrador", title="Póker", category="CARTAS", is_published=False)
        Room.objects.create(game=hidden, name="Borrador")
        response = self.client.get("/api/v1/games/categories")
        self.assertEqual(response.status_code, 200)
        counts = {item["slug"]: item["count"] for item in response.data["categories"]}
        self.assertEqual(counts, {"ruleta": 2, "cartas": 1, "deportes": 1, "otros": 0})
        self.assertEqual(response.data["total"], 4)

    def test_hu61_deactivated_linked_game_disappears(self):
        Game.objects.filter(slug="blackjack").update(is_published=False)
        self.assertEqual(self.client.get("/api/v1/games?category=cartas").data, [])

    def test_hu61_existing_plays_are_related_by_kind_without_changes(self):
        self.assertFalse(any(field.name == "game" for field in Play._meta.get_fields()))
        self.assertEqual(Game.objects.get(play_kind="BLACKJACK").slug, "blackjack")

    def test_hu61_rules_of_linked_game_point_to_its_screen(self):
        game = Game.objects.get(slug="deportes-virtuales")
        rules = self.client.get(f"/api/v1/games/{game.pk}/rules").data
        self.assertEqual((rules["play_room_id"], rules["play_path"]), (None, "/deportes"))

    def test_hu61_requires_authentication(self):
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get("/api/v1/games/categories").status_code, 401)
