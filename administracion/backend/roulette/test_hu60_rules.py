from rest_framework.test import APITestCase

from accounts.models import User
from .models import Game


class HU60RulesTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="reglas@example.com", dni="12345678", birth_date="2000-01-01")
        self.client.force_authenticate(self.user)
        self.game = Game.objects.get(slug="ruleta-europea")

    def test_hu60_returns_rules_payout_table_and_rtp(self):
        response = self.client.get(f"/api/v1/games/{self.game.pk}/rules")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["rtp"], "97.30")
        self.assertEqual(response.data["house_edge"], "2.70")
        self.assertIn("37 casillas", response.data["rules"])
        pays = {row["bet"]: row["pays"] for row in response.data["payout_table"]}
        self.assertEqual(pays["Pleno"], "35 a 1")
        self.assertEqual(pays["Docena"], "2 a 1")
        self.assertIsNotNone(response.data["play_room_id"])

    def test_hu60_hidden_game_has_no_public_rules(self):
        Game.objects.filter(pk=self.game.pk).update(is_published=False)
        self.assertEqual(self.client.get(f"/api/v1/games/{self.game.pk}/rules").status_code, 404)
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get(f"/api/v1/games/{self.game.pk}/rules").status_code, 401)
