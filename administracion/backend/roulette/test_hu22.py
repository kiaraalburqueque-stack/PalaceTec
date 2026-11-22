from datetime import timedelta
from unittest.mock import patch
from .test_game import GameTestBase
from .models import Bet


class HU22ExactDeadlineTests(GameTestBase):
    def test_one_microsecond_before_accepts_at_deadline_rejects(self):
        self.assertEqual(self.round.closes_at-self.round.opened_at, timedelta(seconds=15))
        with patch('roulette.services.timezone.now', return_value=self.round.closes_at-timedelta(microseconds=1)):
            self.assertEqual(self.client.post(self.url, self.data(), format='json').status_code, 201)
        with patch('roulette.services.timezone.now', return_value=self.round.closes_at):
            response = self.client.post(self.url, self.data(), format='json')
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data['code'], 'bets_closed')
        self.assertEqual(Bet.objects.count(), 1)
