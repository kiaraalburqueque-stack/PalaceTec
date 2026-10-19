from datetime import timedelta
from django.utils import timezone
from .test_game import GameTestBase
from .models import GameRound
from .state import room_state


class HU33RecentResultsTests(GameTestBase):
    def test_only_ten_settled_results_of_this_room_newest_first(self):
        now = timezone.now()
        rounds = [GameRound.objects.create(room=self.room, status='SETTLED', winning_number=i,
            opened_at=now+timedelta(seconds=i), closes_at=now+timedelta(seconds=i+15),
            settled_at=now+timedelta(seconds=i+16)) for i in range(12)]
        other_room = self.room.__class__.objects.exclude(pk=self.room.pk).first()
        GameRound.objects.create(room=other_room, status='SETTLED', winning_number=36,
            opened_at=now, closes_at=now+timedelta(seconds=15), settled_at=now+timedelta(hours=1))
        results = room_state(self.room.pk)['recent_results']
        self.assertEqual(len(results), 10)
        self.assertEqual([r['round_id'] for r in results], [r.pk for r in reversed(rounds[2:])])
        self.assertEqual(results[0]['color'], 'negro')

    def test_no_results_initially(self):
        self.assertEqual(room_state(self.room.pk)['recent_results'], [])
