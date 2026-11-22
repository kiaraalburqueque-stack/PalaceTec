"""HU66: exportación del historial propio en Excel y PDF respetando el filtro de fechas."""
import io
from datetime import datetime, timedelta
from decimal import Decimal
from uuid import uuid4
from zoneinfo import ZoneInfo

from openpyxl import load_workbook
from rest_framework.test import APITestCase

from accounts.models import User
from casino.models import Play
from wallet.models import Wallet, WalletTransaction
from .history_export import COLUMNS
from .models import Bet, GameRound, Room

URL = "/api/v1/users/bets-history/export"
LIMA = ZoneInfo("America/Lima")


class HU66ExportTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="hu66@palacetec.pe", dni="66666666", birth_date="2000-01-01")
        self.other = User.objects.create_user(email="otro66@palacetec.pe", dni="66666667", birth_date="2000-01-01")
        self.wallet = Wallet.objects.create(user=self.user, balance=1000)
        self.room = Room.objects.filter(game__slug="ruleta-europea").first()
        self.client.force_authenticate(self.user)
        self.inside = datetime(2026, 11, 10, 12, 0, tzinfo=LIMA)
        self.outside = datetime(2026, 11, 1, 12, 0, tzinfo=LIMA)
        self.win = self.bet(self.user, self.inside, "50.00", "100.00", balance="950.00")
        self.pending = self.bet(self.user, self.inside + timedelta(hours=1), "10.00", None)
        self.old = self.bet(self.user, self.outside, "20.00", "0.00")
        self.foreign = self.bet(self.other, self.inside, "500.00", "0.00")
        play = Play.objects.create(user=self.user, kind="BLACKJACK", amount=Decimal("30.00"), payout=Decimal("30.00"),
                                   settled_at=self.inside, reference=uuid4(), description="Mano de blackjack")
        Play.objects.filter(pk=play.pk).update(created_at=self.inside + timedelta(hours=2))

    def bet(self, user, when, amount, payout, balance=None):
        round = GameRound.objects.create(room=self.room, opened_at=when, closes_at=when + timedelta(seconds=15),
            status="SETTLED" if payout is not None else "CLOSED", winning_number=7 if payout is not None else None,
            settled_at=when + timedelta(seconds=20) if payout is not None else None)
        bet = Bet.objects.create(user=user, round=round, kind="STRAIGHT_UP", selection="7", chips=[10], amount=Decimal(amount),
                                 payout=None if payout is None else Decimal(payout), reference=uuid4())
        Bet.objects.filter(pk=bet.pk).update(created_at=when)
        if balance:
            WalletTransaction.objects.create(wallet=self.wallet, type="BET", amount=Decimal(amount),
                                             balance_after=Decimal(balance), reference=f"roulette:bet:{bet.pk}")
        return bet

    def export(self, fmt, **params):
        return self.client.get(URL, {"format": fmt, **params})

    def sheet_rows(self, response):
        sheet = load_workbook(io.BytesIO(response.content)).active
        rows = [list(row) for row in sheet.iter_rows(values_only=True)]
        header = rows.index(COLUMNS)
        body = [row for row in rows[header + 1:] if row and row[0] and row[0] != "Totales"]
        return body, next(row for row in rows if row and row[0] == "Totales")

    def test_hu66_excel_has_columns_and_rows_of_the_range(self):
        response = self.export("xlsx", startDate="2026-11-10", endDate="2026-11-10")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response["Content-Type"], "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        self.assertIn('filename="historial_palacetec_2026-11-10_2026-11-10.xlsx"', response["Content-Disposition"])
        body, total = self.sheet_rows(response)
        self.assertEqual([row[1] for row in body], ["Ruleta Europea", "Ruleta Europea", "Blackjack"])
        first = body[0]
        self.assertEqual((first[0], first[2], first[3], first[4], first[5], first[6], first[7]),
                         ("2026-11-10 12:00:00", "Pleno: 7", 50, "Ganada", 100, 50, 950))
        self.assertEqual((body[1][4], body[1][5]), ("Pendiente", None))
        self.assertEqual((body[2][4], body[2][2]), ("Empate", "Mano de blackjack"))
        self.assertEqual((total[1], total[3], total[5], total[6]), ("3 apuestas", 90, 130, 50))

    def test_hu66_without_dates_exports_everything_of_the_player(self):
        body, _ = self.sheet_rows(self.export("xlsx"))
        self.assertEqual(len(body), 4)
        self.assertEqual(body[0][0], "2026-11-01 12:00:00")

    def test_hu66_pdf_is_generated_with_name_by_dates(self):
        response = self.export("pdf", startDate="2026-11-01")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response["Content-Type"], "application/pdf")
        self.assertTrue(response.content.startswith(b"%PDF"))
        self.assertIn('historial_palacetec_2026-11-01_hoy.pdf', response["Content-Disposition"])

    def test_hu66_player_cannot_export_history_of_another_user(self):
        body, _ = self.sheet_rows(self.export("xlsx", user_id=self.other.pk, user=self.other.pk))
        self.assertNotIn(500, [row[3] for row in body])
        self.client.force_authenticate(self.other)
        body, _ = self.sheet_rows(self.export("xlsx"))
        self.assertEqual([row[3] for row in body], [500])

    def test_hu66_invalid_format_or_dates_return_400(self):
        self.assertEqual(self.export("csv").status_code, 400)
        self.assertEqual(self.client.get(URL).status_code, 400)
        self.assertEqual(self.export("pdf", startDate="2026-11-10", endDate="2026-11-01").status_code, 400)
        self.assertEqual(self.export("xlsx", startDate="10/11/2026").status_code, 400)

    def test_hu66_requires_authentication(self):
        self.client.force_authenticate(None)
        self.assertEqual(self.export("pdf").status_code, 401)
