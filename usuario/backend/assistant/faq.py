"""HU68: respuestas de preguntas frecuentes por palabras clave (modo sin clave de IA).

Las respuestas sobre la cuenta (saldo y límite diario) siempre salen de aquí, con datos reales de la base,
para no enviar información personal al proveedor de IA.
"""
from roulette.catalog import normalize

FALLBACK = (
    "No tengo una respuesta exacta para eso. Puedo ayudarte con las reglas y pagos de la ruleta, el blackjack y "
    "los deportes virtuales, las probabilidades de una apuesta (por ejemplo «probabilidad de un pleno al 17»), "
    "tu saldo, tu límite diario, la autoexclusión y tu perfil."
)


def has(text, *words):
    return any(word in text for word in words)


def balance_answer(user):
    from wallet.models import Wallet
    wallet = Wallet.objects.filter(user=user).first()
    if wallet is None:
        return "Aún no tienes una billetera activa. Si acabas de registrarte, vuelve a iniciar sesión."
    return (f"Tu saldo es de {wallet.balance:.2f} créditos virtuales: {wallet.bonus_balance:.2f} de bono y "
            f"{wallet.winnings_balance:.2f} de ganancias. Las apuestas usan primero el bono. "
            "Son créditos simulados: no se pueden cobrar ni recargar con dinero real.")


def limit_answer(user):
    from responsible.services import LimitService
    summary = LimitService.summary(user)
    if not summary["configured"]:
        return ("Todavía no defines tu límite diario. Es obligatorio antes de apostar: ve a Mis límites y elige "
                "cuánto quieres apostar como máximo por día. Puedes bajarlo al instante; subirlo tarda 24 horas.")
    text = (f"Tu límite diario es de {summary['limit']} créditos; hoy llevas {summary['spent']} apostados y te quedan "
            f"{summary['remaining']}. El contador se reinicia a medianoche (hora de Lima) y los premios no devuelven cupo.")
    if summary["pending_limit"]:
        text += f" Tienes un aumento pendiente a {summary['pending_limit']} créditos que se activará en 24 horas."
    return text


def payouts_answer():
    return ("Pagos de la ruleta europea (pago neto): pleno 35 a 1; color y par/impar 1 a 1; docena y columna 2 a 1. "
            "Blackjack: victoria 1 a 1, blackjack natural 3 a 2 y el empate devuelve la apuesta. "
            "Deportes virtuales: cuotas fijas de 1.90 (local), 3.20 (empate) y 2.40 (visitante). "
            "Puedes ver cada tabla en el botón «Reglas y pagos» del catálogo.")


# (palabras clave, respuesta). Se usa la primera coincidencia; los textos llegan sin tildes y en minúsculas.
TOPICS = [
    (("dinero real", "retirar", "retiro", "cobrar", "depositar", "deposito", "recargar", "recarga", "canjear"),
     "PalaceTec usa solo créditos virtuales: no se deposita, recarga, retira ni cobra dinero real. Al registrarte "
     "recibes 1,000 créditos de bienvenida."),
    (("autoexcl", "excluirme", "excluir mi cuenta", "bloquear mi cuenta", "dejar de jugar"),
     "Puedes autoexcluirte por 7, 30 o 90 días desde Mis límites. Durante ese tiempo no podrás iniciar sesión ni "
     "apostar, y no se puede cancelar antes. Las apuestas ya aceptadas se liquidan normalmente."),
    (("pausa", "descanso", "aviso de realidad", "tiempo jugando", "cuanto tiempo"),
     "Cada hora de sesión verás un aviso de realidad con tu tiempo y resultado. Si el sistema detecta que subes las "
     "apuestas tras perder, te sugerirá una pausa de 10 minutos; si la aceptas, no podrás apostar durante ese tiempo."),
    (("adiccion", "ludopatia", "no puedo parar", "problema con el juego", "necesito ayuda", "me siento mal"),
     "Si el juego deja de ser entretenimiento, detente. Usa Mis límites para bajar tu límite diario, tomar una pausa "
     "o autoexcluirte, y busca apoyo en alguien de confianza o en un profesional de la salud."),
    (("contrasena", "clave", "password"),
     "Cambia tu contraseña en Mi perfil > Seguridad. Si la olvidaste, usa «¿Olvidaste tu contraseña?» en el inicio "
     "de sesión: el enlace dura 15 minutos y al cambiarla se cierran tus otras sesiones."),
    (("perfil", "apodo", "avatar", "foto", "telefono", "nombre de usuario"),
     "En Mi perfil puedes cambiar tu apodo, nombres, teléfono y avatar. El correo y el DNI no se pueden editar."),
    (("notificacion", "promocion", "correo"),
     "Las notificaciones aparecen en la campana del encabezado. En Mi perfil > Notificaciones eliges si quieres "
     "recibir promociones, avisos de seguridad y alertas de juego."),
    (("historial", "mis apuestas", "estadistica"),
     "En Mi historial ves todas tus apuestas con su resultado y puedes filtrarlas por fechas. En Mis estadísticas "
     "tienes tu porcentaje de victorias, tu mayor premio y la evolución de tu saldo."),
    (("rtp", "ventaja de la casa", "retorno"),
     "El RTP es el porcentaje de lo apostado que un juego devuelve a largo plazo. En la ruleta europea es 97.30 %: "
     "la casa conserva una ventaja de 2.70 % (1/37) en todas las apuestas. A corto plazo los resultados varían."),
    (("blackjack", "21", "carta", "banca", "plantarme", "plantarse", "pedir carta"),
     "Blackjack: llega lo más cerca posible de 21 sin pasarte. El As vale 1 u 11 y las figuras 10. Puedes pedir carta "
     "o plantarte; la banca se planta en 17. Victoria paga 1 a 1, blackjack natural 3 a 2 y el empate devuelve la "
     "apuesta. Apuesta de 10 a 500 créditos."),
    (("deporte", "futbol", "partido", "cuota", "local", "visitante", "empate"),
     "Deportes virtuales: partidos de fútbol simulados con mercado 1X2 (local, empate o visitante) y cuotas fijas de "
     "1.90, 3.20 y 2.40. Se apuesta antes del inicio y el marcador lo genera el servidor. Apostar 10 a cuota 1.90 "
     "devuelve 19 si aciertas."),
    (("paga", "pago", "pagos", "premio", "cuanto gano", "tabla"), None),  # respuesta dinámica: payouts_answer
    (("ruleta", "pleno", "docena", "columna", "rojo", "negro", "par", "impar", "regla", "como se juega", "como funciona", "fichas"),
     "Ruleta europea: 37 casillas (0 al 36). Tienes 15 segundos para colocar fichas de 10, 50, 100 o 500 créditos en "
     "un pleno, color, par/impar, docena o columna; luego el servidor gira la ruleta. Si sale 0, pierden todas las "
     "apuestas externas. Cada sala tiene un mínimo por apuesta y un máximo por ronda."),
    (("hola", "buenas", "buenos dias", "buenas tardes", "ayuda", "que puedes hacer"),
     "¡Hola! Soy el asistente de PalaceTec. Te explico reglas, pagos, probabilidades, límites y cómo usar tu cuenta. "
     "No doy estrategias para ganar: los resultados son aleatorios."),
]


def faq_answer(user, message):
    text = normalize(message)
    if has(text, "saldo", "credito", "billetera", "bono", "cuanto tengo"):
        return balance_answer(user)
    if has(text, "limite", "cupo", "cuanto puedo apostar", "cuanto me queda"):
        return limit_answer(user)
    for keywords, answer in TOPICS:
        if any(f" {keyword} " in f" {text} " or (len(keyword) > 4 and keyword in text) for keyword in keywords):
            return answer if answer is not None else payouts_answer()
    return FALLBACK


def is_account_question(message):
    text = normalize(message)
    return has(text, "saldo", "credito", "billetera", "bono", "cuanto tengo", "limite", "cupo", "cuanto puedo apostar",
               "cuanto me queda")
