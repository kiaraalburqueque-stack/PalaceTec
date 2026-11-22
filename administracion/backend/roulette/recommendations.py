"""HU70: recomendaciones de juegos por reglas, sin modelo entrenado.

Puntaje de cada juego disponible:
- 3 x parte de las rondas del jugador (últimos 30 días) que corresponden a la categoría del juego,
- 2 si el juego está en sus favoritos,
- 1 x popularidad entre todos los jugadores (rondas de 30 días normalizadas contra el más jugado).
Un jugador autoexcluido, en pausa o que ya alcanzó su límite diario no recibe recomendaciones.
"""
from datetime import timedelta
from decimal import Decimal

from django.utils import timezone

from casino.models import Play
from .catalog import available_games, favorite_ids, game_payload
from .game_activity import activity_by_game
from .models import Bet, Room

LIMIT = 4
WINDOW_DAYS = 30
WEIGHTS = {"category": 3, "favorite": 2, "popularity": 1}


def blocked_reason(user, now=None):
    """Motivo por el que no se recomienda seguir jugando, o None."""
    from responsible.models import DailyLimit, DailySpending
    from responsible.services import LimitService
    now = now or timezone.now()
    if user.self_excluded_until and user.self_excluded_until > now:
        return "self_excluded"
    if user.pause_until and user.pause_until > now:
        return "pause_active"
    limit = DailyLimit.objects.filter(user=user).first()
    if limit is not None and limit.amount is not None:
        amount = limit.amount
        if limit.effective_at and limit.effective_at <= now and limit.pending_amount is not None:
            amount = limit.pending_amount  # aumento ya vigente aunque aún no se haya guardado
        spent = DailySpending.objects.filter(user=user, date=LimitService.day(now)).values_list("amount", flat=True).first()
        if (spent or Decimal("0")) >= amount:
            return "daily_limit_reached"
    return None


def recommend(user, request, now=None):
    now = now or timezone.now()
    reason = blocked_reason(user, now)
    if reason:
        return {"results": [], "blocked": reason, "personalized": False}
    since = now - timedelta(days=WINDOW_DAYS)
    games = list(available_games())
    if not games:
        return {"results": [], "blocked": None, "personalized": False}
    by_id = {game.pk: game for game in games}
    recent_bets = Bet.objects.filter(created_at__gte=since)
    recent_plays = Play.objects.filter(created_at__gte=since)
    everyone = activity_by_game(recent_bets, recent_plays)
    mine = activity_by_game(recent_bets.filter(user=user), recent_plays.filter(user=user))
    favorites = favorite_ids(user)

    category_rounds, top_game = {}, {}
    for game_id, item in mine.items():
        game = by_id.get(game_id)
        if game is None:
            continue
        category_rounds[game.category] = category_rounds.get(game.category, 0) + item.rounds
        best = top_game.get(game.category)
        if best is None or item.rounds > mine[best].rounds:
            top_game[game.category] = game_id
    my_total = sum(category_rounds.values())
    most_popular = max((item.rounds for gid, item in everyone.items() if gid in by_id), default=0)

    scored = []
    for game in games:
        parts = {
            "category": WEIGHTS["category"] * (category_rounds.get(game.category, 0) / my_total if my_total else 0),
            "favorite": WEIGHTS["favorite"] if game.pk in favorites else 0,
            "popularity": WEIGHTS["popularity"] * (everyone[game.pk].rounds / most_popular
                                                   if most_popular and game.pk in everyone else 0),
        }
        scored.append((sum(parts.values()), parts, game))
    scored.sort(key=lambda item: (-item[0], -(everyone[item[2].pk].rounds if item[2].pk in everyone else 0), item[2].pk))

    first_rooms = dict(Room.objects.available().filter(game_id__in=by_id).order_by("-id").values_list("game_id", "id"))
    results = []
    for score, parts, game in scored[:LIMIT]:
        main = max(parts, key=parts.get) if score > 0 else "popularity"
        if main == "category":
            played = by_id[top_game[game.category]].title
            code, text = "category", f"Porque jugaste {played}"
        elif main == "favorite":
            code, text = "favorite", "Está en tus favoritos"
        else:
            code, text = "popular", "Popular en PalaceTec"
        results.append({**game_payload(game, request, favorites), "play_room_id": first_rooms.get(game.pk),
                        "reason": text, "reason_code": code, "score": round(score, 3)})
    return {"results": results, "blocked": None, "personalized": bool(my_total or favorites & by_id.keys())}
