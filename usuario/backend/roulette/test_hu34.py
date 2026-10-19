from rest_framework.test import APITestCase
from accounts.models import User
from .models import Game, Room


class HU34CatalogTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email='games@example.com', dni='12345678', birth_date='2000-01-01')
        self.client.force_authenticate(self.user)

    def test_only_published_games_with_active_rooms_and_real_limits(self):
        Game.objects.all().update(is_published=False)
        game = Game.objects.create(slug='roulette-test', title='Ruleta', is_published=True)
        Room.objects.create(game=game, name='small', min_bet=10, max_bet=500)
        Room.objects.create(game=game, name='large', min_bet=50, max_bet=1000)
        Room.objects.create(game=game, name='hidden', min_bet=1, max_bet=9999, is_active=False)
        Game.objects.create(slug='empty', title='Sin salas', is_published=True)
        response = self.client.get('/api/v1/games')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual((response.data[0]['min_bet'], response.data[0]['max_bet']), ('10.00', '1000.00'))

    def test_unpublished_game_room_is_unavailable(self):
        game = Game.objects.create(slug='hidden', title='Hidden', is_published=False)
        room = Room.objects.create(name='hidden', game=game)
        self.assertEqual(self.client.get(f'/api/v1/rooms/{room.pk}/state').status_code, 404)
        self.assertNotIn(room.pk, [r['id'] for r in self.client.get('/api/v1/rooms').data])
