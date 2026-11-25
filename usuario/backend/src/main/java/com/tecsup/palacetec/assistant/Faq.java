package com.tecsup.palacetec.assistant;

import com.tecsup.palacetec.dto.DailyLimitResponse;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.PlainText;
import com.tecsup.palacetec.entity.Wallet;
import com.tecsup.palacetec.repository.WalletRepository;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.DailyLimitService;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Preguntas frecuentes por palabras clave, como assistant/faq.py. Las respuestas sobre la cuenta salen siempre de aquí,
 * con datos reales, para no enviar información personal al proveedor de IA.
 */
@Component
public class Faq {

    private static final String FALLBACK =
            "No tengo una respuesta exacta para eso. Puedo ayudarte con las reglas y pagos de la "
                    + "ruleta, el blackjack y los deportes virtuales, las probabilidades de una apuesta (por "
                    + "ejemplo «probabilidad de un pleno al 17»), tu saldo, tu límite diario, la autoexclusión "
                    + "y tu perfil.";

    private static final String PAYOUTS =
            "Pagos de la ruleta europea (pago neto): pleno 35 a 1; color y par/impar 1 a 1; docena y "
                    + "columna 2 a 1. Blackjack: victoria 1 a 1, blackjack natural 3 a 2 y el empate devuelve "
                    + "la apuesta. Deportes virtuales: cuotas fijas de 1.90 (local), 3.20 (empate) y 2.40 "
                    + "(visitante). Puedes ver cada tabla en el botón «Reglas y pagos» del catálogo.";

    private static final String NO_WALLET =
            "Aún no tienes una billetera activa. Si acabas de registrarte, vuelve a iniciar sesión.";
    private static final String NO_LIMIT =
            "Todavía no defines tu límite diario. Es obligatorio antes de apostar: ve a Mis límites y elige "
                    + "cuánto quieres apostar como máximo por día. Puedes bajarlo al instante; subirlo tarda 24 horas.";
    private static final String BALANCE_START = "Tu saldo es de ";
    private static final String LIMIT_START = "Tu límite diario es de ";
    private static final List<String> ACCOUNT_STARTS = List.of(BALANCE_START, LIMIT_START, NO_WALLET, NO_LIMIT);

    private static final List<String> BALANCE_WORDS = List.of("saldo", "credito", "billetera", "bono", "cuanto tengo");
    private static final List<String> LIMIT_WORDS = List.of("limite", "cuanto puedo apostar", "cuanto me queda");
    /** Solo como palabra completa: en Django, "me preocupo" la contiene y se responde con el límite. */
    private static final String LIMIT_WORD = " cupo ";

    private record Topic(List<String> keywords, String answer) {
    }

    /** Se usa la primera coincidencia. Las palabras clave están sin tildes y en minúsculas. */
    private static final List<Topic> TOPICS = List.of(
            new Topic(List.of("dinero real", "retirar", "retiro", "cobrar", "depositar", "deposito", "recargar",
                    "recarga", "canjear"),
                    "PalaceTec usa solo créditos virtuales: no se deposita, recarga, retira ni cobra "
                            + "dinero real. Al registrarte recibes 1,000 créditos de bienvenida."),
            new Topic(List.of("autoexcl", "excluirme", "excluir mi cuenta", "bloquear mi cuenta", "dejar de jugar"),
                    "Puedes autoexcluirte por 7, 30 o 90 días desde Mis límites. Durante ese tiempo "
                            + "no podrás iniciar sesión ni apostar, y no se puede cancelar antes. Las apuestas "
                            + "ya aceptadas se liquidan normalmente."),
            new Topic(List.of("pausa", "descanso", "aviso de realidad", "tiempo jugando", "cuanto tiempo"),
                    "Cada hora de sesión verás un aviso de realidad con tu tiempo y resultado. Si el "
                            + "sistema detecta que subes las apuestas tras perder, te sugerirá una pausa de 10 "
                            + "minutos; si la aceptas, no podrás apostar durante ese tiempo."),
            new Topic(List.of("adiccion", "ludopatia", "no puedo parar", "problema con el juego", "necesito ayuda",
                    "me siento mal"),
                    "Si el juego deja de ser entretenimiento, detente. Usa Mis límites para bajar tu "
                            + "límite diario, tomar una pausa o autoexcluirte, y busca apoyo en alguien de "
                            + "confianza o en un profesional de la salud."),
            new Topic(List.of("contrasena", "clave", "password"),
                    "Cambia tu contraseña en Mi perfil > Seguridad. Si la olvidaste, usa «¿Olvidaste "
                            + "tu contraseña?» en el inicio de sesión: el enlace dura 15 minutos y al "
                            + "cambiarla se cierran tus otras sesiones."),
            new Topic(List.of("perfil", "apodo", "avatar", "foto", "telefono", "nombre de usuario"),
                    "En Mi perfil puedes cambiar tu apodo, nombres, teléfono y avatar. El correo y "
                            + "el DNI no se pueden editar."),
            new Topic(List.of("notificacion", "promocion", "correo"),
                    "Las notificaciones aparecen en la campana del encabezado. En Mi perfil > "
                            + "Notificaciones eliges si quieres recibir promociones, avisos de seguridad y "
                            + "alertas de juego."),
            new Topic(List.of("historial", "mis apuestas", "estadistica"),
                    "En Mi historial ves todas tus apuestas con su resultado y puedes filtrarlas por "
                            + "fechas. En Mis estadísticas tienes tu porcentaje de victorias, tu mayor premio "
                            + "y la evolución de tu saldo."),
            new Topic(List.of("rtp", "ventaja de la casa", "retorno"),
                    "El RTP es el porcentaje de lo apostado que un juego devuelve a largo plazo. En "
                            + "la ruleta europea es 97.30 %: la casa conserva una ventaja de 2.70 % (1/37) en "
                            + "todas las apuestas. A corto plazo los resultados varían."),
            new Topic(List.of("blackjack", "21", "carta", "banca", "plantarme", "plantarse", "pedir carta"),
                    "Blackjack: llega lo más cerca posible de 21 sin pasarte. El As vale 1 u 11 y "
                            + "las figuras 10. Puedes pedir carta o plantarte; la banca se planta en 17. "
                            + "Victoria paga 1 a 1, blackjack natural 3 a 2 y el empate devuelve la apuesta. "
                            + "Apuesta de 10 a 500 créditos."),
            new Topic(List.of("deporte", "futbol", "partido", "cuota", "local", "visitante", "empate"),
                    "Deportes virtuales: partidos de fútbol simulados con mercado 1X2 (local, empate "
                            + "o visitante) y cuotas fijas de 1.90, 3.20 y 2.40. Se apuesta antes del inicio y "
                            + "el marcador lo genera el servidor. Apostar 10 a cuota 1.90 devuelve 19 si "
                            + "aciertas."),
            new Topic(List.of("paga", "pago", "pagos", "premio", "cuanto gano", "tabla"),
                    PAYOUTS),
            new Topic(List.of("ruleta", "pleno", "docena", "columna", "rojo", "negro", "par", "impar", "regla",
                    "como se juega", "como funciona", "fichas"),
                    "Ruleta europea: 37 casillas (0 al 36). Tienes 15 segundos para colocar fichas "
                            + "de 10, 50, 100 o 500 créditos en un pleno, color, par/impar, docena o columna; "
                            + "luego el servidor gira la ruleta. Si sale 0, pierden todas las apuestas "
                            + "externas. Cada sala tiene un mínimo por apuesta y un máximo por ronda."),
            new Topic(List.of("hola", "buenas", "buenos dias", "buenas tardes", "ayuda", "que puedes hacer"),
                    "¡Hola! Soy el asistente de PalaceTec. Te explico reglas, pagos, probabilidades, "
                            + "límites y cómo usar tu cuenta. No doy estrategias para ganar: los resultados "
                            + "son aleatorios."));
    /** Desde aquí basta con que la palabra clave aparezca dentro de otra. */
    private static final int LONG_KEYWORD = 5;

    private final WalletRepository wallets;
    private final DailyLimitService limits;

    public Faq(WalletRepository wallets, DailyLimitService limits) {
        this.wallets = wallets;
        this.limits = limits;
    }

    public String answer(PlayerPrincipal player, String message) {
        String text = PlainText.normalize(message);
        if (asksForBalance(text)) {
            return balance(player);
        }
        if (asksForLimit(text)) {
            return limit(player);
        }
        String words = PlainText.words(text);
        return TOPICS.stream()
                .filter(topic -> topic.keywords().stream().anyMatch(keyword -> words.contains(" " + keyword + " ")
                        || keyword.length() >= LONG_KEYWORD && (text.contains(keyword) || words.contains(keyword))))
                .map(Topic::answer).findFirst().orElse(FALLBACK);
    }

    static boolean isAccountQuestion(String message) {
        String text = PlainText.normalize(message);
        return asksForBalance(text) || asksForLimit(text);
    }

    /** La respuesta contiene datos de la cuenta. Reconoce también las que guardó Django. */
    static boolean isAccountAnswer(String reply) {
        return ACCOUNT_STARTS.stream().anyMatch(reply::startsWith);
    }

    private static boolean asksForBalance(String text) {
        return BALANCE_WORDS.stream().anyMatch(text::contains);
    }

    private static boolean asksForLimit(String text) {
        return LIMIT_WORDS.stream().anyMatch(text::contains) || PlainText.words(text).contains(LIMIT_WORD);
    }

    private String balance(PlayerPrincipal player) {
        Wallet wallet = wallets.findByUserId(player.userId()).orElse(null);
        if (wallet == null) {
            return NO_WALLET;
        }
        return BALANCE_START + Formats.money(wallet.getBalance()) + " créditos virtuales: "
                + Formats.money(wallet.getBonusBalance()) + " de bono y "
                + Formats.money(wallet.getBalance().subtract(wallet.getBonusBalance())) + " de ganancias. "
                + "Las apuestas usan primero el bono. "
                + "Son créditos simulados: no se pueden cobrar ni recargar con dinero real.";
    }

    private String limit(PlayerPrincipal player) {
        DailyLimitResponse summary = limits.summary(player);
        if (!summary.configured()) {
            return NO_LIMIT;
        }
        String text = LIMIT_START + summary.limit() + " créditos; hoy llevas " + summary.spent()
                + " apostados y te quedan " + summary.remaining() + ". El contador se reinicia a medianoche "
                + "(hora de Lima) y los premios no devuelven cupo.";
        return summary.pendingLimit() == null ? text : text + " Tienes un aumento pendiente a "
                + summary.pendingLimit() + " créditos que se activará en 24 horas.";
    }
}
