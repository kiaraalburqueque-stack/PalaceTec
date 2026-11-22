"""HU73: informe consolidado de negocio en Excel para un rango de fechas.

Hojas: KPIs, Flujo diario (HU48), Ranking de juegos (HU71) y Retención (HU72). Los totales de la hoja KPIs
salen de las mismas series que el resto de hojas, así que siempre coinciden. Cada descarga queda en la auditoría (HU50).
"""
import io
from datetime import timedelta
from decimal import Decimal

from django.http import HttpResponse
from django.utils import timezone
from rest_framework import serializers

from .base import AdminView, client_ip
from .kpis import LIMA, day_bounds
from .reports import financial_flow_range, games_ranking, retention

MAX_DAYS = 366
DEFAULT_DAYS = 30
XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"


class ReportQuery(serializers.Serializer):
    startDate = serializers.DateField(required=False, input_formats=["%Y-%m-%d"])
    endDate = serializers.DateField(required=False, input_formats=["%Y-%m-%d"])

    def validate(self, attrs):
        today = timezone.now().astimezone(LIMA).date()
        end = attrs.get("endDate") or today
        start = attrs.get("startDate") or end - timedelta(days=DEFAULT_DAYS - 1)
        if start > end:
            raise serializers.ValidationError({"endDate": "La fecha final debe ser igual o posterior al inicio."})
        if (end - start).days + 1 > MAX_DAYS:
            raise serializers.ValidationError({"startDate": f"El rango no puede superar {MAX_DAYS} días."})
        return {"startDate": start, "endDate": end}


def report_data(first, last):
    start, end = day_bounds(first)[0], day_bounds(last)[1]
    flow = financial_flow_range(first, last)
    ranking = games_ranking(start, end)
    activity = retention(first, last)
    return flow, ranking, activity


def build_workbook(first, last, admin_email, now=None):
    from openpyxl import Workbook
    from openpyxl.styles import Font, PatternFill

    now = now or timezone.now()
    flow, ranking, activity = report_data(first, last)
    book = Workbook()
    bold = Font(bold=True)
    header_font, header_fill = Font(bold=True, color="FFFFFF"), PatternFill("solid", fgColor="1F2937")

    def header(sheet, titles):
        sheet.append(titles)
        for cell in sheet[sheet.max_row]:
            cell.font, cell.fill = header_font, header_fill

    def money_format(sheet, columns, first_row):
        for row in sheet.iter_rows(min_row=first_row):
            for cell in row:
                if cell.column_letter in columns and isinstance(cell.value, (int, float, Decimal)):
                    cell.number_format = "#,##0.00"

    kpis = book.active
    kpis.title = "KPIs"
    kpis.append(["Informe de negocio · PalaceTec"])
    kpis["A1"].font = Font(bold=True, size=14)
    kpis.append([f"Rango: {first.isoformat()} a {last.isoformat()} (hora de Lima) · Créditos virtuales, sin dinero real"])
    kpis.append([])
    header(kpis, ["Indicador", "Valor"])
    totals = flow["totals"]
    rows = [
        ("Créditos apostados", Decimal(totals["wagered"])), ("Créditos pagados", Decimal(totals["paid"])),
        ("Margen neto", Decimal(totals["net_margin"])), ("Rondas jugadas", ranking["totals"]["rounds"]),
        ("Jugadores activos en el rango (MAU)", activity["mau"]), ("DAU promedio", activity["average_dau"]),
        ("Índice DAU/MAU (%)", activity["dau_mau_ratio"]),
        ("Juegos con actividad", sum(1 for game in ranking["games"] if game["rounds"])),
        ("Generado", now.astimezone(LIMA).strftime("%Y-%m-%d %H:%M")), ("Generado por", admin_email),
    ]
    for row in rows:
        kpis.append(list(row))
    for row in kpis.iter_rows(min_row=5, max_row=7, min_col=2, max_col=2):
        row[0].number_format = "#,##0.00"
    kpis.column_dimensions["A"].width, kpis.column_dimensions["B"].width = 38, 22

    daily = book.create_sheet("Flujo diario")
    header(daily, ["Fecha", "Apostado", "Pagado", "Margen neto"])
    for day in flow["days"]:
        daily.append([day["date"], Decimal(day["wagered"]), Decimal(day["paid"]), Decimal(day["net_margin"])])
    daily.append(["Total", Decimal(totals["wagered"]), Decimal(totals["paid"]), Decimal(totals["net_margin"])])
    for cell in daily[daily.max_row]:
        cell.font = bold
    money_format(daily, "BCD", 2)

    games = book.create_sheet("Ranking de juegos")
    header(games, ["#", "Juego", "Categoría", "Activo", "Rondas", "Jugadores únicos", "Créditos apostados", "Participación (%)"])
    for index, game in enumerate(ranking["games"], start=1):
        games.append([index, game["title"], game["category"], "Sí" if game["active"] else "No", game["rounds"],
                      game["unique_players"], Decimal(game["wagered"]), game["share_pct"]])
    money_format(games, "G", 2)

    retention_sheet = book.create_sheet("Retención")
    header(retention_sheet, ["Fecha", "Usuarios activos (DAU)"])
    for day in activity["days"]:
        retention_sheet.append([day["date"], day["dau"]])
    retention_sheet.append([])
    for label, value in (("MAU (activos distintos del rango)", activity["mau"]), ("DAU promedio", activity["average_dau"]),
                         ("Índice DAU/MAU (%)", activity["dau_mau_ratio"])):
        retention_sheet.append([label, value])
        retention_sheet[retention_sheet.max_row][0].font = bold

    for sheet in (daily, games, retention_sheet):
        for letter in "ABCDEFGH":
            sheet.column_dimensions[letter].width = 34 if letter == "A" and sheet is retention_sheet else 18
        sheet.freeze_panes = "A2"
    buffer = io.BytesIO()
    book.save(buffer)
    return buffer.getvalue()


class BusinessReportView(AdminView):
    """GET /api/v1/admin/reports/business.xlsx?startDate=&endDate= (por defecto, los últimos 30 días)."""

    def perform_content_negotiation(self, request, force=False):
        # La respuesta es un archivo; los errores se devuelven en JSON aunque el cliente pida .xlsx.
        renderer = self.get_renderers()[0]
        return renderer, renderer.media_type

    def get(self, request):
        from .models import AuditLog
        query = ReportQuery(data=request.query_params)
        query.is_valid(raise_exception=True)
        first, last = query.validated_data["startDate"], query.validated_data["endDate"]
        content = build_workbook(first, last, request.user.email)
        # HU50: la auditoría automática solo cubre métodos de escritura; esta descarga se registra aquí.
        AuditLog.objects.create(admin=request.user, action="GET admin-business-report", method="GET",
                                resource=request.path[:255], status_code=200, ip_address=client_ip(request),
                                details={"startDate": first.isoformat(), "endDate": last.isoformat()})
        response = HttpResponse(content, content_type=XLSX)
        response["Content-Disposition"] = f'attachment; filename="informe_negocio_{first.isoformat()}_{last.isoformat()}.xlsx"'
        return response
