from django.core.cache import cache
from django.db import IntegrityError, transaction
from django.test import override_settings
from rest_framework.test import APITestCase, APIClient
from .models import User

DATA = {"email": "security@example.com", "dni": "12345678", "birth_date": "2000-01-01", "password": "Palace123!"}


class HU01HU02SecurityTests(APITestCase):
    def setUp(self):
        cache.clear()
        self.addCleanup(cache.clear)

    def test_database_rejects_underage_user(self):
        with self.assertRaises(IntegrityError), transaction.atomic():
            User.objects.create_user(**{**DATA, "birth_date": "2020-01-01"})

    def test_database_nickname_is_case_insensitive(self):
        User.objects.create_user(**DATA, nickname="Kiara")
        with self.assertRaises(IntegrityError), transaction.atomic():
            User.objects.create_user(**{**DATA, "email": "other@example.com", "dni": "87654321"}, nickname="kiara")

    @override_settings(LOGIN_RATE_LIMIT="2/min")
    def test_ip_throttle_also_limits_unknown_emails(self):
        for _ in range(2):
            self.assertEqual(self.client.post("/api/v1/auth/login", {"email": "unknown@example.com", "password": "wrong"}).status_code, 401)
        self.assertEqual(self.client.post("/api/v1/auth/login", {"email": "other@example.com", "password": "wrong"}, HTTP_X_FORWARDED_FOR="203.0.113.99").status_code, 429)

    def test_browser_requires_csrf_for_registration_login_and_profile(self):
        browser = APIClient(enforce_csrf_checks=True)
        self.assertEqual(browser.post("/api/v1/auth/register", DATA).status_code, 403)
        csrf = browser.get("/api/v1/auth/csrf").data["csrfToken"]
        headers = {"HTTP_X_CSRFTOKEN": csrf, "HTTP_ORIGIN": "http://localhost:5173"}
        self.assertEqual(browser.post("/api/v1/auth/register", DATA, **headers).status_code, 201)
        credentials = {"email": DATA["email"], "password": DATA["password"]}
        self.assertEqual(browser.post("/api/v1/auth/login", credentials).status_code, 403)
        self.assertEqual(browser.post("/api/v1/auth/login", credentials, **headers).status_code, 200)
        profile = {"nickname": "secure", "avatar_url": "/avatars/avatar-1.svg"}
        self.assertEqual(browser.put("/api/v1/users/profile", profile).status_code, 403)
        self.assertEqual(browser.put("/api/v1/users/profile", profile, **headers).status_code, 200)
        bad_origin = {**headers, "HTTP_ORIGIN": "https://untrusted.test"}
        self.assertEqual(browser.put("/api/v1/users/profile", profile, **bad_origin).status_code, 403)
