from unittest.mock import patch

from roulette.models import Game, Room
from .helpers import AdminTestBase


class HU44GameEditTests(AdminTestBase):
    def setUp(self):
        super().setUp()
        self.game = Game.objects.get(slug="ruleta-europea")
        self.url = f"/api/v1/admin/games/{self.game.pk}"

    def test_hu44_updates_limits_and_rtp_and_notifies_active_rooms(self):
        rooms = list(Room.objects.filter(game=self.game))
        self.assertTrue(rooms)
        with patch("admin_api.views.after_commit") as published, self.captureOnCommitCallbacks(execute=True):
            response = self.client.put(self.url, {"min_bet": "25.00", "max_bet": "750.00", "rtp": "96.50"}, format="json")
        self.assertEqual(response.status_code, 200, response.data)
        self.game.refresh_from_db()
        self.assertEqual((str(self.game.min_bet), str(self.game.max_bet), str(self.game.rtp)), ("25.00", "750.00", "96.50"))
        for room in rooms:
            room.refresh_from_db()
            self.assertEqual((room.min_bet, room.max_bet), (25, 750))
        groups = {call.args[0] for call in published.call_args_list if call.args[1] == "room:limits"}
        self.assertEqual(groups, {f"room.{room.pk}" for room in rooms})
        self.as_player()
        state = self.client.get(f"/api/v1/rooms/{rooms[0].pk}/state").data
        self.assertEqual((state["min_bet"], state["max_bet"]), ("25.00", "750.00"))

    def test_hu44_rejects_min_bet_greater_than_max_bet(self):
        response = self.client.put(self.url, {"min_bet": "99999.00"}, format="json")
        self.assertEqual(response.status_code, 400)
        self.assertIn("min_bet", response.data)
        self.game.refresh_from_db()
        self.assertEqual(self.game.min_bet, 10)

    def test_hu44_only_admins(self):
        self.as_player()
        self.assertEqual(self.client.put(self.url, {"rtp": "90.00"}, format="json").status_code, 403)
