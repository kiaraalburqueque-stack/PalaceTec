from datetime import timedelta
from io import StringIO
from unittest.mock import patch
from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from channels.testing import WebsocketCommunicator
from django.conf import settings
from django.core.management import call_command
from django.utils import timezone
from rest_framework.test import APITransactionTestCase
from rest_framework_simplejwt.tokens import AccessToken
from accounts.models import User
from config.asgi import application
from .models import Room, RoomConnection


class HU10WebsocketTests(APITransactionTestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="socket@example.com", dni="12345678", birth_date="2000-01-01")
        self.room = Room.objects.create(name="socket")
        self.token = str(AccessToken.for_user(self.user))

    def communicator(self, token=None, origin="http://localhost:5173", room=None):
        headers = [(b"origin", origin.encode())]
        if token is not None:
            headers.append((b"cookie", f"{settings.JWT_ACCESS_COOKIE}={token}".encode()))
        return WebsocketCommunicator(application, f"/ws/roulette/{room or self.room.pk}/", headers=headers)

    def test_cookie_connect_sync_ping_and_disconnect(self):
        async def scenario():
            socket = self.communicator(self.token)
            self.assertTrue((await socket.connect())[0])
            state = await socket.receive_json_from()
            self.assertEqual(state["event"], "room:state")
            self.assertEqual(state["data"]["room_id"], self.room.pk)
            self.assertEqual((await socket.receive_json_from())["event"], "room:user_joined")
            await socket.send_json_to({"event": "ping"})
            self.assertEqual((await socket.receive_json_from())["event"], "pong")
            await socket.send_json_to({"event": "room:sync"})
            self.assertEqual((await socket.receive_json_from())["event"], "room:state")
            await socket.send_json_to({"event": "round:result", "winning_number": 1})
            self.assertEqual((await socket.receive_json_from())["event"], "error")
            await socket.disconnect()
        async_to_sync(scenario)()
        self.assertFalse(RoomConnection.objects.exists())

    def test_missing_invalid_expired_cookie_or_origin_rejected(self):
        expired = AccessToken.for_user(self.user)
        expired.set_exp(lifetime=timedelta(seconds=-1))
        async def scenario():
            for token, origin in ((None, "http://localhost:5173"), ("invalid", "http://localhost:5173"), (str(expired), "http://localhost:5173"), (self.token, "https://untrusted.test")):
                socket = self.communicator(token, origin)
                self.assertFalse((await socket.connect())[0])
                await socket.disconnect()
        async_to_sync(scenario)()
        self.assertFalse(RoomConnection.objects.exists())

    def test_missing_room_rejected(self):
        async def scenario():
            socket = self.communicator(self.token, room=999999)
            self.assertEqual(await socket.connect(), (False, 4404))
            await socket.disconnect()
        async_to_sync(scenario)()

    def test_private_wallet_event_only_reaches_owner(self):
        other = User.objects.create_user(email="other@example.com", dni="87654321", birth_date="2000-01-01")
        other_token = str(AccessToken.for_user(other))
        async def scenario():
            owner = self.communicator(self.token)
            stranger = self.communicator(other_token)
            await owner.connect()
            await owner.receive_json_from()
            await owner.receive_json_from()
            await stranger.connect()
            await stranger.receive_json_from()
            await stranger.receive_json_from()
            await owner.receive_json_from()  # ingreso del segundo usuario
            await get_channel_layer().group_send(f"user.{self.user.pk}", {"type": "push.event", "event": "wallet:update", "data": {"balance": "123.00"}})
            self.assertEqual((await owner.receive_json_from())["data"]["balance"], "123.00")
            self.assertTrue(await stranger.receive_nothing(timeout=0.05))
            await stranger.disconnect()
            self.assertEqual((await owner.receive_json_from())["event"], "room:user_left")
            await owner.disconnect()
        async_to_sync(scenario)()


class HU16ClockCommandTests(APITransactionTestCase):
    def test_once_creates_round_and_prunes_stale_presence(self):
        room = Room.objects.create(name="reloj")
        user = User.objects.create_user(email="clock@example.com", dni="12345678", birth_date="2000-01-01")
        RoomConnection.objects.create(room=room, user=user, channel_name="expired", expires_at=timezone.now()-timedelta(seconds=1))
        call_command("runroulette", once=True, stdout=StringIO())
        self.assertEqual(room.rounds.count(), 1)
        self.assertFalse(RoomConnection.objects.exists())
