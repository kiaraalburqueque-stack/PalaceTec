"""HU57: canal WebSocket personal para recibir notificaciones en tiempo real."""
import asyncio
import time
from contextlib import suppress
from http.cookies import SimpleCookie

from channels.db import database_sync_to_async
from channels.generic.websocket import AsyncJsonWebsocketConsumer
from django.conf import settings
from django.utils import timezone
from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import AccessToken

from accounts.sessions import session_key

FORWARDED = {"notification:new", "wallet:update"}


class NotificationConsumer(AsyncJsonWebsocketConsumer):
    async def connect(self):
        self.groups_joined = []
        self.expiry_task = None
        user = self.scope["user"]
        if not user.is_authenticated:
            await self.close(code=4401)
            return
        cookies = SimpleCookie()
        cookies.load(dict(self.scope.get("headers", [])).get(b"cookie", b"").decode("latin1"))
        try:
            token = AccessToken(cookies[settings.JWT_ACCESS_COOKIE].value)
        except (KeyError, TokenError):
            await self.close(code=4401)
            return
        self.token_expiry = token["exp"]
        self.jti = session_key(token)
        self.groups_joined = [f"user.{user.pk}", f"session.{self.jti}"]
        for group in self.groups_joined:
            await self.channel_layer.group_add(group, self.channel_name)
        await self.accept()
        await self.send_json({"event": "notification:count", "data": {"unread_count": await self.unread()}})
        self.expiry_task = asyncio.create_task(self.close_on_expiry())

    @database_sync_to_async
    def unread(self):
        from .services import unread_count
        return unread_count(self.scope["user"])

    @database_sync_to_async
    def session_alive(self):
        from accounts.models import AuthSession
        return AuthSession.objects.filter(jti=self.jti, revoked_at__isnull=True, expires_at__gt=timezone.now(),
                                          user__is_active=True).exists()

    async def close_on_expiry(self):
        # El socket vive lo mismo que el JWT; el cliente se reconecta tras renovar la sesión.
        await asyncio.sleep(max(0.1, self.token_expiry - time.time()))
        await self.close(code=4401)

    async def disconnect(self, close_code):
        if self.expiry_task:
            self.expiry_task.cancel()
            with suppress(asyncio.CancelledError):
                await self.expiry_task
        for group in self.groups_joined:
            await self.channel_layer.group_discard(group, self.channel_name)

    async def receive_json(self, content, **kwargs):
        if isinstance(content, dict) and content.get("event") == "ping":
            await self.send_json({"event": "pong", "data": {}})

    async def push_event(self, message):
        event = message["event"]
        if event == "FORCE_DISCONNECT":
            await self.send_json({"event": event, "data": message["data"]})
            await self.close(code=4403)
        elif event == "session:revoked" or time.time() >= self.token_expiry or not await self.session_alive():
            await self.close(code=4401)
        elif event in FORWARDED:
            await self.send_json({"event": event, "data": message["data"]})
