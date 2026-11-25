package com.tecsup.palacetec.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Historial en Excel, con la misma hoja que arma Django con openpyxl. */
public final class HistoryWorkbook {

    private static final int HEADER_ROW = 3;
    private static final int[] WIDTHS = {20, 22, 30, 12, 12, 12, 12, 18};
    /** POI mide el ancho en 1/256 de carácter. */
    private static final int WIDTH_UNIT = 256;
    private static final short TITLE_SIZE = 14;
    private static final int DATE = 0;
    private static final int GAME = 1;
    private static final int BET = 2;
    private static final int AMOUNT = 3;
    private static final int STATUS = 4;
    private static final int PAYOUT = 5;
    private static final int NET = 6;
    private static final int BALANCE = 7;

    private final Sheet sheet;
    private final CellStyle money;
    private final CellStyle bold;
    private final CellStyle boldMoney;
    private final CellStyle title;
    private final CellStyle header;

    private HistoryWorkbook(XSSFWorkbook book) {
        sheet = book.createSheet("Historial");
        Font boldFont = book.createFont();
        boldFont.setBold(true);
        Font titleFont = book.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints(TITLE_SIZE);
        Font headerFont = book.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        short format = book.createDataFormat().getFormat("#,##0.00");
        money = book.createCellStyle();
        money.setDataFormat(format);
        bold = book.createCellStyle();
        bold.setFont(boldFont);
        boldMoney = book.createCellStyle();
        boldMoney.setFont(boldFont);
        boldMoney.setDataFormat(format);
        title = book.createCellStyle();
        title.setFont(titleFont);
        XSSFCellStyle headerStyle = book.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(
                Integer.parseInt(HistoryReport.HEADER_COLOR, 16)), null));
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        header = headerStyle;
    }

    public static byte[] build(HistoryReport report) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            new HistoryWorkbook(book).fill(report);
            book.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void fill(HistoryReport report) {
        text(sheet.createRow(0), 0, HistoryReport.TITLE, title);
        Row info = sheet.createRow(1);
        text(info, 0, "Jugador: " + report.email(), null);
        text(info, 2, "Rango: " + report.rangeLabel(), null);
        text(info, 4, HistoryReport.NOTICE, null);
        Row names = sheet.createRow(HEADER_ROW);
        for (int column = 0; column < HistoryReport.COLUMNS.size(); column++) {
            text(names, column, HistoryReport.COLUMNS.get(column), header);
            sheet.setColumnWidth(column, WIDTHS[column] * WIDTH_UNIT);
        }
        int index = HEADER_ROW + 1;
        for (ExportRow bet : report.rows()) {
            fill(sheet.createRow(index++), bet);
        }
        Row totals = sheet.createRow(index + 1);
        text(totals, DATE, HistoryReport.TOTALS, bold);
        text(totals, GAME, report.countLabel(), bold);
        number(totals, AMOUNT, report.wagered(), boldMoney);
        number(totals, PAYOUT, report.paid(), boldMoney);
        number(totals, NET, report.net(), boldMoney);
        sheet.createFreezePane(0, HEADER_ROW + 1);
    }

    private void fill(Row row, ExportRow bet) {
        text(row, DATE, bet.date(), null);
        text(row, GAME, bet.game(), null);
        text(row, BET, bet.bet(), null);
        number(row, AMOUNT, bet.amount(), money);
        text(row, STATUS, bet.status().label(), null);
        number(row, PAYOUT, bet.payout(), money);
        number(row, NET, bet.net(), money);
        number(row, BALANCE, bet.balance(), money);
    }

    /** Siempre como texto: un valor que empieza con "=" no se convierte en fórmula. */
    private static void text(Row row, int column, String value, CellStyle style) {
        var cell = row.createCell(column);
        cell.setCellValue(HistoryReport.clean(value));
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void number(Row row, int column, BigDecimal value, CellStyle style) {
        var cell = row.createCell(column);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }
}
