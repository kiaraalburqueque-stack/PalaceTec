from rest_framework import serializers
from .models import Bet

CHIP_VALUES = (10, 50, 100, 500)


class BetInputSerializer(serializers.Serializer):
    round_id = serializers.IntegerField(min_value=1)
    kind = serializers.ChoiceField(choices=Bet.Kind.choices)
    selection = serializers.CharField(max_length=5, trim_whitespace=False)
    chips = serializers.ListField(child=serializers.IntegerField(), min_length=1, max_length=100)
    reference = serializers.UUIDField()

    def validate_chips(self, values):
        if any(value not in CHIP_VALUES for value in values):
            raise serializers.ValidationError("Fichas autorizadas: 10, 50, 100 y 500.")
        return values

    def validate(self, attrs):
        kind, value = attrs["kind"], attrs["selection"]
        if kind == Bet.Kind.STRAIGHT_UP:
            if value not in {str(number) for number in range(37)}:
                raise serializers.ValidationError({"selection": "El pleno debe ser un numero del 0 al 36."})
        elif kind == Bet.Kind.COLOR and value not in ("rojo", "negro"):
            raise serializers.ValidationError({"selection": "El color debe ser rojo o negro."})
        elif kind == Bet.Kind.PARITY and value not in ("par", "impar"):
            raise serializers.ValidationError({"selection": "La paridad debe ser par o impar."})
        return attrs


class BetSerializer(serializers.ModelSerializer):
    round_id = serializers.IntegerField(read_only=True)

    class Meta:
        model = Bet
        fields = ["id", "round_id", "kind", "selection", "chips", "amount", "payout", "reference", "created_at"]
