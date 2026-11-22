"""Catálogo del jugador: juegos disponibles, filtro por categoría (HU61) y búsqueda por nombre (HU62)."""
import unicodedata

from django.db.models import Max, Min, Q
from rest_framework import serializers

from .models import FavoriteGame, Game

# Slug de la URL -> categoría guardada en Game.category.
CATEGORY_SLUGS = {
    "ruleta": Game.Category.RULETA,
    "cartas": Game.Category.CARTAS,
    "deportes": Game.Category.DEPORTES,
    "otros": Game.Category.OTROS,
}
SLUG_BY_CATEGORY = {value: key for key, value in CATEGORY_SLUGS.items()}


class CatalogQuery(serializers.Serializer):
    category = serializers.CharField(required=False, allow_blank=True, max_length=20)
    search = serializers.CharField(required=False, allow_blank=True, max_length=80, trim_whitespace=True)

    def validate_category(self, value):
        value = value.strip().lower()
        if value and value not in CATEGORY_SLUGS:
            raise serializers.ValidationError("Categoría no válida. Usa ruleta, cartas, deportes u otros.")
        return value


def available_games():
    """Juegos publicados y activos: con al menos una sala activa o enlazados a su propia pantalla."""
    return (Game.objects.filter(is_published=True, deleted_at__isnull=True)
            .filter(Q(rooms__is_active=True) | ~Q(play_kind=""))
            .annotate(room_min=Min("rooms__min_bet", filter=Q(rooms__is_active=True)),
                      room_max=Max("rooms__max_bet", filter=Q(rooms__is_active=True)))
            .order_by("id"))


def filter_category(games, slug):
    return games.filter(category=CATEGORY_SLUGS[slug]) if slug else games


def normalize(text):
    """HU62: minúsculas y sin tildes ni diéresis ("Pequeña" y "PEQUENA" dan "pequena").

    Se hace en Python para que SQLite y PostgreSQL den el mismo resultado sin la extensión unaccent.
    """
    decomposed = unicodedata.normalize("NFKD", text or "")
    plain = "".join(char for char in decomposed if not unicodedata.combining(char))
    return " ".join(plain.casefold().split())


def apply_search(games, search):
    """Filtra por título; el catálogo es pequeño, así que basta recorrerlo en memoria."""
    term = normalize(search)
    return [game for game in games if term in normalize(game.title)] if term else list(games)


def category_counts(games):
    """Cantidad de juegos por categoría, incluidas las que no tienen ninguno."""
    counts = {slug: 0 for slug in CATEGORY_SLUGS}
    for game in games:
        counts[SLUG_BY_CATEGORY.get(game.category, "otros")] += 1
    return {"total": sum(counts.values()),
            "categories": [{"slug": slug, "label": CATEGORY_SLUGS[slug].label, "count": counts[slug]}
                           for slug in CATEGORY_SLUGS]}


def favorite_ids(user):
    """HU63: IDs de los juegos favoritos del jugador autenticado."""
    return set(FavoriteGame.objects.filter(user=user).values_list("game_id", flat=True))


def game_payload(game, request, favorites=frozenset()):
    room_game = game.room_min is not None
    return {"id": game.pk, "slug": game.slug, "title": game.title, "category": game.category,
            "category_slug": SLUG_BY_CATEGORY.get(game.category, "otros"),
            "rtp": format(game.rtp, ".2f"),
            "cover_url": request.build_absolute_uri(game.cover.url) if game.cover else None,
            "min_bet": format(game.room_min if room_game else game.min_bet, ".2f"),
            "max_bet": format(game.room_max if room_game else game.max_bet, ".2f"),
            "rooms_url": f"/api/v1/rooms?game={game.pk}" if room_game else None,
            "play_path": game.play_path or None,
            "is_favorite": game.pk in favorites}
