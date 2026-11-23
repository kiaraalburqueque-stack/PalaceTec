from django.contrib import admin

from .models import Notification, NotificationSetting


@admin.register(Notification)
class NotificationAdmin(admin.ModelAdmin):
    list_display = ("created_at", "user", "category", "title", "read_at")
    list_filter = ("category", "read_at", "created_at")
    search_fields = ("user__email", "title", "message")
    readonly_fields = ("created_at",)


@admin.register(NotificationSetting)
class NotificationSettingAdmin(admin.ModelAdmin):
    list_display = ("user", "promotions", "security", "game_alerts", "updated_at")
    list_filter = ("promotions", "security", "game_alerts")
    search_fields = ("user__email",)
    readonly_fields = ("updated_at",)
