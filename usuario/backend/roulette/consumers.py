import asyncio
from contextlib import suppress
from datetime import timedelta
from http.cookies import SimpleCookie
import time
from json import JSONDecodeError

from channels.db import database_sync_to_async
from channels.generic.websocket import AsyncJsonWebsocketConsumer
from django.conf import settings
from django.db import transaction
from django.utils import timezone
from rest_framework_simplejwt.tokens import AccessToken
from rest_framework_simplejwt.exceptions import TokenError

from .models import Room, RoomConnection
from .state import room_state


class RouletteConsumer(AsyncJsonWebsocketConsumer):
    async def connect(self):
        self.joined = False
        self.heartbeat_task = None
        user = self.scope["user"]
        if not user.is_authenticated:
            await self.close(code=4401)
            return
        self.room_id = self.scope["url_route"]["kwargs"]["room_id"]
        self.room_group = f"room.{self.room_id}"
        self.user_group = f"user.{user.pk}"
        cookies = SimpleCookie()
        cookies.load(dict(self.scope.get("headers", [])).get(b"cookie", b"").decode("latin1"))
        try:
            token = AccessToken(cookies[settings.JWT_ACCESS_COOKIE].value)
            self.token_expiry = token["exp"]
            self.session_group = f"session.{token['jti']}"
            self.token_jti = token["jti"]
        except (KeyError, TokenError):
            await self.close(code=4401)
            return
        if not await self.join_room():
            await self.close(code=4404)
            return
        await self.channel_layer.group_add(self.room_group, self.channel_name)
        await self.channel_layer.group_add(self.user_group, self.channel_name)
        await self.channel_layer.group_add(self.session_group, self.channel_name)
        self.joined = True
        await self.accept()
        await self.send_json({"event": "room:state", "data": await database_sync_to_async(room_state)(self.room_id)})
        await self.channel_layer.group_send(self.room_group, {"type": "push.event", "event": "room:user_joined", "data": {"user_id": user.pk}})
        self.heartbeat_task = asyncio.create_task(self.heartbeat())

    @database_sync_to_async
    def join_room(self):
        with transaction.atomic():
            room = Room.objects.select_for_update().filter(pk=self.room_id, is_active=True).first()
            if room is None:
                return False
            RoomConnection.objects.create(room=room, user=self.scope["user"], channel_name=self.channel_name,
                expires_at=timezone.now()+timedelta(seconds=60))
            return True

    @database_sync_to_async
    def refresh_connection(self):
        from accounts.models import AuthSession
        if not AuthSession.objects.filter(jti=self.token_jti, revoked_at__isnull=True, expires_at__gt=timezone.now()).exists():
            return 0
        return RoomConnection.objects.filter(channel_name=self.channel_name, room__is_active=True, user__is_active=True).update(expires_at=timezone.now()+timedelta(seconds=60))

    async def heartbeat(self):
        while True:
            # La conexion tambien vence cuando vence el JWT, aunque no haya mensajes.
            await asyncio.sleep(min(20, max(0.1, self.token_expiry-time.time())))
            if time.time() >= self.token_expiry or not await self.refresh_connection():
                await self.close(code=4401)
                return

    async def disconnect(self, close_code):
        if self.heartbeat_task:
            self.heartbeat_task.cancel()
            with suppress(asyncio.CancelledError):
                await self.heartbeat_task
        if self.joined:
            await self.channel_layer.group_discard(self.room_group, self.channel_name)
            await self.channel_layer.group_discard(self.user_group, self.channel_name)
            await self.channel_layer.group_discard(self.session_group, self.channel_name)
            await database_sync_to_async(lambda: RoomConnection.objects.filter(channel_name=self.channel_name).delete())()
            await self.channel_layer.group_send(self.room_group, {"type": "push.event", "event": "room:user_left", "data": {"user_id": self.scope["user"].pk}})

    async def receive(self, text_data=None, bytes_data=None, **kwargs):
        if text_data is None or len(text_data) > 4096:
            await self.close(code=1009)
            return
        try:
            await super().receive(text_data=text_data, **kwargs)
        except JSONDecodeError:
            await self.send_json({"event": "error", "data": {"detail": "JSON invalido."}})

    async def receive_json(self, content, **kwargs):
        if time.time() >= self.token_expiry or not await self.refresh_connection():
            await self.close(code=4401)
            return
        if not isinstance(content, dict):
            await self.send_json({"event": "error", "data": {"detail": "Se requiere un objeto JSON."}})
        elif content.get("event") == "room:sync":
            await self.send_json({"event": "room:state", "data": await database_sync_to_async(room_state)(self.room_id)})
        elif content.get("event") == "ping":
            await self.send_json({"event": "pong", "data": {}})
        else:
            await self.send_json({"event": "error", "data": {"detail": "Evento no permitido. Envia apuestas por HTTP."}})

    async def push_event(self, message):
        if message["event"] == "session:revoked" or time.time() >= self.token_expiry or not await self.refresh_connection():
            await self.close(code=4401)
            return
        await self.send_json({"event": message["event"], "data": message["data"]})
