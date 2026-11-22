"""HU63: juegos favoritos que persisten entre sesiones y son privados de cada jugador."""
from rest_framework.test import APIClient, APITestCase

from accounts.models import User
from .models import FavoriteGame, Game


class HU63FavoriteTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="hu63@palacetec.pe", dni="63636363", birth_date="2000-01-01",
                                             password="Palace123!")
        self.other = User.objects.create_user(email="otro63@palacetec.pe", dni="63636364", birth_date="2000-01-01")
        self.client.force_authenticate(self.user)
        self.roulette = Game.objects.get(slug="ruleta-europea")
        self.blackjack = Game.objects.get(slug="blackjack")

    def toggle(self, game, client=None):
        return (client or self.client).post(f"/api/v1/games/{game.pk}/favorite")

    def test_hu63_toggle_marks_and_unmarks(self):
        first = self.toggle(self.blackjack)
        self.assertEqual((first.status_code, first.data["is_favorite"]), (200, True))
        self.assertTrue(FavoriteGame.objects.filter(user=self.user, game=self.blackjack).exists())
        second = self.toggle(self.blackjack)
        self.assertFalse(second.data["is_favorite"])
        self.assertFalse(FavoriteGame.objects.filter(user=self.user).exists())

    def test_hu63_catalog_and_favorites_list_show_the_flag(self):
        self.toggle(self.blackjack)
        self.toggle(self.roulette)
        catalog = {game["slug"]: game["is_favorite"] for game in self.client.get("/api/v1/games").data}
        self.assertTrue(catalog["blackjack"] and catalog["ruleta-europea"])
        self.assertFalse(catalog["deportes-virtuales"])
        favorites = self.client.get("/api/v1/games/favorites").data
        self.assertEqual([game["slug"] for game in favorites], ["ruleta-europea", "blackjack"])
        self.assertEqual(self.client.get("/api/v1/games/categories").data["favorites"], 2)

    def test_hu63_favorite_persists_after_logout_and_new_login(self):
        client = APIClient(enforce_csrf_checks=False)
        login = client.post("/api/v1/auth/login", {"email": "hu63@palacetec.pe", "password": "Palace123!"}, format="json")
        self.assertEqual(login.status_code, 200)
        self.assertTrue(self.toggle(self.blackjack, client).data["is_favorite"])
        self.assertEqual(client.post("/api/v1/auth/logout", {}, format="json").status_code, 200)
        self.assertEqual(client.get("/api/v1/games/favorites").status_code, 401)
        client = APIClient()
        client.post("/api/v1/auth/login", {"email": "hu63@palacetec.pe", "password": "Palace123!"}, format="json")
        self.assertEqual([game["slug"] for game in client.get("/api/v1/games/favorites").data], ["blackjack"])

    def test_hu63_player_cannot_see_or_change_favorites_of_another(self):
        FavoriteGame.objects.create(user=self.other, game=self.roulette)
        self.assertEqual(self.client.get("/api/v1/games/favorites").data, [])
        self.assertFalse({g["slug"]: g["is_favorite"] for g in self.client.get("/api/v1/games").data}["ruleta-europea"])
        # Alternar el mismo juego solo crea el favorito propio; el del otro jugador sigue intacto.
        self.toggle(self.roulette)
        self.toggle(self.roulette)
        self.assertTrue(FavoriteGame.objects.filter(user=self.other, game=self.roulette).exists())
        self.assertFalse(FavoriteGame.objects.filter(user=self.user).exists())

    def test_hu63_unavailable_game_cannot_be_added_but_disappears_from_list(self):
        self.toggle(self.blackjack)
        Game.objects.filter(pk=self.blackjack.pk).update(is_published=False)
        self.assertEqual(self.client.get("/api/v1/games/favorites").data, [])
        self.assertEqual(self.toggle(self.blackjack).data["is_favorite"], False)  # se puede quitar
        self.assertEqual(self.toggle(self.blackjack).status_code, 404)  # pero no volver a agregar
        self.assertEqual(self.client.post("/api/v1/games/999999/favorite").status_code, 404)

    def test_hu63_requires_authentication(self):
        self.client.force_authenticate(None)
        self.assertEqual(self.toggle(self.blackjack).status_code, 401)
        self.assertEqual(self.client.get("/api/v1/games/favorites").status_code, 401)
