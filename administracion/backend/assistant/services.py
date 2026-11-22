"""HU68: decide de dónde sale cada respuesta del asistente y guarda la conversación."""
import logging

from django.db import transaction

from . import providers
from .faq import faq_answer, is_account_question
from .guard import GUARD_REPLY, RESPONSIBLE_REMINDER, asks_for_strategy
from .models import AssistantMessage
from .odds import compute_odds, odds_text, parse_bet

logger = logging.getLogger(__name__)
HISTORY_FOR_AI = 6
MAX_REPLY = 2000


def recent_history(user):
    messages = AssistantMessage.objects.filter(user=user).order_by("-created_at", "-id")[:HISTORY_FOR_AI]
    return [(message.role, message.content) for message in reversed(messages)]


def build_reply(user, message):
    """Devuelve (texto, origen, datos).

    Orden: filtro de seguridad, probabilidades (HU69), datos de la cuenta, proveedor de IA y FAQ.
    """
    if asks_for_strategy(message):
        return GUARD_REPLY, "guard", {}
    bet = parse_bet(message)
    if bet:
        # HU69: el backend calcula la probabilidad; el modelo de IA nunca la inventa.
        odds = compute_odds(*bet)
        return f"{odds_text(odds)} {RESPONSIBLE_REMINDER}", "odds", {"odds": odds}
    if not is_account_question(message):
        provider = providers.get_provider()
        if provider is not None:
            try:
                text = provider.complete(providers.SYSTEM_PROMPT + providers.catalog_context(), recent_history(user), message)
            except providers.ProviderError as exc:
                logger.warning("El proveedor de IA %s falló; se responde con preguntas frecuentes: %s", provider.name, exc)
            else:
                if text and not asks_for_strategy(text):
                    return text[:MAX_REPLY], "ai", {}
                if text:
                    return GUARD_REPLY, "guard", {}
    return faq_answer(user, message), "faq", {}


def answer(user, message):
    text, source, data = build_reply(user, message)
    with transaction.atomic():
        question = AssistantMessage.objects.create(user=user, role=AssistantMessage.Role.USER, content=message)
        reply = AssistantMessage.objects.create(user=user, role=AssistantMessage.Role.ASSISTANT, content=text,
                                                source=source, data=data)
    return question, reply, RESPONSIBLE_REMINDER
