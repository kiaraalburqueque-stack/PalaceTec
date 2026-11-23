from django.contrib import admin

from .models import BlackjackHand, Play, SportEvent, SportTicket


@admin.register(Play)
class PlayAdmin(admin.ModelAdmin):
    list_display = ("created_at", "user", "kind", "amount", "payout", "settled_at", "reference")
    list_filter = ("kind", "settled_at", "created_at")
    search_fields = ("user__email", "reference", "description")
    readonly_fields = tuple(field.name for field in Play._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False


@admin.register(BlackjackHand)
class BlackjackHandAdmin(admin.ModelAdmin):
    list_display = ("play", "user", "active", "outcome", "revision", "last_action_at")
    list_filter = ("active", "outcome")
    search_fields = ("user__email", "play__reference")
    readonly_fields = tuple(field.name for field in BlackjackHand._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False


@admin.register(SportEvent)
class SportEventAdmin(admin.ModelAdmin):
    list_display = ("home", "away", "starts_at", "ends_at", "home_score", "away_score", "settled_at")
    list_filter = ("starts_at", "settled_at")
    search_fields = ("home", "away")
    readonly_fields = ("settled_at",)


@admin.register(SportTicket)
class SportTicketAdmin(admin.ModelAdmin):
    list_display = ("play", "event", "selection", "odds")
    list_filter = ("selection",)
    search_fields = ("play__user__email", "event__home", "event__away", "play__reference")
    readonly_fields = tuple(field.name for field in SportTicket._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False
