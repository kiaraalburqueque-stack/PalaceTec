from decimal import Decimal
from uuid import uuid4
from unittest.mock import patch
from .test_game import GameTestBase
from .services import RouletteService
from .models import Bet, RepeatOperation
from responsible.services import LimitService


class HU35RepeatTests(GameTestBase):
    def previous(self):
        for selection in ['1', '2']:
            self.assertEqual(self.client.post(self.url, self.data(kind='DOZEN', selection=selection), format='json').status_code, 201)
        source = self.round
        with patch('roulette.services.timezone.now', return_value=source.closes_at):
            RouletteService.tick(self.room.pk)
        with patch('roulette.services.rng.spin', return_value=0):
            RouletteService.tick(self.room.pk)
        self.round = RouletteService.tick(self.room.pk)
        return source

    def test_repeat_copies_all_bets_once_and_rejects_conflicting_reference(self):
        source = self.previous()
        url = self.url+'/repeat'
        payload = {'round_id': self.round.pk, 'source_round_id': source.pk, 'reference': str(uuid4())}
        self.assertEqual(self.client.get(self.url+'/previous').data['source_round_id'], source.pk)
        response = self.client.post(url, payload, format='json')
        self.assertEqual(response.status_code, 201)
        self.assertEqual([b['selection'] for b in response.data['bets']], ['1', '2'])
        self.assertEqual(self.client.post(url, payload, format='json').status_code, 200)
        self.assertEqual(self.client.post(url, {**payload, 'source_round_id': 999}, format='json').status_code, 409)
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 960)
        self.assertEqual(Bet.objects.count(), 4)

    def test_partial_failure_rolls_back_entire_repeat_and_spending(self):
        self.previous()
        LimitService.set_limit(self.user, Decimal('30'))  # 20 consumidos, quedan 10 para un conjunto de 20.
        response = self.client.post(self.url+'/repeat', {'round_id': self.round.pk, 'reference': str(uuid4())}, format='json')
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data['code'], 'daily_limit_exceeded')
        self.assertEqual(Bet.objects.filter(round=self.round).count(), 0)
        self.assertFalse(RepeatOperation.objects.exists())
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 980)
        self.assertEqual(LimitService.summary(self.user)['spent'], '20.00')

    def test_no_previous_and_closed_round(self):
        self.assertIsNone(self.client.get(self.url+'/previous').data['source_round_id'])
        self.assertEqual(self.client.post(self.url+'/repeat', {'round_id': self.round.pk, 'reference': str(uuid4())}, format='json').status_code, 400)
        self.previous()
        with patch('roulette.services.timezone.now', return_value=self.round.closes_at):
            self.assertEqual(self.client.post(self.url+'/repeat', {'round_id': self.round.pk, 'reference': str(uuid4())}, format='json').status_code, 400)
