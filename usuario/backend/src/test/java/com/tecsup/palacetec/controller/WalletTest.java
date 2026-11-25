package com.tecsup.palacetec.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.Browser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** HU06, HU07 y HU36 de Django. */
class WalletTest extends ApiTest {

    private static final String BALANCE = "/api/v1/wallet/balance";
    private static final String SUMMARY = "/api/v1/wallet/summary";
    private static final String PRE_BET = "/api/v1/wallet/pre-bet-check";

    private ResultActions preBet(Player player, String body) throws Exception {
        return mvc.perform(post(PRE_BET).with(player.bearer()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void balanceIsOnlyTheOwnOne() throws Exception {
        Player other = newPlayer();
        jdbc.update("update wallets set balance = 50, bonus_balance = 0 where user_id = ?", other.id());
        Player player = newPlayer();
        for (String path : new String[] {BALANCE, BALANCE + "?user=" + other.id(), PRE_BET}) {
            mvc.perform(get(path).with(player.bearer())).andExpect(status().isOk()).andExpect(content().json("""
                    {"balance": "1000.00", "low_balance_threshold": "10.00", "is_low_balance": false}""", true));
        }
        mvc.perform(get(BALANCE)).andExpect(status().isUnauthorized());
    }

    @Test
    void summarySeparatesBonusAndWinnings() throws Exception {
        Player player = newPlayer();
        jdbc.update("update wallets set balance = 1250.5, bonus_balance = 900 where user_id = ?", player.id());
        mvc.perform(get(SUMMARY).with(player.bearer())).andExpect(status().isOk()).andExpect(content().json("""
                {"balance": "1250.50", "bonus_balance": "900.00", "winnings_balance": "350.50",
                "low_balance_threshold": "10.00", "is_low_balance": false}""", true));
    }

    @ParameterizedTest
    @CsvSource({"10, false", "9.99, true", "0, true"})
    void lowBalanceStartsBelowTen(String balance, boolean low) throws Exception {
        Player player = newPlayer();
        jdbc.update("update wallets set balance = ?::numeric, bonus_balance = 0 where user_id = ?", balance,
                player.id());
        for (String path : new String[] {BALANCE, SUMMARY}) {
            mvc.perform(get(path).with(player.bearer())).andExpect(jsonPath("$.is_low_balance").value(low))
                    .andExpect(jsonPath("$.low_balance_threshold").value("10.00"));
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            1000                   | true
            1000.01                | false
            "1000.00"              | true
            " 1_000 "              | true
            "1e3"                  | true
            1e3                    | true
            "+5"                   | true
            ".5"                   | true
            0.10                   | true
            "1.0000"               | true
            9999999999999999.98999 | false
            "9999999999999999.99"  | false
            """)
    void preBetCheckComparesWithTheBalance(String amount, boolean canBet) throws Exception {
        ResultActions answer = preBet(newPlayer(), "{\"amount\": " + amount + "}");
        // Un número JSON pasa por float, como en Python: 9999999999999999.98999 ya es 1e16.
        if (amount.startsWith("99999")) {
            answer.andExpect(status().isBadRequest());
            return;
        }
        answer.andExpect(status().isOk()).andExpect(content().json(
                "{\"balance\": \"1000.00\", \"can_bet\": " + canBet + "}", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"amount\": 0}", "{\"amount\": -1}", "{\"amount\": \"0.001\"}", "{\"amount\": \"NaN\"}",
            "{\"amount\": \"Infinity\"}", "{\"amount\": \"abc\"}", "{\"amount\": \"\"}", "{\"amount\": null}",
            "{\"amount\": true}", "{\"amount\": [5]}", "{\"amount\": \"1e16\"}", "{\"amount\": \"1e999999999\"}",
            "{\"amount\": \"1e-999999999\"}", "{\"amount\": 1e400}", "{}", ""})
    void preBetCheckRejectsInvalidAmounts(String body) throws Exception {
        preBet(newPlayer(), body).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"detail\": \"Monto invalido.\"}", true));
    }

    @Test
    void worksWithTheCookieSession() throws Exception {
        Player player = newPlayer();
        Browser browser = browser();
        browser.login(player.email(), PASSWORD);
        browser.read(BALANCE).andExpect(status().isOk()).andExpect(jsonPath("$.balance").value("1000.00"));
        browser.read(SUMMARY).andExpect(status().isOk());
        browser.create(PRE_BET, Map.of("amount", 5)).andExpect(status().isOk())
                .andExpect(jsonPath("$.can_bet").value(true));
        browser.withoutCsrf().create(PRE_BET, Map.of("amount", 5)).andExpect(status().isForbidden());
        mvc.perform(post(PRE_BET)).andExpect(status().isUnauthorized());
    }
}
