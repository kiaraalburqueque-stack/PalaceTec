from django.contrib import admin

from .models import AuditLog, SanctionLog


class ReadOnlyAdmin(admin.ModelAdmin):
    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(AuditLog)
class AuditLogAdmin(ReadOnlyAdmin):
    list_display = ("created_at", "admin", "action", "resource", "status_code", "ip_address")
    search_fields = ("admin__email", "action", "resource")


@admin.register(SanctionLog)
class SanctionLogAdmin(ReadOnlyAdmin):
    list_display = ("created_at", "admin", "user", "action", "reason")
    search_fields = ("user__email", "admin__email", "reason")
