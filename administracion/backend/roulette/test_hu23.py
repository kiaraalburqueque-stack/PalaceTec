from unittest.mock import patch
from .test_game import GameTestBase
from .services import RouletteService


class HU23ServerResultTests(GameTestBase):
    def test_full_result_payload_and_payment(self):
        self.client.post(self.url, self.data(kind='DOZEN', selection='1'), format='json')
        with patch('roulette.services.timezone.now', return_value=self.round.closes_at):
            RouletteService.tick(self.room.pk)
        with patch('roulette.services.rng.spin', return_value=12), patch('realtime.events.publish') as publish:
            with self.captureOnCommitCallbacks(execute=True):
                RouletteService.tick(self.room.pk)
            result = next(call.args[2] for call in publish.call_args_list if call.args[1] == 'round:result')
        self.assertEqual((result['winning_number'], result['color'], result['parity']), (12, 'rojo', 'par'))
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 1020)

    def test_zero_has_no_parity_and_is_green(self):
        with patch('roulette.services.timezone.now', return_value=self.round.closes_at):
            RouletteService.tick(self.room.pk)
        with patch('roulette.services.rng.spin', return_value=0), patch('realtime.events.publish') as publish:
            with self.captureOnCommitCallbacks(execute=True):
                RouletteService.tick(self.room.pk)
        result = next(call.args[2] for call in publish.call_args_list if call.args[1] == 'round:result')
        self.assertEqual((result['color'], result['parity']), ('verde', None))
