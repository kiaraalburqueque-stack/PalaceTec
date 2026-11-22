from uuid import uuid4

from responsible.models import DailyLimit
from roulette.models import Bet, Game, GameRound, Room
from roulette.services import RouletteService
from .helpers import AdminTestBase


class HU45SoftDeleteTests(AdminTestBase):
    def setUp(self):
        super().setUp()
        self.game = Game.objects.get(slug="ruleta-europea")
        self.room = Room.objects.filter(game=self.game).first()
        DailyLimit.objects.create(user=self.player, amount=10000)
        round = RouletteService.tick(self.room.pk)
        self.as_player()
        self.client.post(f"/api/v1/rooms/{self.room.pk}/bets", {"round_id": round.pk, "kind": "COLOR",
            "selection": "rojo", "chips": [50], "reference": str(uuid4())}, format="json")
        self.client.force_authenticate(self.admin)

    def test_hu45_public_get_hides_deactivated_game_but_keeps_history(self):
        response = self.client.patch(f"/api/v1/admin/games/{self.game.pk}/soft-delete")
        self.assertEqual(response.status_code, 200)
        self.assertIsNotNone(response.data["deleted_at"])
        self.game.refresh_from_db()
        self.assertFalse(self.game.is_published)
        self.assertTrue(Game.objects.filter(pk=self.game.pk).exists())
        self.assertEqual(Bet.objects.filter(round__room=self.room).count(), 1)
        self.assertTrue(GameRound.objects.filter(room=self.room).exists())
        self.as_player()
        self.assertNotIn(self.game.pk, [game["id"] for game in self.client.get("/api/v1/games").data])
        self.assertEqual(self.client.get(f"/api/v1/rooms/{self.room.pk}/state").status_code, 404)
        self.assertEqual(self.client.get("/api/v1/users/bets-history").data["count"], 1)
        # El administrador lo sigue viendo en su catálogo completo.
        self.client.force_authenticate(self.admin)
        self.assertIn(self.game.pk, [game["id"] for game in self.client.get("/api/v1/admin/games").data])

    def test_hu45_cannot_publish_deleted_game_without_restore(self):
        self.client.patch(f"/api/v1/admin/games/{self.game.pk}/soft-delete")
        self.assertEqual(self.client.put(f"/api/v1/admin/games/{self.game.pk}", {"is_published": True}, format="json").status_code, 400)
        restored = self.client.patch(f"/api/v1/admin/games/{self.game.pk}/restore")
        self.assertIsNone(restored.data["deleted_at"])
        self.as_player()
        self.assertIn(self.game.pk, [game["id"] for game in self.client.get("/api/v1/games").data])

    def test_hu45_only_admins(self):
        self.as_player()
        self.assertEqual(self.client.patch(f"/api/v1/admin/games/{self.game.pk}/soft-delete").status_code, 403)
