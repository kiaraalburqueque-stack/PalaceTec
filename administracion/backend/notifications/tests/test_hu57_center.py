from asgiref.sync import async_to_sync
from django.conf import settings
from channels.testing import WebsocketCommunicator
from django.test import override_settings
from rest_framework.test import APITestCase, APITransactionTestCase

from accounts.models import User
from notifications.models import Notification
from notifications.services import notify


class HU57CenterTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="campana@palacetec.pe", dni="31313131", birth_date="2000-01-01")
        self.other = User.objects.create_user(email="ajeno@palacetec.pe", dni="32323232", birth_date="2000-01-01")
        self.client.force_authenticate(self.user)
        for index in range(3):
            notify(self.user, "GAME", f"Aviso {index}", "Mensaje")
        notify(self.other, "GAME", "Ajeno", "No debe verse")

    def test_hu57_lists_own_notifications_with_unread_counter(self):
        data = self.client.get("/api/v1/notifications").data
        self.assertEqual(data["unread_count"], 3)
        self.assertEqual([item["title"] for item in data["results"]], ["Aviso 2", "Aviso 1", "Aviso 0"])

    def test_hu57_counter_drops_when_marking_read(self):
        first = Notification.objects.filter(user=self.user).last()
        self.assertEqual(self.client.post(f"/api/v1/notifications/{first.pk}/read").data["unread_count"], 2)
        self.assertEqual(self.client.post("/api/v1/notifications/read-all").data["unread_count"], 0)
        self.assertEqual(self.client.get("/api/v1/notifications").data["unread_count"], 0)
        self.assertEqual(Notification.objects.filter(user=self.other, read_at__isnull=True).count(), 1)
        foreign = Notification.objects.get(user=self.other)
        self.assertEqual(self.client.post(f"/api/v1/notifications/{foreign.pk}/read").status_code, 404)

    def test_hu57_requires_login(self):
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get("/api/v1/notifications").status_code, 401)


@override_settings(LOGIN_RATE_LIMIT="1000/min")
class HU57RealtimeTests(APITransactionTestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="vivo@palacetec.pe", dni="33333333", birth_date="2000-01-01", password="Palace123!")
        response = self.client.post("/api/v1/auth/login", {"email": self.user.email, "password": "Palace123!"}, format="json")
        self.token = response.cookies[settings.JWT_ACCESS_COOKIE].value

    def test_hu57_new_notification_arrives_in_real_time(self):
        from config.asgi import application

        async def scenario():
            socket = WebsocketCommunicator(application, "/ws/notifications/", headers=[
                (b"origin", b"http://localhost:5173"), (b"cookie", f"{settings.JWT_ACCESS_COOKIE}={self.token}".encode())])
            self.assertTrue((await socket.connect())[0])
            self.assertEqual(await socket.receive_json_from(), {"event": "notification:count", "data": {"unread_count": 0}})
            from channels.db import database_sync_to_async
            await database_sync_to_async(notify)(self.user, "SECURITY", "Nuevo acceso", "Detectamos un inicio de sesión")
            message = await socket.receive_json_from(timeout=2)
            self.assertEqual(message["event"], "notification:new")
            self.assertEqual((message["data"]["title"], message["data"]["unread_count"]), ("Nuevo acceso", 1))
            await socket.disconnect()

        async_to_sync(scenario)()

    def test_hu57_socket_rejects_anonymous(self):
        from config.asgi import application

        async def scenario():
            socket = WebsocketCommunicator(application, "/ws/notifications/", headers=[(b"origin", b"http://localhost:5173")])
            connected, code = await socket.connect()
            self.assertFalse(connected)

        async_to_sync(scenario)()
