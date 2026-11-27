package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.Browser;
import com.tecsup.palacetec.assistant.AssistantProvider.Turn;
import com.tecsup.palacetec.assistant.AssistantProviders;
import com.tecsup.palacetec.config.AssistantProperties;
import com.tecsup.palacetec.entity.AssistantMessage.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** HU68 y HU69 de Django. La IA es simulada: ver TestAssistantProviders. */
class AssistantTest extends ApiTest {

    private static final String CHAT = "/api/v1/assistant/chat";
    private static final String ODDS = "/api/v1/assistant/odds";
    private static final String REMINDER = "Juega con responsabilidad: son créditos virtuales y puedes fijar tu límite "
            + "diario en Mis límites.";
    private static final String GUARD = "No puedo darte estrategias, trucos ni consejos";
    private static final String FALLBACK = "No tengo una respuesta exacta para eso.";

    @Autowired
    private AssistantProperties properties;

    private Player player;

    @BeforeEach
    void player() {
        player = newPlayer();
    }

    private ResultActions send(String body) throws Exception {
        return mvc.perform(post(CHAT).with(player.bearer()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode ask(String message) throws Exception {
        return json(send(mapper.writeValueAsString(Map.of("message", message))).andExpect(status().isCreated())
                .andReturn());
    }

    private JsonNode reply(String message) throws Exception {
        return ask(message).get("reply");
    }

    private void expect(String message, String source, String text) throws Exception {
        JsonNode reply = reply(message);
        assertThat(reply.get("source").asText()).as(message).isEqualTo(source);
        assertThat(reply.get("content").asText()).as(message).contains(text);
    }

    private JsonNode history() throws Exception {
        return json(mvc.perform(get(CHAT).with(player.bearer())).andExpect(status().isOk()).andReturn());
    }

    @Test
    void testsNeverUseARealProvider() {
        assertThat(properties.apiKey()).isEmpty();
        assertThat(new AssistantProviders(properties, mapper).current()).isEmpty();
    }

    @Test
    void anAnswerIsSavedWithItsQuestion() throws Exception {
        JsonNode body = ask("  ¿Cómo se juega la ruleta?  ");
        assertThat(body).hasSize(3);
        assertThat(body.get("reminder").asText()).isEqualTo(REMINDER);
        JsonNode question = body.get("question");
        assertThat(question).hasSize(6);
        assertThat(question.get("role").asText()).isEqualTo("USER");
        assertThat(question.get("content").asText()).isEqualTo("¿Cómo se juega la ruleta?");
        assertThat(question.get("source").asText()).isEmpty();
        assertThat(question.get("data").toString()).isEqualTo("{}");
        assertThat(question.get("created_at").asText()).endsWith("+00:00");
        JsonNode reply = body.get("reply");
        assertThat(reply.get("role").asText()).isEqualTo("ASSISTANT");
        assertThat(reply.get("source").asText()).isEqualTo("faq");
        assertThat(reply.get("content").asText()).contains("37 casillas");
        assertThat(history()).isEqualTo(mapper.createObjectNode()
                .<com.fasterxml.jackson.databind.node.ObjectNode>set("messages",
                        mapper.createArrayNode().add(question).add(reply)).put("reminder", REMINDER));
    }

    @Test
    void theHistoryIsPrivateAndShowsTheLastThirty() throws Exception {
        Player other = newPlayer();
        jdbc.update("insert into assistant_messages (user_id, role, content, source, data, created_at) "
                + "values (?, 'USER', 'secreto', '', '{}', now())", other.id());
        assertThat(history().get("messages")).isEmpty();
        jdbc.update("insert into assistant_messages (user_id, role, content, source, data, created_at) "
                + "select ?, 'USER', 'm' || n, '', '{}', now() + n * interval '1 second' from generate_series(1, 35) n",
                player.id());
        JsonNode messages = history().get("messages");
        assertThat(messages).hasSize(30);
        assertThat(messages.get(0).get("content").asText()).isEqualTo("m6");
        assertThat(messages.get(29).get("content").asText()).isEqualTo("m35");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            cuanto paga                        | Pagos de la ruleta europea (pago neto)
            ¿Cuánto paga cada apuesta?         | 35 a 1
            reglas del blackjack               | plantarte
            ¿Puedo retirar mi dinero?          | solo créditos virtuales
            quiero autoexcluirme               | 7, 30 o 90 días
            quiero una pausa                   | aviso de realidad
            no puedo parar                     | Si el juego deja de ser entretenimiento
            olvidé mi contraseña               | el enlace dura 15 minutos
            cambiar mi apodo                   | Mi perfil
            las promociones                    | campana
            mi historial                       | porcentaje de victorias
            ¿qué es el RTP exactamente?        | 97.30 %
            cuotas de deportes                 | 1X2
            probabilidad del 40                | No tengo una respuesta exacta
            hola                               | ¡Hola! Soy el asistente
            asdf                               | No tengo una respuesta exacta
            """)
    void frequentQuestionsAreAnsweredWithoutAi(String message, String text) throws Exception {
        expect(message, "faq", text);
    }

    /** En Django los signos pegados impiden reconocer las palabras cortas. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            ¡Hola!          | ¡Hola! Soy el asistente
            ¿Cuánto paga?   | Pagos de la ruleta europea
            ¿par?           | 37 casillas
            """)
    void punctuationDoesNotHideAKeyword(String message, String text) throws Exception {
        expect(message, "faq", text);
    }

    @Test
    void accountAnswersUseTheRealBalanceAndLimit() throws Exception {
        jdbc.update("update wallets set balance = 850, bonus_balance = 600 where user_id = ?", player.id());
        assertThat(reply("¿Cuál es mi saldo?").get("content").asText()).isEqualTo("Tu saldo es de 850.00 créditos "
                + "virtuales: 600.00 de bono y 250.00 de ganancias. Las apuestas usan primero el bono. Son créditos "
                + "simulados: no se pueden cobrar ni recargar con dinero real.");
        expect("¿cuál es mi límite diario?", "faq", "Todavía no defines tu límite diario");
        dailyLimit(player, 200);
        jdbc.update("insert into responsible_dailyspending (user_id, date, amount) "
                + "values (?, (now() at time zone 'America/Lima')::date, 40)", player.id());
        assertThat(reply("cuanto me queda de limite").get("content").asText()).isEqualTo("Tu límite diario es de 200.00 "
                + "créditos; hoy llevas 40.00 apostados y te quedan 160.00. El contador se reinicia a medianoche (hora "
                + "de Lima) y los premios no devuelven cupo.");
        jdbc.update("update responsible_dailylimit set pending_amount = 500, effective_at = now() + interval '1 day'");
        expect("¿y mi cupo?", "faq", "Tienes un aumento pendiente a 500.00 créditos que se activará en 24 horas.");
        jdbc.update("delete from wallets");
        expect("mi billetera", "faq", "Aún no tienes una billetera activa");
    }

    /** En Django, "preocupo" contiene "cupo" y se responde con el límite. */
    @Test
    void wordsThatContainCupoAreNotAboutTheLimit() throws Exception {
        expect("me preocupo por mi forma de jugar", "faq", FALLBACK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Dame una estrategia para ganar en la ruleta", "¿Tienes algún truco?", "¿algún hack?",
            "¿qué número va a salir?", "dame una estrategia con mi saldo"})
    void strategiesAreNeverGiven(String message) throws Exception {
        ai.answer("no debería llegar");
        expect(message, "guard", GUARD);
        assertThat(ai.calls()).isEmpty();
    }

    @Test
    void theAiReceivesTheCatalogAndTheConversation() throws Exception {
        expect("hola", "faq", "Hola");
        ai.answer("La ruleta tiene 37 casillas.");
        JsonNode reply = reply("¿Qué es un pleno?");
        assertThat(reply.get("source").asText()).isEqualTo("ai");
        assertThat(reply.get("content").asText()).isEqualTo("La ruleta tiene 37 casillas.");
        assertThat(reply.get("data").toString()).isEqualTo("{}");
        assertThat(ai.calls()).hasSize(1);
        var call = ai.calls().getFirst();
        assertThat(call.message()).isEqualTo("¿Qué es un pleno?");
        assertThat(call.system()).startsWith("Eres el asistente virtual de PalaceTec")
                .contains("información del catálogo:\n- ", "- Ruleta Europea (RTP 97.30 %). Ruleta europea de 37 casillas",
                        " Pagos: Pleno: paga 35 a 1; Color: paga 1 a 1; ");
        assertThat(call.history()).hasSize(2);
        assertThat(call.history().getFirst()).isEqualTo(new Turn(Role.USER, "hola"));
        assertThat(call.history().getLast().role()).isEqualTo(Role.ASSISTANT);
    }

    @Test
    void onlyTheLastSixMessagesAreSent() throws Exception {
        ai.answer("ok");
        for (int index = 1; index <= 5; index++) {
            reply("pregunta " + index);
        }
        assertThat(ai.calls().getLast().history()).containsExactly(new Turn(Role.USER, "pregunta 2"),
                new Turn(Role.ASSISTANT, "ok"), new Turn(Role.USER, "pregunta 3"), new Turn(Role.ASSISTANT, "ok"),
                new Turn(Role.USER, "pregunta 4"), new Turn(Role.ASSISTANT, "ok"));
    }

    /** Django envía a la IA el saldo y el límite dentro del historial. */
    @Test
    void theBalanceAndTheLimitNeverReachTheAi() throws Exception {
        ai.answer("ok");
        dailyLimit(player, 200);
        expect("¿cuál es mi saldo?", "faq", "1000.00");
        expect("mi limite diario", "faq", "200.00");
        assertThat(ai.calls()).isEmpty();
        // Respuesta guardada por Django a una pregunta que aquí no es de la cuenta.
        jdbc.update("insert into assistant_messages (user_id, role, content, source, data, created_at) values "
                + "(?, 'USER', 'me preocupo', '', '{}', now()), (?, 'ASSISTANT', 'Tu límite diario es de 200.00 "
                + "créditos', 'faq', '{}', now())", player.id(), player.id());
        reply("¿cómo se juega?");
        assertThat(ai.calls().getLast().history()).isEmpty();
        reply("¿y el blackjack?");
        assertThat(ai.calls().getLast().history()).containsExactly(new Turn(Role.USER, "¿cómo se juega?"),
                new Turn(Role.ASSISTANT, "ok"));
        assertThat(ai.calls().toString()).doesNotContain("1000.00", "200.00");
        // El jugador sí conserva la conversación completa.
        assertThat(history().get("messages")).hasSize(10);
    }

    @Test
    void aFailureOfTheAiFallsBackToTheFrequentQuestions() throws Exception {
        ai.fail("caído");
        expect("reglas de la ruleta", "faq", "37 casillas");
        ai.answer("");
        expect("reglas de la ruleta", "faq", "37 casillas");
        assertThat(ai.calls()).hasSize(2);
        assertThat(count("assistant_messages")).isEqualTo(4);
    }

    @Test
    void theAnswerOfTheAiIsFilteredAndCut() throws Exception {
        ai.answer("Usa la martingala y apuesta más.");
        expect("reglas de la ruleta", "guard", GUARD);
        ai.answer("🎲".repeat(2100));
        assertThat(reply("reglas de la ruleta").get("content").asText()).isEqualTo("🎲".repeat(2000));
        // PostgreSQL no guarda el carácter nulo: en Django da un error 500.
        ai.answer("con nulo \0dentro");
        assertThat(reply("reglas de la ruleta").get("content").asText()).isEqualTo("con nulo dentro");
        ai.answer("\0");
        expect("reglas de la ruleta", "faq", "37 casillas");
    }

    @Test
    void oddsAreComputedHereAndNeverByTheAi() throws Exception {
        ai.answer("99 %");
        JsonNode reply = reply("¿Cuál es la probabilidad de un pleno al 17?");
        assertThat(ai.calls()).isEmpty();
        assertThat(reply.get("source").asText()).isEqualTo("odds");
        assertThat(reply.get("content").asText()).contains("2.70 %", "35 a 1").endsWith(REMINDER);
        assertThat(reply.get("data")).isEqualTo(mapper.readTree("""
                {"odds": {"bet": "straight", "selection": "17", "label": "Pleno al 17", "winning_numbers": 1,
                 "total_numbers": 37, "probability": "1/37", "probability_pct": "2.70", "payout": "35:1",
                 "total_return": 36, "house_edge": "1/37", "house_edge_pct": "2.70"}}"""));
        assertThat(history().get("messages").get(1).get("data")).isEqualTo(reply.get("data"));
        expect("probabilidad de la docena 4", "ai", "99 %");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {}                     | This field is required.
            {"message": null}      | This field may not be null.
            {"message": "   "}     | This field may not be blank.
            {"message": true}      | Not a valid string.
            """)
    void theMessageIsValidated(String body, String message) throws Exception {
        send(body).andExpect(status().isBadRequest())
                .andExpect(content().json(mapper.writeValueAsString(Map.of("message", List.of(message))), true));
        send("{\"message\": \"" + "x".repeat(501) + "\"}").andExpect(status().isBadRequest()).andExpect(content()
                .json("{\"message\": [\"Ensure this field has no more than 500 characters.\"]}", true));
        assertThat(count("assistant_messages")).isZero();
    }

    @Test
    void twentyMessagesPerMinuteAreAllowedAndInvalidOnesCount() throws Exception {
        for (int index = 0; index < 10; index++) {
            send("{\"message\": \"hola\"}").andExpect(status().isCreated());
            send("{}").andExpect(status().isBadRequest());
        }
        send("{\"message\": \"hola\"}").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After")).andExpect(content().json("""
                        {"code": "assistant_rate_limited",
                         "detail": "Enviaste muchos mensajes seguidos. Espera un momento y vuelve a intentarlo."}""", true));
        history();
        player = newPlayer();
        send("{\"message\": \"hola\"}").andExpect(status().isCreated());
    }

    @Test
    void chatRequiresASessionAndCsrf() throws Exception {
        mvc.perform(get(CHAT)).andExpect(status().isUnauthorized());
        mvc.perform(post(CHAT)).andExpect(status().isUnauthorized());
        mvc.perform(get(ODDS + "?bet=color&selection=rojo")).andExpect(status().isUnauthorized());
        Browser browser = browser();
        browser.login(player.email(), PASSWORD).andExpect(status().isOk());
        browser.withoutCsrf().create(CHAT, Map.of("message", "hola")).andExpect(status().isForbidden());
        assertThat(count("assistant_messages")).isZero();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            ?bet=straight&number=17           | straight | 17
            ?bet= PLENO &number=0             | straight | 0
            ?bet=numero&number=7.0            | straight | 7
            ?bet=straight&selection=5         | straight | 5
            ?bet=color&selection=Rojo&number= | color    | rojo
            ?bet=docena&selection= 3          | dozen    | 3
            ?bet=paridad&selection=IMPAR      | parity   | impar
            """)
    void oddsOfABet(String query, String bet, String selection) throws Exception {
        mvc.perform(get(ODDS + query).with(player.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11)).andExpect(jsonPath("$.bet").value(bet))
                .andExpect(jsonPath("$.selection").value(selection)).andExpect(jsonPath("$.total_numbers").value(37));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            ''                            | {"bet": ["This field is required."]}
            ?bet=                         | {"bet": ["This field may not be blank."]}
            ?bet=straight                 | {"selection": ["Indica number (pleno) o selection (color, paridad, docena o columna)."]}
            ?bet=color&selection=         | {"selection": ["Indica number (pleno) o selection (color, paridad, docena o columna)."]}
            ?bet=straight&number=37       | {"number": ["Ensure this value is less than or equal to 36."]}
            ?bet=straight&number=-1       | {"number": ["Ensure this value is greater than or equal to 0."]}
            ?bet=straight&number=x        | {"number": ["A valid integer is required."]}
            ?bet=color&selection=yyyyyyyyyyy | {"selection": ["Ensure this field has no more than 10 characters."]}
            ?bet=xxxxxxxxxxxxxxxxxxxxx    | {"bet": ["Ensure this field has no more than 20 characters."]}
            ?bet=color&number=5           | {"detail": "Selección no válida para color: usa rojo, negro."}
            ?bet=split&number=5           | {"detail": "Tipo de apuesta no válido. Usa straight, color, parity, dozen o column."}
            """)
    void invalidOddsAreRejected(String query, String expected) throws Exception {
        mvc.perform(get(ODDS + query).with(player.bearer())).andExpect(status().isBadRequest())
                .andExpect(content().json(expected, true));
    }
}
