from django.conf import settings
from rest_framework.test import APITestCase, APIClient


class HU02LogoutTests(APITestCase):
    def test_logout_removes_browser_cookie(self):
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = 'expired-or-invalid'
        response = self.client.post('/api/v1/auth/logout')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.cookies[settings.JWT_ACCESS_COOKIE]['max-age'], 0)

    def test_logout_requires_csrf_in_browser(self):
        browser = APIClient(enforce_csrf_checks=True)
        self.assertEqual(browser.post('/api/v1/auth/logout').status_code, 403)
