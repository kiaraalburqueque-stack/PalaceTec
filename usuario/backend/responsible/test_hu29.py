from datetime import timedelta
from unittest.mock import patch
from django.utils import timezone
from roulette.test_game import GameTestBase
from roulette.services import RouletteService
from roulette.models import Bet, GameRound
from .models import RiskIncident
from .pattern_detector import detect_loss_escalation


class HU29RiskPatternTests(GameTestBase):
    def play_losses(self, amounts=(10, 30, 90, 270)):
        for amount in amounts:
            response = self.client.post(self.url, self.data(kind='COLOR', selection='rojo', chips=[10]*(amount//10)), format='json')
            self.assertEqual(response.status_code, 201)
            with patch('roulette.services.timezone.now', return_value=self.round.closes_at):
                RouletteService.tick(self.room.pk)
            with patch('roulette.services.rng.spin', return_value=2):
                RouletteService.tick(self.room.pk)
            self.round = RouletteService.tick(self.room.pk)

    def test_four_losing_rounds_trigger_one_private_alert_and_pause(self):
        with patch('realtime.events.publish') as publish:
            with self.captureOnCommitCallbacks(execute=True):
                self.play_losses()
        incident = RiskIncident.objects.get(user=self.user)
        self.assertEqual(len(incident.evidence), 4)
        alerts = [call for call in publish.call_args_list if call.args[1] == 'responsible:risk-alert']
        self.assertEqual(len(alerts), 1)
        self.assertEqual(alerts[0].args[0], f'user.{self.user.pk}')
        recovered = self.client.get('/api/v1/responsible-gaming/risk-alerts')
        self.assertEqual(recovered.data['results'][0]['incident_id'], incident.pk)
        self.assertEqual(self.client.post('/api/v1/responsible-gaming/pause', {'incident_id': incident.pk}, format='json').status_code, 200)
        self.user.refresh_from_db()
        until = self.user.pause_until
        response = self.client.post(self.url, self.data(), format='json')
        self.assertEqual(response.data['code'], 'pause_active')
        self.client.post('/api/v1/responsible-gaming/pause', {'incident_id': incident.pk}, format='json')
        self.user.refresh_from_db()
        self.assertEqual(self.user.pause_until, until)
        self.user.pause_until = timezone.now()-timedelta(seconds=1)
        self.user.save()
        self.assertEqual(self.client.post(self.url, self.data(), format='json').status_code, 201)

    def test_non_tripling_losses_do_not_trigger(self):
        self.play_losses((10, 20, 30, 40))
        self.assertFalse(RiskIncident.objects.exists())

    def test_cannot_pause_with_another_players_incident(self):
        from accounts.models import User
        other = User.objects.create_user(email='other-risk@example.com', dni='87654321', birth_date='2000-01-01')
        incident = RiskIncident.objects.create(user=other, round=self.round)
        self.assertEqual(self.client.post('/api/v1/responsible-gaming/pause', {'incident_id': incident.pk}, format='json').status_code, 404)
        self.assertEqual(self.client.get('/api/v1/responsible-gaming/risk-alerts').data['results'], [])
