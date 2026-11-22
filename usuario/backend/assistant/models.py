from django.conf import settings
from django.db import models


class AssistantMessage(models.Model):
    """HU68: historial de la conversación del jugador con el asistente virtual."""

    class Role(models.TextChoices):
        USER = "USER", "Jugador"
        ASSISTANT = "ASSISTANT", "Asistente"

    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="assistant_messages")
    role = models.CharField(max_length=9, choices=Role.choices)
    content = models.TextField(max_length=4000)
    # faq, ai, guard u odds: de dónde salió la respuesta.
    source = models.CharField(max_length=10, blank=True)
    # Datos estructurados de la respuesta (por ejemplo la tarjeta de probabilidades de la HU69).
    data = models.JSONField(default=dict, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "assistant_messages"
        ordering = ["created_at", "id"]
        indexes = [models.Index(fields=["user", "created_at"], name="assistant_user_created")]

    def as_dict(self):
        return {"id": self.pk, "role": self.role, "content": self.content, "source": self.source,
                "data": self.data, "created_at": self.created_at.isoformat()}
