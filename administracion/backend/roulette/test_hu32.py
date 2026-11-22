from datetime import datetime, timezone
from .models import Bet
from .test_game import GameTestBase


class HU32DateFilterTests(GameTestBase):
    def test_full_final_day_in_lima_and_boundaries(self):
        timestamps = [datetime(2026, 10, 18, 4, 59, 59, tzinfo=timezone.utc),
                      datetime(2026, 10, 18, 5, 0, tzinfo=timezone.utc),
                      datetime(2026, 10, 19, 4, 59, 59, tzinfo=timezone.utc),
                      datetime(2026, 10, 19, 5, 0, tzinfo=timezone.utc)]
        ids = []
        for timestamp in timestamps:
            bet = self.client.post(self.url, self.data(), format='json').data
            Bet.objects.filter(pk=bet['id']).update(created_at=timestamp)
            ids.append(bet['id'])
        response = self.client.get('/api/v1/users/bets-history?startDate=2026-10-18&endDate=2026-10-18')
        self.assertEqual(response.status_code, 200)
        self.assertEqual({b['id'] for b in response.data['results']}, set(ids[1:3]))

    def test_invalid_dates(self):
        for query in ['startDate=2026-10-19&endDate=2026-10-18', 'startDate=not-a-date', 'endDate=9999-12-31']:
            self.assertEqual(self.client.get('/api/v1/users/bets-history?'+query).status_code, 400)
