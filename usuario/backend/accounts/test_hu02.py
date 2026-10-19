from datetime import date, timedelta
from unittest.mock import patch

from django.conf import settings
from django.test import override_settings
from django.utils import timezone
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken

from .models import User


class HU02LoginTests(APITestCase):
    def setUp(self):
        self.password = 'Palace123!'
        self.user = User.objects.create_user(
            email='kiara@example.com', dni='01234567',
            birth_date=date(2000, 1, 1), password=self.password,
        )

    def login(self, **changes):
        return self.client.post('/api/v1/auth/login', {
            'email': self.user.email, 'password': self.password, **changes,
        }, format='json')

    @override_settings(DEBUG=True)
    def test_success_and_cookie(self):
        response = self.login()
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data, {'id': self.user.pk, 'email': self.user.email})
        cookie = response.cookies[settings.JWT_ACCESS_COOKIE]
        self.assertTrue(cookie['httponly'])
        self.assertEqual(cookie['samesite'], 'Lax')
        self.assertFalse(cookie['secure'])
        self.assertEqual(cookie['max-age'], 900)
        token = AccessToken(cookie.value)
        self.assertEqual(token['exp'] - token['iat'], 900)

    @override_settings(DEBUG=False)
    def test_production_cookie_is_secure(self):
        self.assertTrue(self.login().cookies[settings.JWT_ACCESS_COOKIE]['secure'])

    def test_wrong_password(self):
        response = self.login(password='incorrecta')
        self.assertEqual(response.status_code, 401)
        self.assertEqual(response.data, {'detail': 'Credenciales inválidas'})
        self.assertNotIn(settings.JWT_ACCESS_COOKIE, response.cookies)
        self.user.refresh_from_db()
        self.assertEqual(self.user.failed_attempts, 1)

    def test_unknown_email_has_same_message(self):
        wrong_password = self.login(password='incorrecta')
        unknown_email = self.login(email='unknown@example.com')
        self.assertEqual(unknown_email.status_code, 401)
        self.assertEqual(unknown_email.data, wrong_password.data)

    def test_fifth_failure_locks_account(self):
        now = timezone.now()
        with patch('accounts.views.timezone.now', return_value=now):
            for _ in range(4):
                self.assertEqual(self.login(password='incorrecta').status_code, 401)
            response = self.login(password='incorrecta')
            self.assertEqual(response.status_code, 429)
            self.assertEqual(response.data['retry_after'], 900)
            self.assertEqual(response['Retry-After'], '900')
        self.user.refresh_from_db()
        self.assertEqual(self.user.failed_attempts, 5)
        self.assertEqual(self.user.locked_until, now + timedelta(minutes=15))
        with patch('accounts.views.timezone.now', return_value=now + timedelta(seconds=60)):
            response = self.login()
            self.assertEqual(response.status_code, 429)
            self.assertEqual(response.data['retry_after'], 840)
            self.assertNotIn(settings.JWT_ACCESS_COOKIE, response.cookies)

    def test_success_resets_counter(self):
        for _ in range(3):
            self.login(password='incorrecta')
        self.assertEqual(self.login().status_code, 200)
        self.user.refresh_from_db()
        self.assertEqual(self.user.failed_attempts, 0)
        self.assertIsNone(self.user.locked_until)
        self.assertEqual(self.login(password='incorrecta').status_code, 401)

    def test_expired_lock_allows_login(self):
        self.user.failed_attempts = 5
        self.user.locked_until = timezone.now() - timedelta(seconds=1)
        self.user.save()
        self.assertEqual(self.login().status_code, 200)
        self.user.refresh_from_db()
        self.assertEqual(self.user.failed_attempts, 0)
        self.assertIsNone(self.user.locked_until)

    def test_expired_lock_starts_new_failure_sequence(self):
        self.user.failed_attempts = 5
        self.user.locked_until = timezone.now() - timedelta(seconds=1)
        self.user.save()
        self.assertEqual(self.login(password='incorrecta').status_code, 401)
        self.user.refresh_from_db()
        self.assertEqual(self.user.failed_attempts, 1)
        self.assertIsNone(self.user.locked_until)

    def test_me_with_valid_cookie(self):
        self.login()
        response = self.client.get('/api/v1/auth/me')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data, {'id': self.user.pk, 'email': self.user.email, 'role': 'USER'})

    def test_me_without_cookie(self):
        self.assertEqual(self.client.get('/api/v1/auth/me').status_code, 401)

    def test_me_with_invalid_cookie(self):
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = 'invalid'
        self.assertEqual(self.client.get('/api/v1/auth/me').status_code, 401)

    def test_me_with_expired_cookie(self):
        token = AccessToken.for_user(self.user)
        token.set_exp(from_time=timezone.now() - timedelta(minutes=16))
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = str(token)
        self.assertEqual(self.client.get('/api/v1/auth/me').status_code, 401)
