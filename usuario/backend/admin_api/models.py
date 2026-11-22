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


class ImmutableRecord(Exception):
    pass


class AuditLogQuerySet(models.QuerySet):
    def update(self, **kwargs):
        raise ImmutableRecord("El registro de auditoría no se puede modificar.")

    def delete(self):
        raise ImmutableRecord("El registro de auditoría no se puede eliminar.")


class AuditLog(models.Model):
    """HU50: bitácora de solo inserción de las acciones de modificación de administradores."""

    admin = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="audit_logs")
    action = models.CharField(max_length=120)
    method = models.CharField(max_length=8)
    resource = models.CharField(max_length=255)
    status_code = models.PositiveSmallIntegerField()
    ip_address = models.GenericIPAddressField(null=True, blank=True)
    details = models.JSONField(default=dict, blank=True)
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)

    objects = AuditLogQuerySet.as_manager()

    class Meta:
        db_table = "admin_audit_logs"
        ordering = ["-created_at", "-id"]

    def save(self, *args, **kwargs):
        if self.pk is not None:
            raise ImmutableRecord("El registro de auditoría no se puede modificar.")
        super().save(*args, **kwargs)

    def delete(self, *args, **kwargs):
        raise ImmutableRecord("El registro de auditoría no se puede eliminar.")
