from datetime import date
from django.conf import settings
from django.utils import timezone
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken
from .models import User, AuthSession


class HU30RevocationTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="logout@example.com", dni="12345678", birth_date=date(2000, 1, 1))
        self.token = str(AccessToken.for_user(self.user))
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = self.token

    def test_copied_token_cannot_be_reused_after_logout(self):
        self.assertEqual(self.client.get('/api/v1/auth/me').status_code, 200)
        self.assertEqual(self.client.post('/api/v1/auth/logout').status_code, 200)
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = self.token
        self.assertEqual(self.client.get('/api/v1/auth/me').status_code, 401)
        self.assertIsNotNone(AuthSession.objects.get().revoked_at)

    def test_logout_before_first_authenticated_request(self):
        self.assertEqual(self.client.post('/api/v1/auth/logout').status_code, 200)
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = self.token
        self.assertEqual(self.client.get('/api/v1/auth/me').status_code, 401)

    def test_other_session_remains_valid(self):
        other_token = str(AccessToken.for_user(self.user))
        self.client.post('/api/v1/auth/logout')
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = other_token
        self.assertEqual(self.client.get('/api/v1/auth/me').status_code, 200)
