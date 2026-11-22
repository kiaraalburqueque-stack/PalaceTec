from accounts.models import User
from .models import Bet
from .services import RouletteService
from .test_game import GameTestBase


class HU54BetDetailTests(GameTestBase):
    def test_hu54_owner_sees_full_detail_with_chips(self):
        bet_id = self.client.post(self.url, self.data(kind="COLOR", selection="rojo", chips=[10, 50, 50]), format="json").data["id"]
        response = self.client.get(f"/api/v1/users/bets/{bet_id}")
        self.assertEqual(response.status_code, 200)
        self.assertEqual((response.data["kind"], response.data["selection"], response.data["chips"]), ("COLOR", "rojo", [10, 50, 50]))
        self.assertEqual(response.data["status"], "PENDING")
        self.assertEqual(response.data["round"]["id"], self.round.pk)
        self.assertIsNone(response.data["round"]["winning_number"])

    def test_hu54_settled_detail_shows_winning_number(self):
        bet_id = self.client.post(self.url, self.data(), format="json").data["id"]
        self.round.status = "CLOSED"
        self.round.save(update_fields=["status"])
        RouletteService.tick(self.room.pk)
        data = self.client.get(f"/api/v1/users/bets/{bet_id}").data
        self.assertIn(data["status"], ("WON", "LOST"))
        self.assertIsNotNone(data["round"]["winning_number"])
        self.assertIn(data["round"]["winning_color"], ("rojo", "negro", "verde"))

    def test_hu54_other_users_cannot_see_it(self):
        bet_id = self.client.post(self.url, self.data(), format="json").data["id"]
        other = User.objects.create_user(email="curioso@example.com", dni="87654321", birth_date="2000-01-01")
        self.client.force_authenticate(other)
        self.assertEqual(self.client.get(f"/api/v1/users/bets/{bet_id}").status_code, 404)
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get(f"/api/v1/users/bets/{bet_id}").status_code, 401)
        self.assertTrue(Bet.objects.filter(pk=bet_id).exists())
