from django.core import mail
from rest_framework.test import APITestCase

from accounts.models import User
from notifications.models import Notification
from notifications.services import notify


class HU58PreferencesTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="prefs@palacetec.pe", dni="41414141", birth_date="2000-01-01")
        self.client.force_authenticate(self.user)
        self.url = "/api/v1/users/notification-preferences"

    def test_hu58_defaults_and_update(self):
        self.assertEqual(self.client.get(self.url).data, {"promotions": True, "security": True, "game_alerts": True})
        response = self.client.put(self.url, {"promotions": False}, format="json")
        self.assertEqual(response.data, {"promotions": False, "security": True, "game_alerts": True})
        self.assertEqual(self.client.put(self.url, {"promotions": "talvez"}, format="json").status_code, 400)

    def test_hu58_no_promotional_email_when_flag_is_false(self):
        self.client.put(self.url, {"promotions": False}, format="json")
        with self.captureOnCommitCallbacks(execute=True):
            result = notify(self.user, "PROMOTION", "Bono", "Gira gratis", email=True)
        self.assertIsNone(result)
        self.assertEqual(len(mail.outbox), 0)
        self.assertFalse(Notification.objects.filter(user=self.user).exists())

    def test_hu58_enabled_category_creates_and_emails(self):
        self.client.put(self.url, {"game_alerts": False}, format="json")
        with self.captureOnCommitCallbacks(execute=True):
            notify(self.user, "PROMOTION", "Bono", "Gira gratis", email=True)
            notify(self.user, "GAME", "Ganaste", "Ronda 1")
        self.assertEqual(len(mail.outbox), 1)
        self.assertEqual(list(Notification.objects.values_list("category", flat=True)), ["PROMOTION"])

    def test_hu58_admin_promotion_respects_preferences(self):
        other = User.objects.create_user(email="promo@palacetec.pe", dni="42424242", birth_date="2000-01-01")
        self.client.put(self.url, {"promotions": False}, format="json")
        admin = User.objects.create_user(email="jefe@palacetec.pe", dni="43434343", birth_date="2000-01-01", is_staff=True)
        self.client.force_authenticate(admin)
        with self.captureOnCommitCallbacks(execute=True):
            response = self.client.post("/api/v1/admin/promotions", {"title": "Fin de semana", "message": "Doble bono", "send_email": True}, format="json")
        self.assertEqual(response.data, {"sent": 1, "skipped": 1})
        self.assertEqual([m.to for m in mail.outbox], [[other.email]])
