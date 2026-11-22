from django.contrib import admin

from .models import DailyLimit, DailySpending, ExcludedPerson, RiskIncident, SelfExclusion


@admin.register(SelfExclusion)
class SelfExclusionAdmin(admin.ModelAdmin):
    list_display = ("user", "days", "created_at", "excluded_until")
    list_filter = ("days", "created_at")
    search_fields = ("user__email",)
    readonly_fields = tuple(field.name for field in SelfExclusion._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False


@admin.register(RiskIncident)
class RiskIncidentAdmin(admin.ModelAdmin):
    list_display = ("created_at", "user", "kind", "round", "play", "pause_started_at")
    list_filter = ("kind", "created_at")
    search_fields = ("user__email",)
    readonly_fields = tuple(field.name for field in RiskIncident._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False


@admin.register(DailyLimit)
class DailyLimitAdmin(admin.ModelAdmin):
    list_display = ("user", "amount", "pending_amount", "effective_at")
    list_filter = ("effective_at",)
    search_fields = ("user__email",)


@admin.register(DailySpending)
class DailySpendingAdmin(admin.ModelAdmin):
    list_display = ("date", "user", "amount")
    list_filter = ("date",)
    search_fields = ("user__email",)
    readonly_fields = tuple(field.name for field in DailySpending._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False


@admin.register(ExcludedPerson)
class ExcludedPersonAdmin(admin.ModelAdmin):
    list_display = ("created_at", "source", "dni_hash", "reason")
    list_filter = ("source", "created_at")
    search_fields = ("dni_hash", "source", "reason")
    readonly_fields = tuple(field.name for field in ExcludedPerson._meta.fields)
