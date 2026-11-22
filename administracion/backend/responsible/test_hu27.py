from datetime import timedelta
from unittest.mock import patch
from uuid import uuid4
from django.conf import settings
from django.utils import timezone
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken
from accounts.models import User
from roulette.models import Room
from roulette.services import RouletteService
from wallet.models import Wallet
from .models import DailyLimit, SelfExclusion


class HU27SelfExclusionTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email='self@example.com', dni='12345678', birth_date='2000-01-01', password='Palace123!')
        self.token = str(AccessToken.for_user(self.user))
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = self.token

    def test_exclusion_revokes_session_and_blocks_login_and_direct_bet(self):
        Wallet.objects.create(user=self.user, balance=1000, bonus_balance=1000)
        DailyLimit.objects.create(user=self.user, amount=1000)
        room = Room.objects.first()
        round = RouletteService.tick(room.pk)
        response = self.client.post('/api/v1/responsible-gaming/self-exclude', {'days': 7}, format='json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(SelfExclusion.objects.get().days, 7)
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = self.token
        self.assertIn(self.client.get('/api/v1/auth/me').status_code, [401, 403])
        self.assertEqual(self.client.post('/api/v1/auth/login', {'email': self.user.email, 'password': 'Palace123!'}, format='json').status_code, 403)
        from rest_framework.exceptions import PermissionDenied
        with self.assertRaises(PermissionDenied):
            RouletteService.place_bet(self.user, room.pk, {'round_id': round.pk, 'kind': 'COLOR', 'selection': 'rojo', 'chips': [10], 'reference': str(uuid4())})

    def test_invalid_period_and_no_cancel_endpoint(self):
        self.assertEqual(self.client.post('/api/v1/responsible-gaming/self-exclude', {'days': 1}, format='json').status_code, 400)
        self.assertEqual(self.client.delete('/api/v1/responsible-gaming/self-exclude').status_code, 405)

    def test_login_allowed_when_period_ends(self):
        self.user.self_excluded_until = timezone.now()-timedelta(seconds=1)
        self.user.save()
        response = self.client.post('/api/v1/auth/login', {'email': self.user.email, 'password': 'Palace123!'}, format='json')
        self.assertEqual(response.status_code, 200)
