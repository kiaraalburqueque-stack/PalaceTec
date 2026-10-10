from django.utils import timezone
from rest_framework import serializers

from .models import User


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

    def create(self, validated_data):
        return User.objects.create_user(**validated_data)
