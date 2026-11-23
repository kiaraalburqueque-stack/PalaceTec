"""Actividad por juego del catálogo: rondas jugadas, créditos apostados y jugadores (HU67, HU70, HU71 y HU73).

Une las dos tablas de jugadas:
- roulette.Bet se liga a su juego por la sala (round -> room -> game). Varias fichas de un jugador en la
  misma ronda cuentan como una sola ronda jugada.
- casino.Play (blackjack y deportes) se liga a su juego por Play.kind = Game.play_kind; cada jugada es una ronda.
"""
from dataclasses import dataclass, field
from decimal import Decimal

from django.db.models import Count, Sum

from .models import Game


@dataclass
class GameActivity:
    game_id: int
    rounds: int = 0
    wagered: Decimal = Decimal("0")
    players: set = field(default_factory=set)


def play_kind_games():
    """Play.kind -> id del juego enlazado (blackjack y deportes-virtuales)."""
    return dict(Game.objects.exclude(play_kind="").values_list("play_kind", "id"))


def default_roulette_game_id():
    """Salas antiguas sin juego asignado se cuentan como Ruleta Europea."""
    return Game.objects.filter(slug="ruleta-europea").values_list("id", flat=True).first()


def activity_by_game(bets, plays):
    """Devuelve {game_id: GameActivity} a partir de querysets de Bet y Play ya filtrados."""
    result = {}

    def entry(game_id):
        return result.setdefault(game_id, GameActivity(game_id))

    fallback = None
    rows = bets.order_by().values("round__room__game_id", "round_id", "user_id").annotate(total=Sum("amount"))
    for row in rows:
        game_id = row["round__room__game_id"]
        if game_id is None:
            fallback = fallback or default_roulette_game_id()
            game_id = fallback
        if game_id is None:
            continue
        item = entry(game_id)
        item.rounds += 1
        item.wagered += row["total"] or Decimal("0")
        item.players.add(row["user_id"])
    kinds = play_kind_games()
    for row in plays.order_by().values("kind", "user_id").annotate(count=Count("id"), total=Sum("amount")):
        game_id = kinds.get(row["kind"])
        if game_id is None:
            continue
        item = entry(game_id)
        item.rounds += row["count"]
        item.wagered += row["total"] or Decimal("0")
        item.players.add(row["user_id"])
    return result
