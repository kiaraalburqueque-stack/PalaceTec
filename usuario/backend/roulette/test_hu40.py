from unittest.mock import patch
from accounts.models import User
from .test_game import GameTestBase
from .services import RouletteService


class HU40StatsTests(GameTestBase):
    def test_settled_totals_net_pending_and_user_isolation(self):
        self.client.post(self.url, self.data(kind='COLOR', selection='rojo'), format='json')
        self.client.post(self.url, self.data(kind='COLOR', selection='negro', chips=[50]), format='json')
        self.assertEqual(self.client.get('/api/v1/stats/summary').data['total_wagered'], '0.00')
        with patch('roulette.services.timezone.now', return_value=self.round.closes_at):
            RouletteService.tick(self.room.pk)
        with patch('roulette.services.rng.spin', return_value=1):
            RouletteService.tick(self.room.pk)
        data = self.client.get('/api/v1/stats/summary').data
        self.assertEqual((data['total_wagered'], data['total_paid'], data['net']), ('60.00', '20.00', '-40.00'))
        other = User.objects.create_user(email='stats-other@example.com', dni='87654321', birth_date='2000-01-01')
        self.client.force_authenticate(other)
        self.assertEqual(self.client.get('/api/v1/stats/summary').data['net'], '0.00')
