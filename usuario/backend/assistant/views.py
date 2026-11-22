import math

from django.conf import settings
from rest_framework import serializers
from rest_framework.exceptions import APIException
from rest_framework.response import Response
from rest_framework.throttling import UserRateThrottle

from roulette.views import AuthenticatedView
from .guard import RESPONSIBLE_REMINDER
from .models import AssistantMessage
from .services import answer

HISTORY_LIMIT = 30


class AssistantRateThrottle(UserRateThrottle):
    """HU68: hasta 20 mensajes por minuto por jugador."""
    scope = "assistant"

    def get_rate(self):
        return settings.ASSISTANT_RATE_LIMIT


class AssistantThrottled(APIException):
    status_code = 429

    def __init__(self, wait):
        # El manejador de DRF agrega la cabecera Retry-After con este valor.
        self.wait = math.ceil(wait) if wait else None
        super().__init__({"code": "assistant_rate_limited",
                          "detail": "Enviaste muchos mensajes seguidos. Espera un momento y vuelve a intentarlo."})


class ChatInput(serializers.Serializer):
    message = serializers.CharField(min_length=1, max_length=500, trim_whitespace=True)


class AssistantChatView(AuthenticatedView):
    """HU68: GET devuelve el historial reciente y POST envía una pregunta al asistente."""

    def get_throttles(self):
        return [AssistantRateThrottle()] if self.request.method == "POST" else []

    def throttled(self, request, wait):
        raise AssistantThrottled(wait)

    def get(self, request):
        messages = AssistantMessage.objects.filter(user=request.user).order_by("-created_at", "-id")[:HISTORY_LIMIT]
        return Response({"messages": [message.as_dict() for message in reversed(messages)], "reminder": RESPONSIBLE_REMINDER})

    def post(self, request):
        data = ChatInput(data=request.data)
        data.is_valid(raise_exception=True)
        question, reply, reminder = answer(request.user, data.validated_data["message"])
        return Response({"question": question.as_dict(), "reply": reply.as_dict(), "reminder": reminder}, status=201)
