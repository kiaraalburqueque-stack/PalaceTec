package com.tecsup.palacetec.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecsup.palacetec.assistant.AssistantProvider.Turn;
import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.ChatResponse;
import com.tecsup.palacetec.dto.OddsResponse;
import com.tecsup.palacetec.entity.AssistantMessage;
import com.tecsup.palacetec.entity.AssistantMessage.Role;
import com.tecsup.palacetec.entity.Game;
import com.tecsup.palacetec.repository.AssistantMessageRepository;
import com.tecsup.palacetec.repository.GameRepository;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * Decide de dónde sale cada respuesta y guarda la conversación (HU68), como assistant/services.py. No abre una
 * transacción: la llamada a la IA puede tardar y no debe retener una conexión de la base.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final int HISTORY = 30;
    private static final int HISTORY_FOR_AI = 6;
    private static final int MAX_REPLY = 2000;

    private final AssistantMessageRepository messages;
    private final GameRepository games;
    private final AssistantProviders providers;
    private final Faq faq;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AssistantService(AssistantMessageRepository messages, GameRepository games, AssistantProviders providers,
                            Faq faq, ObjectMapper mapper, Clock clock) {
        this.messages = messages;
        this.games = games;
        this.providers = providers;
        this.faq = faq;
        this.mapper = mapper;
        this.clock = clock;
    }

    private record Reply(String text, String source, JsonNode data) {
    }

    public ChatResponse.History history(Long userId) {
        return new ChatResponse.History(latest(userId, HISTORY).stream().map(ChatResponse.Message::from).toList(),
                AssistantTexts.RESPONSIBLE_REMINDER);
    }

    public ChatResponse answer(PlayerPrincipal player, String message) {
        Reply reply = reply(player, message);
        Long userId = player.userId();
        // Las dos juntas o ninguna: saveAll es una sola transacción.
        List<AssistantMessage> saved = messages.saveAll(List.of(
                new AssistantMessage(userId, Role.USER, message, "", mapper.createObjectNode(), TimeConfig.now(clock)),
                new AssistantMessage(userId, Role.ASSISTANT, reply.text(), reply.source(), reply.data(),
                        TimeConfig.now(clock))));
        return new ChatResponse(ChatResponse.Message.from(saved.get(0)), ChatResponse.Message.from(saved.get(1)),
                AssistantTexts.RESPONSIBLE_REMINDER);
    }

    /** Orden: filtro de seguridad, probabilidades (HU69), datos de la cuenta, proveedor de IA y preguntas frecuentes. */
    private Reply reply(PlayerPrincipal player, String message) {
        if (StrategyGuard.asksForStrategy(message)) {
            return plain(AssistantTexts.GUARD_REPLY, "guard");
        }
        Odds.Question bet = Odds.parse(message);
        if (bet != null) {
            OddsResponse odds = Odds.compute(bet);
            return new Reply(Odds.text(odds) + " " + AssistantTexts.RESPONSIBLE_REMINDER, "odds",
                    mapper.valueToTree(Map.of("odds", odds)));
        }
        if (!Faq.isAccountQuestion(message)) {
            String text = providers.current().map(provider -> ask(provider, player.userId(), message)).orElse("");
            if (!text.isEmpty()) {
                return StrategyGuard.asksForStrategy(text) ? plain(AssistantTexts.GUARD_REPLY, "guard")
                        : plain(cut(text), "ai");
            }
        }
        return plain(faq.answer(player, message), "faq");
    }

    /** Vacío si el proveedor falla: se responde con las preguntas frecuentes. */
    private String ask(AssistantProvider provider, Long userId, String message) {
        try {
            String text = provider.complete(AssistantTexts.SYSTEM_PROMPT + catalog(), historyForAi(userId), message);
            // PostgreSQL no admite el carácter nulo.
            return text.replace("\0", "");
        } catch (ProviderException exception) {
            log.warn("El proveedor de IA {} falló; se responde con preguntas frecuentes: {}", provider.name(),
                    exception.getMessage());
            return "";
        }
    }

    /**
     * Últimos mensajes, sin los que tratan del saldo o del límite del jugador: esos datos no salen de aquí. Django sí
     * los envía.
     */
    private List<Turn> historyForAi(Long userId) {
        List<Turn> turns = new ArrayList<>();
        boolean skipAnswer = false;
        for (AssistantMessage message : latest(userId, HISTORY_FOR_AI)) {
            if (message.getRole() == Role.USER) {
                skipAnswer = Faq.isAccountQuestion(message.getContent());
                if (!skipAnswer) {
                    turns.add(new Turn(Role.USER, message.getContent()));
                }
            } else if (Faq.isAccountAnswer(message.getContent())) {
                // Se quita también su pregunta, aunque no se haya reconocido como una de la cuenta.
                if (!skipAnswer && !turns.isEmpty() && turns.getLast().role() == Role.USER) {
                    turns.removeLast();
                }
            } else if (!skipAnswer && !turns.isEmpty()) {
                turns.add(new Turn(Role.ASSISTANT, message.getContent()));
            }
        }
        return turns;
    }

    /** Del más antiguo al más reciente. */
    private List<AssistantMessage> latest(Long userId, int size) {
        return messages.findByUserIdOrderByCreatedAtDescIdDesc(userId, Limit.of(size)).reversed();
    }

    /** Reglas y pagos de los juegos disponibles, como contexto para el modelo. */
    private String catalog() {
        return games.findAvailable().stream().map(AssistantService::describe).collect(Collectors.joining("\n"));
    }

    private static String describe(Game game) {
        String payouts = StreamSupport.stream(game.getPayoutTable().spliterator(), false)
                .map(row -> row.path("bet").asText("None") + ": paga " + row.path("pays").asText("None"))
                .collect(Collectors.joining("; "));
        return "- " + game.getTitle() + " (RTP " + game.getRtp().toPlainString() + " %). " + game.getRules()
                + " Pagos: " + payouts;
    }

    private Reply plain(String text, String source) {
        return new Reply(text, source, mapper.createObjectNode());
    }

    private static String cut(String text) {
        return text.codePointCount(0, text.length()) <= MAX_REPLY ? text
                : text.substring(0, text.offsetByCodePoints(0, MAX_REPLY));
    }
}
