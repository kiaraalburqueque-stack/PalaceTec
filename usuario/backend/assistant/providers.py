"""HU68: cliente del proveedor de IA configurable desde el .env.

ASSISTANT_PROVIDER = faq (por defecto), gemini u openai. Sin ASSISTANT_API_KEY se usa siempre el modo de
preguntas frecuentes. Las probabilidades nunca las calcula el modelo: salen de assistant/odds.py.
"""
import json
import logging
import urllib.request

from django.conf import settings

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "Eres el asistente virtual de PalaceTec, un casino de práctica con créditos virtuales (sin dinero real). "
    "Responde en español, en pocas frases, solo sobre las reglas y pagos de sus juegos, los límites de juego "
    "responsable y el uso de la cuenta. Nunca des estrategias, trucos ni consejos para ganar, recuperar pérdidas o "
    "apostar más; recuerda que cada resultado es aleatorio. No inventes probabilidades ni datos de la cuenta del "
    "jugador: si te los piden, indica que el asistente los calcula aparte. Usa solo esta información del catálogo:\n"
)


class ProviderError(Exception):
    pass


def catalog_context():
    """Reglas y tablas de pago de los juegos publicados, como contexto para el modelo."""
    from roulette.catalog import available_games
    blocks = []
    for game in available_games():
        rows = "; ".join(f"{row.get('bet')}: paga {row.get('pays')}" for row in game.payout_table or [])
        blocks.append(f"- {game.title} (RTP {game.rtp} %). {game.rules} Pagos: {rows}")
    return "\n".join(blocks)


def post_json(url, payload, headers):
    request = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST",
                                     headers={"Content-Type": "application/json", **headers})
    try:
        with urllib.request.urlopen(request, timeout=settings.ASSISTANT_TIMEOUT) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as exc:  # red, tiempo de espera, HTTP o JSON inválido
        raise ProviderError(str(exc)) from exc


class GeminiProvider:
    name = "gemini"
    default_model = "gemini-2.0-flash"

    def __init__(self, api_key, model=""):
        self.api_key, self.model = api_key, model or self.default_model

    def complete(self, system, history, message):
        contents = [{"role": "model" if role == "ASSISTANT" else "user", "parts": [{"text": text}]} for role, text in history]
        contents.append({"role": "user", "parts": [{"text": message}]})
        data = post_json(f"https://generativelanguage.googleapis.com/v1beta/models/{self.model}:generateContent",
                         {"system_instruction": {"parts": [{"text": system}]}, "contents": contents,
                          "generationConfig": {"maxOutputTokens": 400, "temperature": 0.3}},
                         {"x-goog-api-key": self.api_key})
        try:
            return "".join(part.get("text", "") for part in data["candidates"][0]["content"]["parts"]).strip()
        except (KeyError, IndexError, TypeError) as exc:
            raise ProviderError("Respuesta inesperada de Gemini.") from exc


class OpenAIProvider:
    name = "openai"
    default_model = "gpt-4o-mini"

    def __init__(self, api_key, model="", base_url=""):
        self.api_key, self.model = api_key, model or self.default_model
        self.base_url = (base_url or "https://api.openai.com/v1").rstrip("/")

    def complete(self, system, history, message):
        messages = [{"role": "system", "content": system}]
        messages += [{"role": "assistant" if role == "ASSISTANT" else "user", "content": text} for role, text in history]
        messages.append({"role": "user", "content": message})
        data = post_json(f"{self.base_url}/chat/completions",
                         {"model": self.model, "messages": messages, "max_tokens": 400, "temperature": 0.3},
                         {"Authorization": f"Bearer {self.api_key}"})
        try:
            return data["choices"][0]["message"]["content"].strip()
        except (KeyError, IndexError, TypeError, AttributeError) as exc:
            raise ProviderError("Respuesta inesperada de OpenAI.") from exc


def get_provider():
    """Proveedor configurado o None para usar las preguntas frecuentes."""
    name = (settings.ASSISTANT_PROVIDER or "faq").lower()
    key = settings.ASSISTANT_API_KEY
    if not key or name == "faq":
        return None
    if name == "gemini":
        return GeminiProvider(key, settings.ASSISTANT_MODEL)
    if name == "openai":
        return OpenAIProvider(key, settings.ASSISTANT_MODEL, settings.ASSISTANT_BASE_URL)
    logger.warning("ASSISTANT_PROVIDER=%s no es válido; se usan las preguntas frecuentes.", name)
    return None
