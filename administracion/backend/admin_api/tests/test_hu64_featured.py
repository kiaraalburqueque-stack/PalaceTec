"""HU64: juegos destacados (máximo 5) en el carrusel del inicio del jugador."""
from admin_api.models import AuditLog
from roulette.models import Game, Room
from .helpers import AdminTestBase


class HU64FeaturedTests(AdminTestBase):
    def setUp(self):
        super().setUp()
        self.games = []
        for index in range(6):
            game = Game.objects.create(slug=f"destacado-{index}", title=f"Destacado {index}", is_published=True)
            Room.objects.create(game=game, name=f"Mesa {index}")
            self.games.append(game)

    def feature(self, game, value=True):
        return self.client.patch(f"/api/v1/admin/games/{game.pk}/featured", {"is_featured": value}, format="json")

    def test_hu64_admin_marks_and_unmarks_featured(self):
        response = self.feature(self.games[0])
        self.assertEqual((response.status_code, response.data["is_featured"]), (200, True))
        self.assertTrue(Game.objects.get(pk=self.games[0].pk).is_featured)
        self.assertFalse(self.feature(self.games[0], False).data["is_featured"])
        self.assertIn("is_featured", self.client.get("/api/v1/admin/games").data[0])

    def test_hu64_sixth_featured_game_is_rejected(self):
        for game in self.games[:5]:
            self.assertEqual(self.feature(game).status_code, 200)
        response = self.feature(self.games[5])
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["code"], "featured_limit")
        self.assertFalse(Game.objects.get(pk=self.games[5].pk).is_featured)
        self.assertEqual(self.feature(self.games[0]).status_code, 200)  # volver a marcar uno ya destacado no suma
        self.feature(self.games[0], False)
        self.assertEqual(self.feature(self.games[5]).status_code, 200)

    def test_hu64_deactivated_game_leaves_carousel_and_frees_its_place(self):
        for game in self.games[:5]:
            self.feature(game)
        self.client.patch(f"/api/v1/admin/games/{self.games[0].pk}/soft-delete")
        self.assertFalse(Game.objects.get(pk=self.games[0].pk).is_featured)
        self.as_player()
        slugs = [game["slug"] for game in self.client.get("/api/v1/games/featured").data]
        self.assertEqual(slugs, [f"destacado-{index}" for index in range(1, 5)])
        self.client.force_authenticate(self.admin)
        self.assertEqual(self.feature(self.games[0]).data["code"], "game_unavailable")
        self.assertEqual(self.feature(self.games[5]).status_code, 200)

    def test_hu64_unpublished_featured_game_is_hidden_from_players(self):
        self.feature(self.games[1])
        Game.objects.filter(pk=self.games[1].pk).update(is_published=False)
        self.as_player()
        self.assertEqual(self.client.get("/api/v1/games/featured").data, [])

    def test_hu64_player_sees_featured_with_link_and_cannot_change_them(self):
        self.feature(self.games[2])
        blackjack = Game.objects.get(slug="blackjack")
        self.feature(blackjack)
        self.as_player()
        data = {game["slug"]: game for game in self.client.get("/api/v1/games/featured").data}
        self.assertEqual(data["destacado-2"]["play_room_id"], Room.objects.get(game=self.games[2]).pk)
        self.assertEqual(data["blackjack"]["play_path"], "/blackjack")
        self.assertEqual(self.feature(self.games[3]).status_code, 403)

    def test_hu64_change_is_recorded_in_audit_log(self):
        self.feature(self.games[0])
        log = AuditLog.objects.filter(action="PATCH admin-game-featured").first()
        self.assertIsNotNone(log)
        self.assertEqual((log.admin, log.details), (self.admin, {"is_featured": "True"}))

    def test_hu64_requires_boolean(self):
        self.assertEqual(self.client.patch(f"/api/v1/admin/games/{self.games[0].pk}/featured", {}, format="json").status_code, 400)
