from accounts.models import User
from .test_game import GameTestBase


class HU31HistoryTests(GameTestBase):
    def test_history_is_private_paginated_and_pending_is_not_loss(self):
        for _ in range(3):
            self.assertEqual(self.client.post(self.url, self.data(), format='json').status_code, 201)
        response = self.client.get('/api/v1/users/bets-history?page_size=2')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data['count'], 3)
        self.assertEqual(len(response.data['results']), 2)
        self.assertIsNotNone(response.data['next'])
        bet = response.data['results'][0]
        self.assertEqual(bet['status'], 'PENDING')
        self.assertIsNone(bet['net'])
        self.assertIn('game', bet)
        other = User.objects.create_user(email='stranger@example.com', dni='87654321', birth_date='2000-01-01')
        self.client.force_authenticate(other)
        self.assertEqual(self.client.get('/api/v1/users/bets-history').data['count'], 0)
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get('/api/v1/users/bets-history').status_code, 401)

    def test_order_and_second_page(self):
        ids = [self.client.post(self.url, self.data(), format='json').data['id'] for _ in range(3)]
        response = self.client.get('/api/v1/users/bets-history?page_size=2&page=2')
        self.assertEqual([bet['id'] for bet in response.data['results']], [ids[0]])
