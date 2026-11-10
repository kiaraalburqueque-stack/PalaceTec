import re

from django.core import mail
from django.test import override_settings
from rest_framework.test import APIClient, APITestCase

from notifications.models import Notification
from .models import AuthSession, LoginHistory, User

CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36"
FIREFOX = "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0"


@override_settings(LOGIN_RATE_LIMIT="1000/min")
class HU56SuspiciousLoginTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="alerta@palacetec.pe", dni="51515151", birth_date="2000-01-01", password="Palace123!")

    def login(self, ip="10.0.0.1", agent=CHROME, password="Palace123!"):
        with self.captureOnCommitCallbacks(execute=True):
            return APIClient().post("/api/v1/auth/login", {"email": self.user.email, "password": password},
                                    format="json", REMOTE_ADDR=ip, HTTP_USER_AGENT=agent)

    def test_hu56_first_and_known_logins_send_no_alert(self):
        self.assertEqual(self.login().status_code, 200)
        self.assertEqual(self.login().status_code, 200)
        self.assertEqual(len(mail.outbox), 0)
        self.assertEqual(LoginHistory.objects.filter(user=self.user).count(), 2)

    def test_hu56_new_ip_sends_mail_with_not_me_button(self):
        self.login()
        self.login(ip="200.48.1.9")
        self.assertEqual(len(mail.outbox), 1)
        message = mail.outbox[0]
        self.assertIn("No fui yo", message.alternatives[0][0])
        self.assertIn("200.48.1.9", message.body)
        entry = LoginHistory.objects.order_by("-id").first()
        self.assertTrue(entry.is_new_ip and entry.alert_sent)
        self.assertFalse(entry.is_new_device)
        self.assertTrue(Notification.objects.filter(user=self.user, category="SECURITY").exists())

    def test_hu56_new_device_sends_mail_and_failed_login_is_not_recorded(self):
        self.login()
        self.login(password="Incorrecta1!")
        self.assertEqual(LoginHistory.objects.count(), 1)
        self.login(agent=FIREFOX)
        self.assertEqual(len(mail.outbox), 1)
        self.assertIn("Firefox en Linux", mail.outbox[0].body)

    def test_hu56_not_me_locks_account_and_revokes_sessions(self):
        self.login()
        self.login(ip="200.48.1.9")
        token = re.search(r"/seguridad/no-fui-yo/([\w-]+)", mail.outbox[0].body).group(1)
        response = self.client.post("/api/v1/auth/not-me", {"token": token}, format="json")
        self.assertEqual(response.status_code, 200)
        self.user.refresh_from_db()
        self.assertIsNotNone(self.user.locked_until)
        self.assertFalse(AuthSession.objects.filter(user=self.user, revoked_at__isnull=True).exists())
        self.assertEqual(self.login().status_code, 429)
        self.assertEqual(self.client.post("/api/v1/auth/not-me", {"token": "falso"}, format="json").status_code, 400)
        # El enlace vence a las 24 horas del acceso.
        from datetime import timedelta
        from django.utils import timezone
        LoginHistory.objects.filter(reported_at__isnull=False).update(reported_at=None, created_at=timezone.now() - timedelta(hours=25))
        self.assertEqual(self.client.post("/api/v1/auth/not-me", {"token": token}, format="json").status_code, 400)

    def test_hu56_security_preference_off_skips_alert(self):
        from notifications.services import preferences
        setting = preferences(self.user)
        setting.security = False
        setting.save()
        self.login()
        self.login(ip="200.48.1.9")
        self.assertEqual(len(mail.outbox), 0)
