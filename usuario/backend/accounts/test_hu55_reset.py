import re
from datetime import timedelta

from django.core import mail
from django.test import override_settings
from django.utils import timezone
from rest_framework.test import APITestCase

from .models import AuthSession, PasswordResetToken, User


@override_settings(LOGIN_RATE_LIMIT="1000/min", FRONTEND_URL="http://localhost:5173")
class HU55ResetPasswordTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="olvido@palacetec.pe", dni="12121212", birth_date="2000-01-01", password="Palace123!")

    def request_link(self, email="olvido@palacetec.pe"):
        with self.captureOnCommitCallbacks(execute=True):
            response = self.client.post("/api/v1/auth/forgot-password", {"email": email}, format="json")
        self.assertEqual(response.status_code, 200)
        return response

    def last_token(self):
        return re.search(r"/reset-password/([\w-]+)", mail.outbox[-1].body).group(1)

    def reset(self, token, password="Nueva123!"):
        return self.client.post("/api/v1/auth/reset-password", {"token": token, "new_password": password}, format="json")

    def test_hu55_sends_link_valid_15_minutes_and_stores_only_hash(self):
        self.request_link()
        self.assertEqual(len(mail.outbox), 1)
        self.assertIn("http://localhost:5173/reset-password/", mail.outbox[0].body)
        token = self.last_token()
        record = PasswordResetToken.objects.get()
        self.assertNotEqual(record.token_hash, token)
        self.assertAlmostEqual((record.expires_at - timezone.now()).total_seconds(), 15 * 60, delta=5)

    def test_hu55_unknown_email_gets_same_answer_without_mail(self):
        known = self.request_link().data
        unknown = self.request_link("nadie@palacetec.pe").data
        self.assertEqual(known, unknown)
        self.assertEqual(len(mail.outbox), 1)

    def test_hu55_resets_password_and_revokes_sessions(self):
        self.client.post("/api/v1/auth/login", {"email": self.user.email, "password": "Palace123!"}, format="json")
        self.request_link()
        response = self.reset(self.last_token())
        self.assertEqual(response.status_code, 200)
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password("Nueva123!"))
        self.assertFalse(AuthSession.objects.filter(user=self.user, revoked_at__isnull=True).exists())

    def test_hu55_new_password_must_differ(self):
        self.request_link()
        response = self.reset(self.last_token(), "Palace123!")
        self.assertEqual(response.status_code, 400)
        self.assertIn("new_password", response.data)
        self.assertEqual(self.reset(self.last_token(), "debil").status_code, 400)

    def test_hu55_used_or_expired_token_is_rejected(self):
        self.request_link()
        token = self.last_token()
        self.assertEqual(self.reset(token).status_code, 200)
        used = self.reset(token, "Otra456!")
        self.assertEqual((used.status_code, used.data["code"]), (400, "invalid_token"))
        self.request_link()
        token = self.last_token()
        PasswordResetToken.objects.filter(used_at__isnull=True).update(expires_at=timezone.now() - timedelta(seconds=1))
        self.assertEqual(self.reset(token, "Otra456!").data["code"], "invalid_token")
        self.assertEqual(self.reset("inventado", "Otra456!").status_code, 400)

    def test_hu55_new_link_invalidates_previous(self):
        self.request_link()
        first = self.last_token()
        self.request_link()
        self.assertEqual(self.reset(first).data["code"], "invalid_token")
        self.assertEqual(self.reset(self.last_token()).status_code, 200)
