"""HU62: búsqueda de juegos por nombre sin distinguir tildes, ñ ni mayúsculas."""
from rest_framework.test import APITestCase

from accounts.models import User
from .catalog import normalize
from .models import Game, Room


class HU62SearchTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="hu62@palacetec.pe", dni="62626262", birth_date="2000-01-01")
        self.client.force_authenticate(self.user)
        self.game = Game.objects.create(slug="ruleta-pequena", title="Ruleta Pequeña Mágica", is_published=True)
        Room.objects.create(game=self.game, name="Mesa")

    def slugs(self, term, **extra):
        response = self.client.get("/api/v1/games", {"search": term, **extra})
        self.assertEqual(response.status_code, 200)
        return [game["slug"] for game in response.data]

    def test_hu62_normalize_removes_accents_and_case(self):
        self.assertEqual(normalize("  PEQUEÑA   Mágica "), "pequena magica")
        self.assertEqual(normalize("Pingüino"), "pinguino")

    def test_hu62_accents_enye_and_case_give_same_result(self):
        variants = ["pequeña", "PEQUEÑA", "pequena", "Pequena", "MÁGICA", "magica", "ña mág"]
        for term in variants:
            with self.subTest(term=term):
                self.assertEqual(self.slugs(term), ["ruleta-pequena"])

    def test_hu62_partial_match_and_combination_with_category(self):
        self.assertIn("blackjack", self.slugs("JACK"))
        self.assertEqual(self.slugs("ruleta", category="cartas"), [])
        self.assertIn("ruleta-pequena", self.slugs("ruleta", category="ruleta"))

    def test_hu62_no_results_returns_empty_list(self):
        self.assertEqual(self.slugs("tragamonedas"), [])

    def test_hu62_blank_search_returns_full_catalog(self):
        self.assertEqual(len(self.slugs("   ")), len(self.client.get("/api/v1/games").data))

    def test_hu62_counts_follow_the_search(self):
        data = self.client.get("/api/v1/games/categories", {"search": "pequeña"}).data
        counts = {item["slug"]: item["count"] for item in data["categories"]}
        self.assertEqual((data["total"], counts["ruleta"], counts["cartas"]), (1, 1, 0))

    def test_hu62_search_too_long_is_rejected(self):
        self.assertEqual(self.client.get("/api/v1/games", {"search": "x" * 81}).status_code, 400)
