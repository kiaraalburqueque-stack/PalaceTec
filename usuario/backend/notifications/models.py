from django.conf import settings
from django.db import models


class Notification(models.Model):
    """HU57: aviso del centro de notificaciones del jugador."""

    class Category(models.TextChoices):
        PROMOTION = "PROMOTION", "Promoción"
        SECURITY = "SECURITY", "Seguridad"
        GAME = "GAME", "Alerta de juego"

    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="notifications")
    category = models.CharField(max_length=10, choices=Category.choices)
    title = models.CharField(max_length=120)
    message = models.CharField(max_length=500)
    read_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "notifications"
        ordering = ["-created_at", "-id"]
        indexes = [models.Index(fields=["user", "read_at"], name="notification_user_unread")]

    @property
    def is_read(self):
        return self.read_at is not None

    def as_dict(self):
        return {"id": self.pk, "category": self.category, "title": self.title, "message": self.message,
                "is_read": self.is_read, "created_at": self.created_at.isoformat()}
