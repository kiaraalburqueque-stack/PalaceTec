from uuid import uuid5
from django.db import transaction
from rest_framework import serializers
from accounts.models import User
from accounts.authentication import CookieJWTAuthentication
from wallet.services import ReferenceConflict
from .models import Bet, GameRound, Room, RepeatOperation
from .services import RouletteService, BetsClosed


class RepeatInput(serializers.Serializer):
    round_id = serializers.IntegerField(min_value=1)
    source_round_id = serializers.IntegerField(min_value=1, required=False)
    reference = serializers.UUIDField()


def previous_round(user, room_id):
    return GameRound.objects.filter(room_id=room_id, status="SETTLED", bets__user=user).order_by("-id").first()


@transaction.atomic
def repeat_bets(user, room_id, data, auth_token=None):
    serializer = RepeatInput(data=data)
    serializer.is_valid(raise_exception=True)
    values = serializer.validated_data
    room = Room.objects.available().select_for_update(of=("self",)).get(pk=room_id)
    target = GameRound.objects.select_for_update().filter(pk=values["round_id"], room=room).first()
    user = User.objects.select_for_update().get(pk=user.pk)
    if auth_token is not None:
        CookieJWTAuthentication().get_user(auth_token)
    from responsible.services import ExclusionService
    ExclusionService.ensure_user_allowed(user)
    existing = RepeatOperation.objects.filter(user=user, reference=values["reference"]).first()
    if existing:
        if existing.room_id != room.pk or existing.target_round_id != values["round_id"] or (
                "source_round_id" in values and values["source_round_id"] != existing.source_round_id):
            raise ReferenceConflict("Esta referencia corresponde a otra repeticion.")
        return existing, False
    if target is None:
        raise BetsClosed("Ronda no disponible.")
    source = previous_round(user, room_id)
    if source is None or ("source_round_id" in values and values["source_round_id"] != source.pk):
        raise serializers.ValidationError({"source_round_id": "No existe esa ronda anterior para repetir."})
    operation = RepeatOperation.objects.create(user=user, room=room, source_round=source,
        target_round=target, reference=values["reference"])
    for previous in Bet.objects.filter(user=user, round=source).order_by("id"):
        bet, created = RouletteService.place_bet(user, room_id, {"round_id": target.pk,
            "kind": previous.kind, "selection": previous.selection, "chips": previous.chips,
            "reference": str(uuid5(values["reference"], f"roulette:{user.pk}:{previous.pk}"))}, auth_token)
        if not created:
            raise ReferenceConflict("La referencia de repeticion ya fue utilizada.")
        bet.repeat_operation = operation
        bet.save(update_fields=["repeat_operation"])
    return operation, True
