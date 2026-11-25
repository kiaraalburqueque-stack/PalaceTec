package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.tecsup.palacetec.ApiTest;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.ResultActions;

/** HU66 de Django: exportación del historial propio en Excel y PDF. */
class HistoryExportTest extends ApiTest {

    private static final String PATH = "/api/v1/users/bets-history/export";
    private static final String BAD_DATE = "Date has wrong format. Use one of these formats instead: YYYY-MM-DD.";
    private static final int FIRST_ROW = 4;

    private Player player;
    private long game;
    private long round;

    @BeforeEach
    void history() {
        player = newPlayer();
        game = newGame("exportar");
        round = newSettledRound(newRoom(game, 10, 500), 7);
        long won = newBet(player, round, "50", "100", "2026-11-10T12:00:00-05:00");
        newBet(player, round, "10", null, "2026-11-10T13:00:00-05:00");
        newPlay(player, "BLACKJACK", "30", "30", "2026-11-10T14:00:00-05:00");
        newBet(player, round, "20", "0", "2026-11-01T12:00:00-05:00");
        newBet(newPlayer(), round, "500", "0", "2026-11-10T12:00:00-05:00");
        jdbc.update("insert into wallet_transactions (wallet_id, type, amount, balance_after, reference, created_at) "
                + "select id, 'BET', 50, 950, ?, now() from wallets where user_id = ?", "roulette:bet:" + won, player.id());
    }

    private ResultActions export(String query) throws Exception {
        return mvc.perform(get(PATH + query).with(player.bearer()));
    }

    private byte[] file(String query) throws Exception {
        return export(query).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private static XSSFSheet sheet(byte[] file) throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            return book.getSheetAt(0);
        }
    }

    private static List<Object> values(XSSFRow row) {
        List<Object> values = new ArrayList<>();
        for (int column = 0; column < 8; column++) {
            XSSFCell cell = row.getCell(column);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                values.add("");
            } else {
                values.add(cell.getCellType() == CellType.NUMERIC ? cell.getNumericCellValue()
                        : cell.getStringCellValue());
            }
        }
        return values;
    }

    private static String text(byte[] pdf) throws Exception {
        try (PdfReader reader = new PdfReader(pdf)) {
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(new PdfTextExtractor(reader).getTextFromPage(page)).append('\n');
            }
            return text.toString();
        }
    }

    @Test
    void excelHasTheColumnsRowsAndTotalsOfTheRange() throws Exception {
        XSSFSheet sheet = sheet(export("?format=xlsx&startDate=2026-11-10&endDate=2026-11-10")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"historial_palacetec_2026-11-10_2026-11-10.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(sheet.getSheetName()).isEqualTo("Historial");
        assertThat(values(sheet.getRow(0))).startsWith("Historial de apuestas · PalaceTec");
        assertThat(values(sheet.getRow(1))).startsWith("Jugador: " + player.email(), "",
                "Rango: 2026-11-10 a 2026-11-10", "", "Créditos virtuales, sin dinero real");
        assertThat((Object) sheet.getRow(2)).isNull();
        assertThat(values(sheet.getRow(3))).containsExactly("Fecha (Lima)", "Juego", "Apuesta", "Monto", "Resultado",
                "Pago total", "Neto", "Saldo tras apostar");
        assertThat(values(sheet.getRow(FIRST_ROW))).containsExactly("2026-11-10 12:00:00", "Juego exportar",
                "Color: rojo", 50.0, "Ganada", 100.0, 50.0, 950.0);
        assertThat(values(sheet.getRow(FIRST_ROW + 1))).containsExactly("2026-11-10 13:00:00", "Juego exportar",
                "Color: rojo", 10.0, "Pendiente", "", "", "");
        assertThat(values(sheet.getRow(FIRST_ROW + 2))).containsExactly("2026-11-10 14:00:00", "Blackjack", "Jugada",
                30.0, "Empate", 30.0, 0.0, "");
        assertThat((Object) sheet.getRow(FIRST_ROW + 3)).isNull();
        assertThat(values(sheet.getRow(FIRST_ROW + 4))).containsExactly("Totales", "3 apuestas", "", 90.0, "", 130.0,
                50.0, "");
    }

    @Test
    void excelKeepsTheFormatOfDjango() throws Exception {
        XSSFSheet sheet = sheet(file("?format=xlsx"));
        XSSFCell header = sheet.getRow(3).getCell(0);
        assertThat(header.getCellStyle().getFont().getBold()).isTrue();
        assertThat(header.getCellStyle().getFillForegroundColorColor().getARGBHex()).isEqualTo("FF1F2937");
        assertThat(sheet.getRow(FIRST_ROW).getCell(3).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
        assertThat(sheet.getRow(sheet.getLastRowNum()).getCell(0).getCellStyle().getFont().getBold()).isTrue();
        assertThat(sheet.getColumnWidth(2)).isEqualTo(30 * 256);
        assertThat(sheet.getPaneInformation().getHorizontalSplitTopRow()).isEqualTo((short) FIRST_ROW);
    }

    @Test
    void withoutDatesEverythingOfThePlayerIsExportedFromOldestToNewest() throws Exception {
        XSSFSheet sheet = sheet(file("?format=xlsx&startDate=&endDate=&user_id=1"));
        assertThat(values(sheet.getRow(1)).get(2)).isEqualTo("Rango: inicio a hoy");
        assertThat(values(sheet.getRow(FIRST_ROW)).getFirst()).isEqualTo("2026-11-01 12:00:00");
        assertThat(values(sheet.getRow(FIRST_ROW + 5))).startsWith("Totales", "4 apuestas", "", 110.0);
    }

    @Test
    void pdfIsNamedByItsDatesAndShowsTheSameRows() throws Exception {
        byte[] pdf = export("?format=pdf&startDate=2026-11-01").andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"historial_palacetec_2026-11-01_hoy.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThat(text(pdf)).contains("Historial de apuestas · PalaceTec", "Jugador: " + player.email(),
                "Rango: 2026-11-01 a hoy", "Saldo tras apostar", "2026-11-10 12:00:00", "Pendiente", "950.00", "—",
                "4 apuestas").doesNotContain("500.00");
    }

    @Test
    void anEmptyPeriodIsExportedToo() throws Exception {
        String dates = "&startDate=2020-01-01&endDate=2020-01-01";
        assertThat(text(file("?format=pdf" + dates))).contains("No hay apuestas en este período.", "0 apuestas");
        assertThat(values(sheet(file("?format=xlsx" + dates)).getRow(FIRST_ROW + 1)))
                .containsExactly("Totales", "0 apuestas", "", 0.0, "", 0.0, 0.0, "");
    }

    @Test
    void longHistoriesRepeatTheHeaderOnEveryPage() throws Exception {
        jdbc.update("insert into user_bets (user_id, round_id, kind, selection, chips, amount, payout, reference, "
                + "created_at) select ?, ?, 'COLOR', 'rojo', '[10]', 1234.5, 0, gen_random_uuid(), now() "
                + "from generate_series(1, 60)", player.id(), round);
        byte[] pdf = file("?format=pdf");
        try (PdfReader reader = new PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                assertThat(new PdfTextExtractor(reader).getTextFromPage(page)).contains("Fecha (Lima)");
            }
        }
        assertThat(text(pdf)).contains("1,234.50", "-1,234.50");
    }

    /** Django conserva las más antiguas; aquí no se pierde lo último que jugó. */
    @Test
    void whenThereAreTooManyRowsTheNewestAreKept() throws Exception {
        jdbc.update("delete from user_bets where user_id = ?", player.id());
        jdbc.update("insert into user_bets (user_id, round_id, kind, selection, chips, amount, payout, reference, "
                + "created_at) select ?, ?, 'COLOR', 'rojo', '[10]', n, 0, gen_random_uuid(), "
                + "timestamptz '2026-11-20T00:00:00Z' + n * interval '1 second' from generate_series(1, 5001) n",
                player.id(), round);
        XSSFSheet sheet = sheet(file("?format=xlsx"));
        assertThat(values(sheet.getRow(FIRST_ROW)).get(3)).isEqualTo(2.0);
        assertThat(values(sheet.getRow(FIRST_ROW + 4999)).get(3)).isEqualTo(5001.0);
        assertThat(values(sheet.getRow(FIRST_ROW + 5001))).startsWith("Totales", "5000 apuestas");
    }

    /** En Django, un "=" inicial queda como fórmula y un carácter de control da un error 500. */
    @Test
    void titlesAreAlwaysPlainText() throws Exception {
        jdbc.update("update roulette_game set title = '=1+1' || chr(7) where id = ?", game);
        XSSFCell cell = sheet(file("?format=xlsx")).getRow(FIRST_ROW).getCell(1);
        assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
        assertThat(cell.getStringCellValue()).isEqualTo("=1+1");
        assertThat(text(file("?format=pdf"))).contains("=1+1");
    }

    /** En Django, un correo con "<" da un error 500 en el PDF. */
    @Test
    void emailsAreNeverReadAsMarkup() throws Exception {
        jdbc.update("update users set email = '\"<b\"&x@example.test' where id = ?", player.id());
        assertThat(text(file("?format=pdf"))).contains("Jugador: \"<b\"&x@example.test");
        assertThat(values(sheet(file("?format=xlsx")).getRow(1)).getFirst())
                .isEqualTo("Jugador: \"<b\"&x@example.test");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            ''                                   | {"format": ["This field is required."]}
            ?format=                             | {"format": ["Formato no válido. Usa xlsx o pdf."]}
            ?format=XLSX                         | {"format": ["Formato no válido. Usa xlsx o pdf."]}
            ?format=pdf&startDate=10/11/2026     | {"startDate": ["@"]}
            ?format=csv&startDate=x&endDate=2026-02-30 | {"startDate": ["@"], "endDate": ["@"], "format": ["Formato no válido. Usa xlsx o pdf."]}
            ?format=pdf&startDate=2026-11-10&endDate=2026-11-01 | {"endDate": ["La fecha final debe ser igual o posterior al inicio."]}
            ?format=csv&startDate=2026-11-10&endDate=2026-11-01 | {"format": ["Formato no válido. Usa xlsx o pdf."]}
            ?format=xlsx&endDate=9999-12-31      | {"endDate": ["La fecha final excede el rango permitido."]}
            """)
    void theQueryIsValidated(String query, String expected) throws Exception {
        export(query).andExpect(status().isBadRequest())
                .andExpect(content().json(expected.replace("@", BAD_DATE), true));
    }

    @ParameterizedTest
    @CsvSource({"startDate=2026-1-5,2026-01-05_hoy", "startDate=9999-12-31,9999-12-31_hoy",
            "startDate=0001-01-01&endDate=0001-01-01,0001-01-01_0001-01-01"})
    void acceptsTheSameDatesAsDjango(String dates, String name) throws Exception {
        export("?format=xlsx&" + dates).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",
                "attachment; filename=\"historial_palacetec_" + name + ".xlsx\""));
    }

    @Test
    void requiresASessionAndReactCanReadTheFileName() throws Exception {
        mvc.perform(get(PATH + "?format=pdf")).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "?format=pdf").with(player.bearer()).header("Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Expose-Headers",
                        org.hamcrest.Matchers.containsString("Content-Disposition")));
    }
}
