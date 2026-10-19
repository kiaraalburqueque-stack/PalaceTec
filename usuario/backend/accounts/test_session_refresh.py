from datetime import timedelta
from django.conf import settings
from django.utils import timezone
from rest_framework.test import APITestCase, APIClient
from rest_framework_simplejwt.tokens import AccessToken
from .models import User, AuthSession


class HU28HU30RefreshTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email='refresh@example.com', dni='12345678', birth_date='2000-01-01', password='Palace123!')
        self.assertEqual(self.client.post('/api/v1/auth/login', {'email': self.user.email, 'password': 'Palace123!'}, format='json').status_code, 200)

    def test_refresh_keeps_session_start_and_rotates_access_jti(self):
        session = AuthSession.objects.get(user=self.user)
        first = AccessToken(self.client.cookies[settings.JWT_ACCESS_COOKIE].value)
        AuthSession.objects.filter(pk=session.pk).update(expires_at=timezone.now()-timedelta(seconds=1))
        self.assertEqual(self.client.post('/api/v1/auth/refresh').status_code, 200)
        second = AccessToken(self.client.cookies[settings.JWT_ACCESS_COOKIE].value)
        self.assertNotEqual(first['jti'], second['jti'])
        self.assertEqual(first['sid'], second['sid'])
        self.assertEqual(self.client.get('/api/v1/auth/me').status_code, 200)
        self.assertEqual(AuthSession.objects.count(), 1)
        self.assertEqual(AuthSession.objects.get().started_at, session.started_at)

    def test_revoked_refresh_cookie_cannot_restore_session(self):
        refresh = self.client.cookies[settings.JWT_REFRESH_COOKIE].value
        self.client.post('/api/v1/auth/logout')
        self.client.cookies[settings.JWT_REFRESH_COOKIE] = refresh
        self.assertEqual(self.client.post('/api/v1/auth/refresh').status_code, 401)

    def test_refreshed_access_cannot_outlive_absolute_session_deadline(self):
        deadline = timezone.now()+timedelta(seconds=60)
        AuthSession.objects.filter(user=self.user).update(refresh_expires_at=deadline)
        self.assertEqual(self.client.post('/api/v1/auth/refresh').status_code, 200)
        token = AccessToken(self.client.cookies[settings.JWT_ACCESS_COOKIE].value)
        self.assertLessEqual(token['exp'], int(deadline.timestamp()))

    def test_logout_works_after_access_expired(self):
        expired = AccessToken(self.client.cookies[settings.JWT_ACCESS_COOKIE].value)
        expired.set_exp(lifetime=timedelta(seconds=-1))
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = str(expired)
        refresh = self.client.cookies[settings.JWT_REFRESH_COOKIE].value
        self.assertEqual(self.client.post('/api/v1/auth/logout').status_code, 200)
        self.client.cookies[settings.JWT_REFRESH_COOKIE] = refresh
        self.assertEqual(self.client.post('/api/v1/auth/refresh').status_code, 401)

    def test_refresh_requires_csrf_and_rejects_tampered_cookie(self):
        browser = APIClient(enforce_csrf_checks=True)
        browser.cookies = self.client.cookies
        self.assertEqual(browser.post('/api/v1/auth/refresh').status_code, 403)
        self.client.cookies[settings.JWT_REFRESH_COOKIE] = 'tampered'
        self.assertEqual(self.client.post('/api/v1/auth/refresh').status_code, 401)
