from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as DjangoUserAdmin

from .models import AuthSession, LoginHistory, PasswordResetToken, User


@admin.register(User)
class UserAdmin(DjangoUserAdmin):
    ordering = ("email",)
    list_display = ("email", "nickname", "first_name", "last_name", "is_active", "is_staff", "created_at")
    list_filter = ("is_active", "is_staff", "is_superuser", "created_at")
    search_fields = ("email", "dni", "nickname", "first_name", "last_name")
    readonly_fields = ("created_at", "last_login")
    fieldsets = (
        (None, {"fields": ("email", "password")} ),
        ("Datos personales", {"fields": ("first_name", "last_name", "nickname", "dni", "birth_date", "phone", "avatar_url")} ),
        ("Estado y permisos", {"fields": ("is_active", "is_staff", "is_superuser", "groups", "user_permissions")} ),
        ("Juego responsable", {"fields": ("self_excluded_until", "pause_until")} ),
        ("Seguridad", {"fields": ("failed_attempts", "locked_until", "auth_version", "last_login", "created_at")} ),
    )
    add_fieldsets = ((None, {"classes": ("wide",), "fields": ("email", "dni", "birth_date", "password1", "password2", "is_active", "is_staff")} ),)
    filter_horizontal = ("groups", "user_permissions")


@admin.register(AuthSession)
class AuthSessionAdmin(admin.ModelAdmin):
    list_display = ("user", "started_at", "expires_at", "refresh_expires_at", "revoked_at")
    list_filter = ("revoked_at", "expires_at")
    search_fields = ("user__email", "jti")
    readonly_fields = ("user", "jti", "started_at", "expires_at", "refresh_expires_at", "revoked_at")
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False


@admin.register(LoginHistory)
class LoginHistoryAdmin(admin.ModelAdmin):
    list_display = ("created_at", "user", "ip_address", "is_new_ip", "is_new_device", "alert_sent", "reported_at")
    list_filter = ("is_new_ip", "is_new_device", "alert_sent", "created_at")
    search_fields = ("user__email", "ip_address", "user_agent")
    readonly_fields = tuple(field.name for field in LoginHistory._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False


@admin.register(PasswordResetToken)
class PasswordResetTokenAdmin(admin.ModelAdmin):
    list_display = ("user", "created_at", "expires_at", "used_at")
    list_filter = ("used_at", "expires_at", "created_at")
    search_fields = ("user__email",)
    readonly_fields = tuple(field.name for field in PasswordResetToken._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False
