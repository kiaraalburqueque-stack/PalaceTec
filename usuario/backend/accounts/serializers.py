from decimal import Decimal

from django.db import transaction
from django.utils import timezone

from wallet.models import Wallet, WalletTransaction
from rest_framework import serializers

from .models import User


class LoginSerializer(serializers.Serializer):
    email = serializers.EmailField(max_length=254)
    password = serializers.CharField(write_only=True, trim_whitespace=False)


class RegisterSerializer(serializers.ModelSerializer):
    # La vista gestiona duplicados con HTTP 409, no con el validador único de DRF.
    email = serializers.EmailField(max_length=254)
    dni = serializers.RegexField(r"\A[0-9]{8}\Z", trim_whitespace=False)
    password = serializers.CharField(write_only=True, trim_whitespace=False)

    class Meta:
        model = User
        fields = ["id", "email", "password", "dni", "birth_date"]
        read_only_fields = ["id"]

    def validate_password(self, value):
        if len(value) < 8:
            raise serializers.ValidationError("La contraseña debe tener al menos 8 caracteres.")
        if not any(character.isupper() for character in value):
            raise serializers.ValidationError("La contraseña debe incluir una mayúscula.")
        if not any(not character.isalnum() and not character.isspace() for character in value):
            raise serializers.ValidationError("La contraseña debe incluir un símbolo.")
        return value

    def validate_birth_date(self, value):
        today = timezone.localdate()
        age = today.year - value.year - ((today.month, today.day) < (value.month, value.day))
        if age < 18:
            raise serializers.ValidationError("Debes tener al menos 18 años cumplidos.")
        return value

    @transaction.atomic
    def create(self, validated_data):
        user = User.objects.create_user(**validated_data)
        wallet = Wallet.objects.create(user=user, balance=Decimal("1000.00"), bonus_balance=Decimal("1000.00"))
        WalletTransaction.objects.create(
            wallet=wallet, type=WalletTransaction.Type.WELCOME_BONUS,
            amount=Decimal("1000.00"), balance_after=wallet.balance,
        )
        return user


AVATARS = tuple(f"/avatars/avatar-{index}.svg" for index in range(1, 7))


class ProfileSerializer(serializers.ModelSerializer):
    nickname = serializers.RegexField(r"\A[a-zA-Z0-9_]{3,30}\Z", required=False)
    avatar_url = serializers.ChoiceField(choices=AVATARS, required=False)
    phone = serializers.RegexField(r"\A\+?[0-9 ()-]{7,20}\Z", required=False, allow_blank=True)

    class Meta:
        model = User
        fields = ["id", "email", "dni", "nickname", "avatar_url", "first_name", "last_name", "phone"]
        read_only_fields = ["id", "email", "dni"]

    def validate(self, attrs):
        forbidden = {key: "Este campo no es editable." for key in ("email", "dni") if key in self.initial_data}
        if forbidden:
            raise serializers.ValidationError(forbidden)
        for field in ("first_name", "last_name"):
            value = attrs.get(field, "")
            if any(not (char.isalpha() or char in " .'-") for char in value):
                raise serializers.ValidationError({field: "Usa letras y separadores de nombre validos."})
        return attrs

    def validate_nickname(self, value):
        value = value.lower()
        if User.objects.filter(nickname=value).exclude(pk=self.instance.pk).exists():
            raise serializers.ValidationError("Este apodo ya esta registrado.")
        return value
