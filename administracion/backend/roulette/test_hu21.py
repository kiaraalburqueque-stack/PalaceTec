from asgiref.sync import async_to_sync
from channels.db import database_sync_to_async
from channels.layers import get_channel_layer
from channels.testing import WebsocketCommunicator
from django.conf import settings
from django.utils import timezone
from rest_framework.test import APITransactionTestCase
from rest_framework_simplejwt.tokens import AccessToken
from accounts.models import User, AuthSession
from config.asgi import application
from .models import Room


class HU21RevokedConnectionTests(APITransactionTestCase):
    def setUp(self):
        self.user = User.objects.create_user(email='socket21@example.com', dni='12345678', birth_date='2000-01-01')
        self.room = Room.objects.create(name='HU21')
        self.token = AccessToken.for_user(self.user)

    def communicator(self):
        return WebsocketCommunicator(application, f'/ws/roulette/{self.room.pk}/', headers=[
            (b'origin', b'http://localhost:5173'),
            (b'cookie', f'{settings.JWT_ACCESS_COOKIE}={self.token}'.encode())])

    def test_revocation_closes_live_socket_and_rejects_reconnect(self):
        async def scenario():
            socket = self.communicator()
            self.assertTrue((await socket.connect())[0])
            await socket.receive_json_from()
            await socket.receive_json_from()
            await database_sync_to_async(lambda: AuthSession.objects.filter(jti=self.token['jti']).update(revoked_at=timezone.now()))()
            await get_channel_layer().group_send(f"session.{self.token['jti']}", {
                'type': 'push.event', 'event': 'session:revoked', 'data': {}})
            self.assertEqual((await socket.receive_output())['code'], 4401)
            await socket.disconnect()
            reconnect = self.communicator()
            self.assertEqual(await reconnect.connect(), (False, 4401))
            await reconnect.disconnect()
        async_to_sync(scenario)()

    def test_self_excluded_user_cannot_connect_with_preexisting_token(self):
        from datetime import timedelta
        self.user.self_excluded_until = timezone.now()+timedelta(days=7)
        self.user.save()
        async def scenario():
            socket = self.communicator()
            self.assertEqual(await socket.connect(), (False, 4401))
            await socket.disconnect()
        async_to_sync(scenario)()
