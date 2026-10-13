from datetime import timedelta
from io import StringIO
from unittest.mock import patch
from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from channels.db import database_sync_to_async
from uuid import uuid4
from wallet.models import Wallet
from .services import RouletteService
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

    def test_entire_round_publishes_balance_and_private_winnings(self):
        Wallet.objects.create(user=self.user, balance=1000, bonus_balance=1000)
        round = RouletteService.tick(self.room.pk)
        data = {"round_id": round.pk, "kind": "STRAIGHT_UP", "selection": "1", "chips": [10], "reference": str(uuid4())}
        async def scenario():
            socket = self.communicator(self.token)
            await socket.connect()
            await socket.receive_json_from()
            await socket.receive_json_from()
            await database_sync_to_async(RouletteService.place_bet)(self.user, self.room.pk, data)
            debit = await socket.receive_json_from()
            self.assertEqual(debit["event"], "wallet:update")
            self.assertEqual(debit["data"]["balance"], "990.00")
            with patch("roulette.services.timezone.now", return_value=round.closes_at):
                await database_sync_to_async(RouletteService.tick)(self.room.pk)
            self.assertEqual((await socket.receive_json_from())["event"], "bets:closed")
            self.assertEqual((await socket.receive_json_from())["event"], "timer:stop")
            with patch("roulette.services.rng.spin", return_value=1):
                await database_sync_to_async(RouletteService.tick)(self.room.pk)
            events = [await socket.receive_json_from() for _ in range(3)]
            by_name = {event["event"]: event["data"] for event in events}
            self.assertEqual(by_name["wallet:update"]["balance"], "1350.00")
            self.assertEqual(by_name["round:result"]["winning_number"], 1)
            self.assertEqual(by_name["round:winnings"]["payout"], "360.00")
            await socket.disconnect()
        async_to_sync(scenario)()

    def test_multiple_simultaneous_clients(self):
        import asyncio
        async def scenario():
            sockets = [self.communicator(self.token) for _ in range(12)]
            results = await asyncio.gather(*(socket.connect() for socket in sockets))
            self.assertTrue(all(connected for connected, _ in results))
            for socket in sockets:
                self.assertEqual((await socket.receive_json_from())["event"], "room:state")
            self.assertEqual(await database_sync_to_async(RoomConnection.objects.count)(), 12)
            await asyncio.gather(*(socket.disconnect() for socket in sockets))
        async_to_sync(scenario)()
        self.assertFalse(RoomConnection.objects.exists())


class HU16ClockCommandTests(APITransactionTestCase):
    def test_once_creates_round_and_prunes_stale_presence(self):
        room = Room.objects.create(name="reloj")
        user = User.objects.create_user(email="clock@example.com", dni="12345678", birth_date="2000-01-01")
        RoomConnection.objects.create(room=room, user=user, channel_name="expired", expires_at=timezone.now()-timedelta(seconds=1))
        call_command("runroulette", once=True, stdout=StringIO())
        self.assertEqual(room.rounds.count(), 1)
        self.assertFalse(RoomConnection.objects.exists())
