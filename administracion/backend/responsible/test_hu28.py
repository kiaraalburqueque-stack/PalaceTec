from datetime import timedelta
from unittest.mock import patch
from uuid import uuid4
from django.conf import settings
from django.utils import timezone
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken
from accounts.models import User, AuthSession
from roulette.models import Room
from roulette.services import RouletteService
from wallet.models import Wallet
from .models import DailyLimit


class HU28SessionSummaryTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email='session@example.com', dni='12345678', birth_date='2000-01-01')
        Wallet.objects.create(user=self.user, balance=1000, bonus_balance=1000)
        DailyLimit.objects.create(user=self.user, amount=1000)
        self.token = AccessToken.for_user(self.user)
        self.token.set_exp(lifetime=timedelta(hours=2))
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = str(self.token)

    def test_summary_persists_across_requests_and_is_session_scoped(self):
        url = '/api/v1/responsible-gaming/session-summary'
        self.assertEqual(self.client.get(url).status_code, 200)
        now = timezone.now()
        AuthSession.objects.filter(jti=self.token['jti']).update(started_at=now-timedelta(hours=1))
        room = Room.objects.first()
        round = RouletteService.tick(room.pk)
        response = self.client.post(f'/api/v1/rooms/{room.pk}/bets', {'round_id': round.pk, 'kind': 'COLOR', 'selection': 'rojo', 'chips': [10], 'reference': str(uuid4())}, format='json')
        self.assertEqual(response.status_code, 201)
        summary = self.client.get(url).data
        self.assertGreaterEqual(summary['duration_seconds'], 3600)
        self.assertEqual(summary['pending_wagered'], '10.00')
        with patch('roulette.services.timezone.now', return_value=round.closes_at):
            RouletteService.tick(room.pk)
        with patch('roulette.services.rng.spin', return_value=1):
            RouletteService.tick(room.pk)
        summary = self.client.get(url).data
        self.assertEqual((summary['total_wagered'], summary['total_paid'], summary['net']), ('10.00', '20.00', '10.00'))
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = str(AccessToken.for_user(self.user))
        self.assertEqual(self.client.get(url).data['total_wagered'], '0.00')
