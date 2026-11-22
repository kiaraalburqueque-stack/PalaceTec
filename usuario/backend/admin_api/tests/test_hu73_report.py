"""HU73: informe consolidado de negocio en Excel con 4 hojas y registro en la auditoría."""
import io
from datetime import datetime, time, timedelta
from decimal import Decimal
from uuid import uuid4

from django.utils import timezone
from openpyxl import load_workbook

from admin_api.kpis import LIMA
from admin_api.models import AuditLog
from casino.models import Play
from roulette.models import Bet, GameRound, Room
from .helpers import AdminTestBase

URL = "/api/v1/admin/reports/business.xlsx"


class HU73BusinessReportTests(AdminTestBase):
    def setUp(self):
        super().setUp()
        self.today = timezone.now().astimezone(LIMA).date()
        room = Room.objects.filter(game__slug="ruleta-europea").first()
        when = datetime.combine(self.today - timedelta(days=2), time(12), tzinfo=LIMA)
        round = GameRound.objects.create(room=room, opened_at=when, closes_at=when + timedelta(seconds=15),
                                         status="SETTLED", winning_number=3, settled_at=when + timedelta(seconds=20))
        bet = Bet.objects.create(user=self.player, round=round, kind="STRAIGHT_UP", selection="3", chips=[100],
                                 amount=Decimal("100"), payout=Decimal("3600"), reference=uuid4())
        Bet.objects.filter(pk=bet.pk).update(created_at=when)
        play = Play.objects.create(user=self.player, kind="SPORTS", amount=Decimal("50"), payout=Decimal("0"),
                                   settled_at=when, reference=uuid4())
        Play.objects.filter(pk=play.pk).update(created_at=when + timedelta(days=1))
        old = Play.objects.create(user=self.player, kind="BLACKJACK", amount=Decimal("500"), reference=uuid4())
        Play.objects.filter(pk=old.pk).update(created_at=when - timedelta(days=40))

    def download(self, **params):
        return self.client.get(URL, params)

    def workbook(self, response):
        return load_workbook(io.BytesIO(response.content))

    def test_hu73_workbook_has_four_sheets(self):
        response = self.download(startDate=(self.today - timedelta(days=6)).isoformat(), endDate=self.today.isoformat())
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response["Content-Type"], "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        self.assertIn(f"informe_negocio_{self.today - timedelta(days=6)}_{self.today}.xlsx", response["Content-Disposition"])
        self.assertEqual(self.workbook(response).sheetnames, ["KPIs", "Flujo diario", "Ranking de juegos", "Retención"])

    def test_hu73_kpi_totals_match_the_other_sheets(self):
        book = self.workbook(self.download(startDate=(self.today - timedelta(days=6)).isoformat()))
        kpis = {row[0]: row[1] for row in book["KPIs"].iter_rows(min_row=5, values_only=True) if row[0]}
        self.assertEqual((kpis["Créditos apostados"], kpis["Créditos pagados"], kpis["Margen neto"]), (150, 3600, -3450))
        daily = list(book["Flujo diario"].iter_rows(min_row=2, values_only=True))
        self.assertEqual(len(daily), 8)  # 7 días + fila de total
        self.assertEqual(daily[-1], ("Total", 150, 3600, -3450))
        self.assertEqual(sum(row[1] for row in daily[:-1]), kpis["Créditos apostados"])
        ranking = {row[1]: row for row in book["Ranking de juegos"].iter_rows(min_row=2, values_only=True)}
        self.assertEqual(sum(row[6] for row in ranking.values()), kpis["Créditos apostados"])
        self.assertEqual(ranking["Ruleta Europea"][4:7], (1, 1, 100))
        self.assertEqual(ranking["Blackjack 21"][6], 0)  # la mano de hace 40 días queda fuera del rango
        self.assertEqual(kpis["Rondas jugadas"], 2)
        self.assertEqual(kpis["Jugadores activos en el rango (MAU)"], 1)
        retention = list(book["Retención"].iter_rows(min_row=2, values_only=True))
        self.assertEqual(sum(row[1] for row in retention[:7]), 2)
        self.assertIn(("MAU (activos distintos del rango)", 1), retention)

    def test_hu73_default_range_is_last_30_days(self):
        book = self.workbook(self.download())
        self.assertEqual(book["Flujo diario"].max_row, 32)  # encabezado + 30 días + total

    def test_hu73_download_is_recorded_in_audit_log(self):
        start = (self.today - timedelta(days=6)).isoformat()
        self.download(startDate=start, endDate=self.today.isoformat())
        log = AuditLog.objects.get(action="GET admin-business-report")
        self.assertEqual((log.admin, log.method, log.status_code), (self.admin, "GET", 200))
        self.assertEqual(log.details, {"startDate": start, "endDate": self.today.isoformat()})

    def test_hu73_only_admins_and_valid_ranges(self):
        self.assertEqual(self.download(startDate="2026-11-10", endDate="2026-11-01").status_code, 400)
        self.assertEqual(self.download(startDate="2024-01-01", endDate="2026-01-01").status_code, 400)
        self.assertEqual(self.client.get(URL, HTTP_ACCEPT="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").status_code, 200)
        self.as_player()
        self.assertEqual(self.download().status_code, 403)
        self.assertFalse(AuditLog.objects.filter(admin=self.player).exists())
