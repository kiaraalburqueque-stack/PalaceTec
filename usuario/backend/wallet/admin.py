from django.contrib import admin

from .models import Wallet, WalletTransaction


class WalletTransactionInline(admin.TabularInline):
    model = WalletTransaction
    extra = 0
    can_delete = False
    fields = ("created_at", "type", "amount", "balance_after", "reference")
    readonly_fields = fields


@admin.register(Wallet)
class WalletAdmin(admin.ModelAdmin):
    list_display = ("user", "balance", "bonus_balance", "winnings_balance")
    search_fields = ("user__email", "user__dni")
    readonly_fields = ("user", "balance", "bonus_balance")
    inlines = (WalletTransactionInline,)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False


@admin.register(WalletTransaction)
class WalletTransactionAdmin(admin.ModelAdmin):
    list_display = ("created_at", "wallet", "type", "amount", "balance_after", "reference")
    list_filter = ("type", "created_at")
    search_fields = ("wallet__user__email", "reference")
    readonly_fields = tuple(field.name for field in WalletTransaction._meta.fields)
    def has_add_permission(self, request): return False
    def has_change_permission(self, request, obj=None): return False
