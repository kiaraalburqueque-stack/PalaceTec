package com.tecsup.palacetec.export;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/** Historial en PDF (A4 apaisado), con el diseño que arma Django con ReportLab. */
public final class HistoryPdf {

    private static final float CM = 72 / 2.54f;
    private static final float MARGIN = 1.2f * CM;
    private static final float[] WIDTHS = {3.6f, 3.8f, 5.6f, 2.4f, 2.4f, 2.6f, 2.4f, 3f};
    private static final Color HEADER = new Color(Integer.parseInt(HistoryReport.HEADER_COLOR, 16));
    private static final Color STRIPE = new Color(0xF3F4F6);
    private static final Color GRID = new Color(0xD1D5DB);
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Font CELL_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
    private static final Font CELL_HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final float ROW_HEIGHT = 18;
    private static final float SIDE_PADDING = 6;
    private static final float GRID_WIDTH = 0.25f;
    private static final float TOTALS_LINE = 0.8f;
    /** Desde esta columna los valores van a la derecha. */
    private static final int FIRST_RIGHT = 3;
    private static final int MAX_BET_TEXT = 40;

    private HistoryPdf() {
    }

    public static byte[] build(HistoryReport report) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Document document = new Document(PageSize.A4.rotate(), MARGIN, MARGIN, MARGIN, MARGIN)) {
            PdfWriter.getInstance(document, output);
            document.addTitle("Historial de apuestas PalaceTec");
            document.open();
            Paragraph title = new Paragraph(HistoryReport.TITLE, TITLE);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(6);
            document.add(title);
            Paragraph info = new Paragraph(HistoryReport.clean("Jugador: " + report.email() + " · Rango: "
                    + report.rangeLabel() + " · " + HistoryReport.NOTICE), NORMAL);
            info.setSpacingAfter(0.4f * CM);
            document.add(info);
            document.add(table(report));
            if (report.rows().isEmpty()) {
                document.add(new Paragraph("No hay apuestas en este período.", NORMAL));
            }
        }
        return output.toByteArray();
    }

    private static PdfPTable table(HistoryReport report) {
        PdfPTable table = new PdfPTable(WIDTHS.length);
        float[] widths = new float[WIDTHS.length];
        for (int column = 0; column < WIDTHS.length; column++) {
            widths[column] = WIDTHS[column] * CM;
        }
        table.setTotalWidth(widths);
        table.setLockedWidth(true);
        table.setHeaderRows(1);
        HistoryReport.COLUMNS.forEach(name -> table.addCell(grid(name, CELL_HEADER, HEADER, Element.ALIGN_LEFT)));
        boolean striped = false;
        for (ExportRow row : report.rows()) {
            List<String> values = List.of(row.date(), row.game(), shorten(row.bet()), money(row.amount()),
                    row.status().label(), money(row.payout()), money(row.net()), money(row.balance()));
            for (int column = 0; column < values.size(); column++) {
                table.addCell(grid(values.get(column), CELL, striped ? STRIPE : Color.WHITE, alignment(column)));
            }
            striped = !striped;
        }
        List<String> totals = List.of(HistoryReport.TOTALS, report.countLabel(), "", money(report.wagered()), "",
                money(report.paid()), money(report.net()), "");
        for (int column = 0; column < totals.size(); column++) {
            PdfPCell cell = cell(totals.get(column), CELL_BOLD, alignment(column));
            cell.setBorder(Rectangle.TOP);
            cell.setBorderWidthTop(TOTALS_LINE);
            table.addCell(cell);
        }
        return table;
    }

    private static PdfPCell grid(String text, Font font, Color background, int alignment) {
        PdfPCell cell = cell(text, font, alignment);
        cell.setBackgroundColor(background);
        cell.setBorderColor(GRID);
        cell.setBorderWidth(GRID_WIDTH);
        return cell;
    }

    private static PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(HistoryReport.clean(text), font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setFixedHeight(ROW_HEIGHT);
        cell.setPaddingLeft(SIDE_PADDING);
        cell.setPaddingRight(SIDE_PADDING);
        cell.setNoWrap(true);
        return cell;
    }

    private static int alignment(int column) {
        return column >= FIRST_RIGHT ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
    }

    private static String shorten(String text) {
        return text.codePointCount(0, text.length()) <= MAX_BET_TEXT ? text
                : text.substring(0, text.offsetByCodePoints(0, MAX_BET_TEXT));
    }

    /** Miles con coma y dos decimales; una raya si no hay valor. */
    static String money(BigDecimal value) {
        return value == null ? "—"
                : new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(value);
    }
}
