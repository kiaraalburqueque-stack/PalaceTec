from decimal import Decimal

from rest_framework import serializers

from accounts.models import User
from roulette.models import Game


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


COVER_SIGNATURES = {
    "image/jpeg": (b"\xff\xd8\xff",),
    "image/png": (b"\x89PNG\r\n\x1a\n",),
    "image/webp": (b"RIFF",),
}
COVER_EXTENSIONS = {"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png", "webp": "image/webp"}


def validate_cover(upload):
    """HU43: solo JPG, PNG o WEBP de hasta 2 MB; se revisa la firma real del archivo."""
    from django.conf import settings
    extension = upload.name.rsplit(".", 1)[-1].lower() if "." in upload.name else ""
    kind = COVER_EXTENSIONS.get(extension)
    if kind is None:
        raise serializers.ValidationError("La portada debe ser JPG, PNG o WEBP.")
    if upload.size > settings.GAME_COVER_MAX_BYTES:
        raise serializers.ValidationError("La portada no debe superar 2 MB.")
    head = upload.read(12)
    upload.seek(0)
    valid = any(head.startswith(signature) for signature in COVER_SIGNATURES[kind])
    if kind == "image/webp":
        valid = valid and head[8:12] == b"WEBP"
    if not valid:
        raise serializers.ValidationError("El contenido del archivo no corresponde a una imagen válida.")
    return upload


class AdminGameSerializer(serializers.ModelSerializer):
    cover = serializers.FileField(required=False, allow_null=True, validators=[validate_cover], write_only=True)
    cover_url = serializers.SerializerMethodField()
    min_bet = serializers.DecimalField(max_digits=12, decimal_places=2, min_value=Decimal("1.00"))
    max_bet = serializers.DecimalField(max_digits=12, decimal_places=2, min_value=Decimal("1.00"))
    rtp = serializers.DecimalField(max_digits=5, decimal_places=2, min_value=Decimal("80.00"), max_value=Decimal("99.99"))
    title = serializers.CharField(min_length=3, max_length=80)
    slug = serializers.SlugField(max_length=50)
    is_published = serializers.BooleanField(required=False, default=True)
    rooms = serializers.SerializerMethodField()

    class Meta:
        model = Game
        fields = ["id", "slug", "title", "category", "min_bet", "max_bet", "rtp", "is_published",
                  "cover", "cover_url", "rooms", "rules", "payout_table", "created_at", "deleted_at"]
        read_only_fields = ["id", "created_at", "deleted_at"]

    def get_cover_url(self, game):
        if not game.cover:
            return None
        request = self.context.get("request")
        return request.build_absolute_uri(game.cover.url) if request else game.cover.url

    def get_rooms(self, game):
        return game.rooms.count()

    def validate_payout_table(self, value):
        if not isinstance(value, list) or len(value) > 30 or not all(
                isinstance(row, dict) and str(row.get("bet", "")).strip() and str(row.get("pays", "")).strip() for row in value):
            raise serializers.ValidationError("Cada fila de la tabla de pagos necesita 'bet' y 'pays'.")
        return [{key: str(row.get(key, ""))[:80] for key in ("bet", "description", "pays", "probability")} for row in value]

    def create(self, validated_data):
        from roulette.rules import EUROPEAN_ROULETTE_PAYOUTS, EUROPEAN_ROULETTE_RULES
        if validated_data.get("category", Game.Category.RULETA) == Game.Category.RULETA:
            validated_data.setdefault("rules", EUROPEAN_ROULETTE_RULES)
            if not validated_data.get("payout_table"):
                validated_data["payout_table"] = EUROPEAN_ROULETTE_PAYOUTS
        return super().create(validated_data)

    def validate_slug(self, value):
        duplicated = Game.objects.filter(slug=value)
        if self.instance:
            duplicated = duplicated.exclude(pk=self.instance.pk)
        if duplicated.exists():
            raise serializers.ValidationError("Ya existe un juego con este slug.")
        return value

    def validate(self, attrs):
        if self.instance and self.instance.deleted_at and attrs.get("is_published"):
            raise serializers.ValidationError({"is_published": "El juego está desactivado. Restáuralo antes de publicarlo."})
        low = attrs.get("min_bet", getattr(self.instance, "min_bet", None))
        high = attrs.get("max_bet", getattr(self.instance, "max_bet", None))
        if low is not None and high is not None and low > high:
            raise serializers.ValidationError({"min_bet": "La apuesta mínima no puede ser mayor que la máxima."})
        return attrs


class PromotionInput(serializers.Serializer):
    title = serializers.CharField(min_length=3, max_length=120)
    message = serializers.CharField(min_length=5, max_length=500)
    send_email = serializers.BooleanField(required=False, default=False)
