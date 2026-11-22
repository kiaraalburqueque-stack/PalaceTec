from uuid import uuid4

from asgiref.sync import async_to_sync
from channels.testing import WebsocketCommunicator
from django.conf import settings
from rest_framework.test import APITransactionTestCase
from rest_framework_simplejwt.tokens import AccessToken

from accounts.models import User
from config.asgi import application
from responsible.models import DailyLimit
from wallet.models import Wallet
from .models import GameRound, Room
from .services import RouletteService


class HU59ReconnectTests(APITransactionTestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="reconecta@example.com", dni="12345678", birth_date="2000-01-01")
        Wallet.objects.create(user=self.user, balance=1000)
        DailyLimit.objects.create(user=self.user, amount=100000)
        self.room = Room.objects.create(name="reconexion")
        self.round = RouletteService.tick(self.room.pk)
        self.token = str(AccessToken.for_user(self.user))
        self.client.force_authenticate(self.user)
        for selection in ("rojo", "negro"):
            self.client.post(f"/api/v1/rooms/{self.room.pk}/bets", {"round_id": self.round.pk, "kind": "COLOR",
                             "selection": selection, "chips": [50], "reference": str(uuid4())}, format="json")

    def resume(self):
        async def scenario():
            socket = WebsocketCommunicator(application, f"/ws/roulette/{self.room.pk}/", headers=[
                (b"origin", b"http://localhost:5173"), (b"cookie", f"{settings.JWT_ACCESS_COOKIE}={self.token}".encode())])
            self.assertTrue((await socket.connect())[0])
            await socket.receive_json_from()  # room:state
            await socket.receive_json_from()  # room:user_joined
            await socket.send_json_to({"event": "room:resume"})
            message = await socket.receive_json_from()
            await socket.disconnect()
            return message
        return async_to_sync(scenario)()

    def test_hu59_reconnect_restores_round_and_active_bets(self):
        message = self.resume()
        self.assertEqual(message["event"], "RECONNECT_SUCCESS")
        self.assertEqual(message["data"]["round_id"], self.round.pk)
        self.assertEqual(sorted(bet["selection"] for bet in message["data"]["bets"]), ["negro", "rojo"])
        self.assertEqual(message["data"]["total_amount"], "100.00")
        self.assertEqual(message["data"]["state"]["round"]["status"], "OPEN")

    def test_hu59_bets_of_settled_round_are_not_recovered(self):
        GameRound.objects.filter(pk=self.round.pk).update(status="CLOSED")
        RouletteService.tick(self.room.pk)  # liquida
        RouletteService.tick(self.room.pk)  # abre una ronda nueva
        message = self.resume()
        self.assertNotEqual(message["data"]["round_id"], self.round.pk)
        self.assertEqual(message["data"]["bets"], [])
