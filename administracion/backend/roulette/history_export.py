"""HU66: exportación del historial propio en Excel (openpyxl) y PDF (ReportLab).

Reutiliza el filtro de fechas de la HU32 y solo incluye apuestas del jugador autenticado.
"""
import io
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from zoneinfo import ZoneInfo

from casino.models import Play
from wallet.models import WalletTransaction
from .models import Bet
from .queries import DateRangeInput, filter_dates

LIMA = ZoneInfo("America/Lima")
MAX_ROWS = 5000
COLUMNS = ["Fecha (Lima)", "Juego", "Apuesta", "Monto", "Resultado", "Pago total", "Neto", "Saldo tras apostar"]
STATUS = {"PENDING": "Pendiente", "WON": "Ganada", "LOST": "Perdida", "PUSH": "Empate"}


@dataclass
class ExportRow:
    created_at: datetime
    game: str
    bet: str
    amount: Decimal
    status: str
    payout: Decimal | None
    balance: Decimal | None

    @property
    def net(self):
        return None if self.payout is None else self.payout - self.amount

    def values(self):
        return [self.created_at.astimezone(LIMA).strftime("%Y-%m-%d %H:%M:%S"), self.game, self.bet, self.amount,
                STATUS[self.status], self.payout, self.net, self.balance]


def date_range(params):
    data = DateRangeInput(data=params)
    data.is_valid(raise_exception=True)
    return data.validated_data.get("startDate"), data.validated_data.get("endDate")


def export_rows(user, params):
    """Apuestas propias de ruleta y casino del rango, de la más antigua a la más reciente."""
    bets = list(filter_dates(Bet.objects.filter(user=user), params).select_related("round__room__game")
                .order_by("created_at", "id")[:MAX_ROWS])
    plays = list(filter_dates(Play.objects.filter(user=user), params).order_by("created_at", "id")[:MAX_ROWS])
    references = [f"roulette:bet:{bet.pk}" for bet in bets] + [f"casino:bet:{play.pk}" for play in plays]
    balances = dict(WalletTransaction.objects.filter(wallet__user=user, reference__in=references)
                    .values_list("reference", "balance_after"))
    rows = []
    for bet in bets:
        game = bet.round.room.game.title if bet.round.room.game_id else "Ruleta Europea"
        status = "PENDING" if bet.payout is None else "WON" if bet.payout > 0 else "LOST"
        rows.append(ExportRow(bet.created_at, game, f"{bet.get_kind_display()}: {bet.selection}", bet.amount, status,
                              bet.payout, balances.get(f"roulette:bet:{bet.pk}")))
    for play in plays:
        status = ("PENDING" if play.payout is None else "PUSH" if play.payout == play.amount
                  else "WON" if play.payout > play.amount else "LOST")
        rows.append(ExportRow(play.created_at, play.get_kind_display(), play.description or play.get_kind_display(),
                              play.amount, status, play.payout, balances.get(f"casino:bet:{play.pk}")))
    rows.sort(key=lambda row: row.created_at)
    return rows[:MAX_ROWS]


def totals(rows):
    settled = [row for row in rows if row.payout is not None]
    wagered = sum((row.amount for row in rows), Decimal("0"))
    paid = sum((row.payout for row in settled), Decimal("0"))
    settled_wagered = sum((row.amount for row in settled), Decimal("0"))
    return {"count": len(rows), "wagered": wagered, "paid": paid, "net": paid - settled_wagered}


def range_label(start, end):
    return f"{start.isoformat() if start else 'inicio'} a {end.isoformat() if end else 'hoy'}"


def file_name(start, end, extension):
    return f"historial_palacetec_{start.isoformat() if start else 'inicio'}_{end.isoformat() if end else 'hoy'}.{extension}"


def build_xlsx(user, rows, start, end):
    from openpyxl import Workbook
    from openpyxl.styles import Alignment, Font, PatternFill

    book = Workbook()
    sheet = book.active
    sheet.title = "Historial"
    sheet.append(["Historial de apuestas · PalaceTec"])
    sheet["A1"].font = Font(bold=True, size=14)
    sheet.append([f"Jugador: {user.email}", "", f"Rango: {range_label(start, end)}", "", "Créditos virtuales, sin dinero real"])
    sheet.append([])
    sheet.append(COLUMNS)
    header = sheet.max_row
    for cell in sheet[header]:
        cell.font = Font(bold=True, color="FFFFFF")
        cell.fill = PatternFill("solid", fgColor="1F2937")
        cell.alignment = Alignment(horizontal="center")
    for row in rows:
        sheet.append(row.values())
    summary = totals(rows)
    sheet.append([])
    sheet.append(["Totales", f"{summary['count']} apuestas", "", summary["wagered"], "", summary["paid"], summary["net"], ""])
    for cell in sheet[sheet.max_row]:
        cell.font = Font(bold=True)
    for row in sheet.iter_rows(min_row=header + 1, min_col=4, max_col=8):
        for cell in row:
            cell.number_format = "#,##0.00"
    for letter, width in zip("ABCDEFGH", (20, 22, 30, 12, 12, 12, 12, 18)):
        sheet.column_dimensions[letter].width = width
    sheet.freeze_panes = sheet.cell(row=header + 1, column=1)
    buffer = io.BytesIO()
    book.save(buffer)
    return buffer.getvalue()


def money(value):
    return "—" if value is None else f"{value:,.2f}"


def build_pdf(user, rows, start, end):
    from reportlab.lib import colors
    from reportlab.lib.pagesizes import A4, landscape
    from reportlab.lib.styles import getSampleStyleSheet
    from reportlab.lib.units import cm
    from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

    buffer = io.BytesIO()
    document = SimpleDocTemplate(buffer, pagesize=landscape(A4), leftMargin=1.2 * cm, rightMargin=1.2 * cm,
                                 topMargin=1.2 * cm, bottomMargin=1.2 * cm, title="Historial de apuestas PalaceTec")
    styles = getSampleStyleSheet()
    summary = totals(rows)
    story = [Paragraph("Historial de apuestas · PalaceTec", styles["Title"]),
             Paragraph(f"Jugador: {user.email} · Rango: {range_label(start, end)} · Créditos virtuales, sin dinero real",
                       styles["Normal"]),
             Spacer(1, 0.4 * cm)]
    data = [COLUMNS] + [[r.values()[0], r.game, r.bet[:40], money(r.amount), STATUS[r.status], money(r.payout),
                         money(r.net), money(r.balance)] for r in rows]
    data.append(["Totales", f"{summary['count']} apuestas", "", money(summary["wagered"]), "", money(summary["paid"]),
                 money(summary["net"]), ""])
    table = Table(data, repeatRows=1, colWidths=[3.6 * cm, 3.8 * cm, 5.6 * cm, 2.4 * cm, 2.4 * cm, 2.6 * cm, 2.4 * cm, 3 * cm])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1F2937")), ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"), ("FONTNAME", (0, -1), (-1, -1), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, -1), 8), ("ALIGN", (3, 1), (-1, -1), "RIGHT"),
        ("ROWBACKGROUNDS", (0, 1), (-1, -2), [colors.white, colors.HexColor("#F3F4F6")]),
        ("LINEABOVE", (0, -1), (-1, -1), 0.8, colors.black), ("GRID", (0, 0), (-1, -2), 0.25, colors.HexColor("#D1D5DB")),
    ]))
    story.append(table)
    if not rows:
        story.append(Paragraph("No hay apuestas en este período.", styles["Normal"]))
    document.build(story)
    return buffer.getvalue()
