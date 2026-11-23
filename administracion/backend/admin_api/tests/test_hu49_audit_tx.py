import csv
import io
from uuid import uuid4

from django.contrib.auth.models import Group

from responsible.models import DailyLimit
from roulette.models import Room
from roulette.services import RouletteService
from wallet.models import WalletTransaction
from wallet.services import WalletService
from .helpers import AdminTestBase, make_user

URL = "/api/v1/admin/audit/transactions"


class HU49TransactionAuditTests(AdminTestBase):
    def setUp(self):
        super().setUp()
        DailyLimit.objects.create(user=self.player, amount=10000)
        room = Room.objects.first()
        round = RouletteService.tick(room.pk)
        self.as_player()
        bet = self.client.post(f"/api/v1/rooms/{room.pk}/bets", {"round_id": round.pk, "kind": "COLOR", "selection": "negro",
                               "chips": [100], "reference": str(uuid4())}, format="json")
        self.bet_id = bet.data["id"]
        self.other = make_user("otro@palacetec.pe", "80000000")
        WalletService.credit(self.other, "25.00", "manual:test")
        self.client.force_authenticate(self.admin)

    def test_hu49_search_by_user_and_transaction(self):
        data = self.client.get(f"{URL}?user_id={self.player.pk}").data
        self.assertEqual(data["count"], 1)
        row = data["results"][0]
        self.assertEqual((row["type"], row["amount"], row["email"]), ("BET", "100.00", self.player.email))
        self.assertEqual(row["bet"]["bet_id"], self.bet_id)
        tx = WalletTransaction.objects.get(wallet__user=self.other)
        self.assertEqual([r["id"] for r in self.client.get(f"{URL}?transaction_id={tx.pk}").data["results"]], [tx.pk])
        self.assertEqual(self.client.get(f"{URL}?search=otro@").data["count"], 1)

    def test_hu49_filters_by_event_type(self):
        self.assertEqual(self.client.get(f"{URL}?type=WIN").data["count"], 1)
        self.assertEqual(self.client.get(f"{URL}?type=BET").data["count"], 1)
        self.assertEqual(self.client.get(f"{URL}?type=OTRO").status_code, 400)

    def test_hu49_exports_filtered_csv(self):
        response = self.client.get(f"{URL}/export?type=BET")
        self.assertEqual(response.status_code, 200)
        self.assertIn("text/csv", response["Content-Type"])
        rows = list(csv.reader(io.StringIO(response.content.decode("utf-8-sig"))))
        self.assertEqual(rows[0][0], "id")
        self.assertEqual(len(rows), 2)
        self.assertEqual(rows[1][4], "BET")
        self.assertEqual(rows[1][8], str(self.bet_id))

    def test_hu49_common_user_gets_403_and_auditor_is_allowed(self):
        self.as_player()
        self.assertEqual(self.client.get(URL).status_code, 403)
        self.assertEqual(self.client.get(URL + "/export").status_code, 403)
        auditor = make_user("auditor@palacetec.pe", "90000000")
        auditor.groups.add(Group.objects.get(name="Auditor"))
        self.client.force_authenticate(auditor)
        self.assertEqual(self.client.get(URL).status_code, 200)
