from django.contrib import admin

from .models import Bet, Game, GameRound, RepeatOperation, Room, RoomConnection


@admin.register(Game)
class GameAdmin(admin.ModelAdmin):
    list_display = ("title", "slug", "category", "is_published", "min_bet", "max_bet", "rtp", "deleted_at")
    list_filter = ("category", "is_published", "deleted_at")
    search_fields = ("title", "slug", "rules")
    readonly_fields = ("created_at", "deleted_at")
    fieldsets = (("Catálogo", {"fields": ("title", "slug", "category", "is_published", "cover")} ),
                 ("Parámetros", {"fields": ("min_bet", "max_bet", "rtp", "rules", "payout_table")} ),
                 ("Registro", {"fields": ("created_at", "deleted_at")} ))


@admin.register(Room)
class RoomAdmin(admin.ModelAdmin):
    list_display = ("name", "game", "is_active", "min_bet", "max_bet")
    list_filter = ("is_active", "game")
    search_fields = ("name", "game__title")


@admin.register(GameRound)
class GameRoundAdmin(admin.ModelAdmin):
    list_display = ("id", "room", "status", "opened_at", "closes_at", "winning_number", "settled_at")
    list_filter = ("status", "room")
    search_fields = ("room__name",)
    readonly_fields = tuple(field.name for field in GameRound._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False


@admin.register(Bet)
class BetAdmin(admin.ModelAdmin):
    list_display = ("created_at", "user", "round", "kind", "selection", "amount", "payout", "reference")
    list_filter = ("kind", "created_at", "round__room")
    search_fields = ("user__email", "reference", "round__room__name")
    readonly_fields = tuple(field.name for field in Bet._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False


@admin.register(RepeatOperation)
class RepeatOperationAdmin(admin.ModelAdmin):
    list_display = ("created_at", "user", "room", "source_round", "target_round", "reference")
    list_filter = ("created_at", "room")
    search_fields = ("user__email", "reference")
    readonly_fields = tuple(field.name for field in RepeatOperation._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False


@admin.register(RoomConnection)
class RoomConnectionAdmin(admin.ModelAdmin):
    list_display = ("room", "user", "channel_name", "expires_at")
    list_filter = ("room", "expires_at")
    search_fields = ("user__email", "channel_name")
    readonly_fields = tuple(field.name for field in RoomConnection._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False
