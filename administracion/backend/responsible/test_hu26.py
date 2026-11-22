from datetime import datetime, timedelta, timezone as utc
from decimal import Decimal
from unittest.mock import patch
from uuid import uuid4
from rest_framework.test import APITestCase
from accounts.models import User
from wallet.models import Wallet, WalletTransaction
from roulette.models import Room, Bet
from roulette.services import RouletteService
from .models import DailyLimit, DailySpending
from .services import LimitService


class HU26DailyLimitTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email='limits@example.com', dni='12345678', birth_date='2000-01-01')
        self.client.force_authenticate(self.user)
        self.wallet = Wallet.objects.create(user=self.user, balance=1000, bonus_balance=1000)
        self.room = Room.objects.first()
        self.round = RouletteService.tick(self.room.pk)
        self.url = f'/api/v1/rooms/{self.room.pk}/bets'

    def bet(self, chips=None, reference=None):
        return self.client.post(self.url, {'round_id': self.round.pk, 'kind': 'COLOR', 'selection': 'rojo', 'chips': chips or [10], 'reference': reference or str(uuid4())}, format='json')

    def test_configuration_required_exact_remaining_and_replay(self):
        self.assertEqual(self.bet().data['code'], 'daily_limit_required')
        LimitService.set_limit(self.user, Decimal('10'))
        ref = str(uuid4())
        self.assertEqual(self.bet(reference=ref).status_code, 201)
        self.assertEqual(self.bet(reference=ref).status_code, 200)
        self.assertEqual(self.bet().data['code'], 'daily_limit_exceeded')
        self.assertEqual(DailySpending.objects.get().amount, 10)
        self.assertEqual(Bet.objects.count(), 1)

    def test_failed_debit_does_not_consume_limit(self):
        LimitService.set_limit(self.user, Decimal('100'))
        self.wallet.balance = self.wallet.bonus_balance = 0
        self.wallet.save()
        self.assertEqual(self.bet().data['code'], 'insufficient_balance')
        self.assertFalse(DailySpending.objects.exists())
        self.assertFalse(Bet.objects.exists())

    def test_increase_waits_24h_and_reduction_cancels_pending(self):
        now = datetime(2026, 10, 18, 12, tzinfo=utc.utc)
        with patch('responsible.services.timezone.now', return_value=now):
            LimitService.set_limit(self.user, Decimal('100'))
            result = LimitService.set_limit(self.user, Decimal('200'))
            self.assertEqual(result['limit'], '100.00')
        with patch('responsible.services.timezone.now', return_value=now+timedelta(hours=24)-timedelta(seconds=1)):
            self.assertEqual(LimitService.summary(self.user)['limit'], '100.00')
        with patch('responsible.services.timezone.now', return_value=now+timedelta(hours=24)):
            self.assertEqual(LimitService.summary(self.user)['limit'], '200.00')
            LimitService.set_limit(self.user, Decimal('300'))
            result = LimitService.set_limit(self.user, Decimal('50'))
            self.assertEqual((result['limit'], result['pending_limit']), ('50.00', None))

    def test_day_rolls_at_midnight_lima(self):
        LimitService.set_limit(self.user, Decimal('10'))
        before = datetime(2026, 10, 19, 4, 59, 59, tzinfo=utc.utc)
        with patch('responsible.services.timezone.now', return_value=before):
            LimitService.check_and_reserve(self.user, Decimal('10'))
            self.assertEqual(LimitService.summary(self.user)['remaining'], '0.00')
        with patch('responsible.services.timezone.now', return_value=before+timedelta(seconds=1)):
            self.assertEqual(LimitService.summary(self.user)['remaining'], '10.00')
            LimitService.check_and_reserve(self.user, Decimal('10'))
        self.assertEqual(DailySpending.objects.count(), 2)

    def test_zero_negative_nonfinite_and_precision(self):
        for amount in ['-1', 'NaN', 'Infinity', '0.001']:
            self.assertEqual(self.client.post('/api/v1/responsible-gaming/daily-limit', {'amount': amount}, format='json').status_code, 400)
        self.assertEqual(self.client.post('/api/v1/responsible-gaming/daily-limit', {'amount': '0'}, format='json').status_code, 200)
        self.assertEqual(self.bet().data['code'], 'daily_limit_exceeded')
