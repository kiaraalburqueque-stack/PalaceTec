from math import ceil
from django.core.cache import cache
from django.utils import timezone
from .constants import RED_NUMBERS, BLACK_NUMBERS
from .models import GameRound, Room
from .serializers import CHIP_VALUES


def round_data(round, now=None):
    now = now or timezone.now()
    return {
        "id": round.pk, "status": round.status,
        "opened_at": round.opened_at.isoformat(), "closes_at": round.closes_at.isoformat(),
        "remaining_seconds": max(0, ceil((round.closes_at - now).total_seconds())) if round.status == "OPEN" else 0,
        "winning_number": round.winning_number,
    }


def room_state(room_id):
    room = Room.objects.get(pk=room_id, is_active=True)
    round = room.rounds.order_by("-id").first()
    state = {
        "room_id": room.pk, "server_time": timezone.now().isoformat(),
        "min_bet": str(room.min_bet), "max_bet": str(room.max_bet),
        "round": round_data(round) if round else None,
        "chip_values": list(CHIP_VALUES),
        "numbers": [{"number": number, "color": "rojo" if number in RED_NUMBERS else "negro" if number in BLACK_NUMBERS else "verde"} for number in range(37)],
    }
    # Cache de presentacion; las decisiones de dinero/plazo siempre consultan la BD.
    cache.set(f"roulette:room:{room_id}", state, timeout=30)
    return state
