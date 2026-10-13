from datetime import timedelta
from django.utils import timezone
from rest_framework.test import APITestCase
from accounts.models import User
from .models import Room, RoomConnection


class HU09HU11RoomTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="room@example.com", dni="12345678", birth_date="2000-01-01")
        self.client.force_authenticate(self.user)
        self.room = Room.objects.first()

    def test_active_rooms_and_distinct_live_players(self):
        Room.objects.create(name="oculta", is_active=False)
        for channel, seconds in (("a", 60), ("b", 60), ("old", -1)):
            RoomConnection.objects.create(room=self.room, user=self.user, channel_name=channel, expires_at=timezone.now()+timedelta(seconds=seconds))
        response = self.client.get("/api/v1/rooms")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 3)
        self.assertEqual(response.data[0]["players"], 1)

    def test_state_contains_wheel_and_chips(self):
        response = self.client.get(f"/api/v1/rooms/{self.room.pk}/state")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data["numbers"]), 37)
        self.assertEqual(response.data["numbers"][0], {"number": 0, "color": "verde"})
        self.assertEqual(response.data["chip_values"], [10, 50, 100, 500])

    def test_missing_room_and_authentication(self):
        self.assertEqual(self.client.get("/api/v1/rooms/99999/state").status_code, 404)
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get("/api/v1/rooms").status_code, 401)
