from decimal import Decimal

from rest_framework import serializers

from accounts.models import User


class AdminUserSerializer(serializers.ModelSerializer):
    role = serializers.SerializerMethodField()
    status = serializers.SerializerMethodField()
    balance = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = ["id", "email", "dni", "nickname", "first_name", "last_name", "role", "status",
                  "is_active", "balance", "created_at"]

    def get_role(self, user):
        return "ADMIN" if user.is_staff or user.is_superuser else "USER"

    def get_status(self, user):
        return "ACTIVE" if user.is_active else "SUSPENDED"

    def get_balance(self, user):
        wallet = getattr(user, "wallet", None)
        return format(wallet.balance if wallet else Decimal("0"), ".2f")


class UserListQuery(serializers.Serializer):
    status = serializers.ChoiceField(choices=["active", "suspended"], required=False)
    role = serializers.ChoiceField(choices=["admin", "user"], required=False)
    search = serializers.CharField(required=False, allow_blank=True, max_length=254)
    ordering = serializers.ChoiceField(required=False, default="-created_at", choices=[
        "email", "-email", "created_at", "-created_at", "dni", "-dni", "id", "-id"])


class UserStatusInput(serializers.Serializer):
    is_active = serializers.BooleanField()
    reason = serializers.CharField(max_length=255, trim_whitespace=True)

    def validate_reason(self, value):
        if len(value.strip()) < 5:
            raise serializers.ValidationError("Describe el motivo de la sanción (mínimo 5 caracteres).")
        return value.strip()
