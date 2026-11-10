from unittest.mock import patch

from django.test import override_settings

from accounts.models import AuthSession, User
from admin_api.models import SanctionLog
from .helpers import AdminTestBase


@override_settings(LOGIN_RATE_LIMIT="1000/min")
class HU42StatusTests(AdminTestBase):
    def url(self, user):
        return f"/api/v1/admin/users/{user.pk}/status"

    def login(self):
        from rest_framework.test import APIClient
        client = APIClient()
        return client.post("/api/v1/auth/login", {"email": self.player.email, "password": "Palace123!"}, format="json")

    def test_hu42_suspend_requires_reason(self):
        response = self.client.patch(self.url(self.player), {"is_active": False}, format="json")
        self.assertEqual(response.status_code, 400)
        response = self.client.patch(self.url(self.player), {"is_active": False, "reason": "  "}, format="json")
        self.assertEqual(response.status_code, 400)
        self.player.refresh_from_db()
        self.assertTrue(self.player.is_active)

    def test_hu42_suspended_user_gets_403_on_login_and_is_disconnected(self):
        self.assertEqual(self.login().status_code, 200)
        self.assertTrue(AuthSession.objects.filter(user=self.player, revoked_at__isnull=True).exists())
        with patch("admin_api.views.after_commit") as published, self.captureOnCommitCallbacks(execute=True):
            response = self.client.patch(self.url(self.player), {"is_active": False, "reason": "Uso de multicuentas"}, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["status"], "SUSPENDED")
        events = [call.args[1] for call in published.call_args_list]
        self.assertIn("FORCE_DISCONNECT", events)
        self.assertFalse(AuthSession.objects.filter(user=self.player, revoked_at__isnull=True).exists())
        login = self.login()
        self.assertEqual(login.status_code, 403)
        self.assertEqual(login.data["code"], "account_suspended")
        log = SanctionLog.objects.get()
        self.assertEqual((log.admin, log.user, log.action, log.reason), (self.admin, self.player, "SUSPEND", "Uso de multicuentas"))

    def test_hu42_wrong_password_of_suspended_user_is_still_401(self):
        User.objects.filter(pk=self.player.pk).update(is_active=False)
        from rest_framework.test import APIClient
        response = APIClient().post("/api/v1/auth/login", {"email": self.player.email, "password": "otra"}, format="json")
        self.assertEqual(response.status_code, 401)

    def test_hu42_activate_again_allows_login(self):
        self.client.patch(self.url(self.player), {"is_active": False, "reason": "Revisión de cuenta"}, format="json")
        response = self.client.patch(self.url(self.player), {"is_active": True, "reason": "Revisión terminada"}, format="json")
        self.assertEqual(response.data["status"], "ACTIVE")
        self.assertEqual(self.login().status_code, 200)
        self.assertEqual(self.client.get(f"/api/v1/admin/users/{self.player.pk}/sanctions").data[0]["action"], "ACTIVATE")

    def test_hu42_cannot_suspend_self_and_players_get_403(self):
        self.assertEqual(self.client.patch(self.url(self.admin), {"is_active": False, "reason": "Prueba propia"}, format="json").status_code, 400)
        self.as_player()
        self.assertEqual(self.client.patch(self.url(self.admin), {"is_active": False, "reason": "Golpe de estado"}, format="json").status_code, 403)
