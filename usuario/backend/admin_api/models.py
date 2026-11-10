from django.conf import settings
from django.db import models


class SanctionLog(models.Model):
    """HU42: cada suspensión o activación queda registrada con su motivo."""

    class Action(models.TextChoices):
        SUSPEND = "SUSPEND", "Suspensión"
        ACTIVATE = "ACTIVATE", "Activación"

    admin = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="sanctions_applied")
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="sanctions")
    action = models.CharField(max_length=8, choices=Action.choices)
    reason = models.CharField(max_length=255)
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)

    class Meta:
        db_table = "admin_sanction_logs"
        ordering = ["-created_at", "-id"]
