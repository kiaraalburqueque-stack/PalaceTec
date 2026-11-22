"""HU61: agrega blackjack y deportes simulados al catálogo.

Solo crea (o completa) las filas de Game. Las jugadas existentes de casino.Play no se modifican:
se relacionan con su juego por Play.kind = Game.play_kind.
"""
from decimal import Decimal

from django.db import migrations

GAMES = [
    {"slug": "blackjack", "title": "Blackjack 21", "category": "CARTAS", "play_kind": "BLACKJACK",
     "play_path": "/blackjack", "rtp": Decimal("98.00"), "rules": "BLACKJACK_RULES", "payouts": "BLACKJACK_PAYOUTS"},
    {"slug": "deportes-virtuales", "title": "Deportes virtuales", "category": "DEPORTES", "play_kind": "SPORTS",
     "play_path": "/deportes", "rtp": Decimal("96.00"), "rules": "SPORTS_RULES", "payouts": "SPORTS_PAYOUTS"},
]


def seed(apps, schema_editor):
    from roulette import rules
    Game = apps.get_model("roulette", "Game")
    manager = Game.objects.using(schema_editor.connection.alias)
    for data in GAMES:
        game, created = manager.get_or_create(slug=data["slug"], defaults={
            "title": data["title"], "category": data["category"], "is_published": True,
            "min_bet": Decimal("10"), "max_bet": Decimal("500"), "rtp": data["rtp"],
            "play_kind": data["play_kind"], "play_path": data["play_path"],
            "rules": getattr(rules, data["rules"]), "payout_table": getattr(rules, data["payouts"])})
        if not created:
            # Si el administrador ya creó un juego con ese slug, solo se enlaza con su pantalla.
            game.play_kind, game.play_path = data["play_kind"], data["play_path"]
            game.save(update_fields=["play_kind", "play_path"])


def unseed(apps, schema_editor):
    Game = apps.get_model("roulette", "Game")
    Game.objects.using(schema_editor.connection.alias).filter(play_kind__in=["BLACKJACK", "SPORTS"]).update(
        play_kind="", play_path="")


class Migration(migrations.Migration):
    dependencies = [("roulette", "0011_game_play_link_and_category_index")]
    operations = [migrations.RunPython(seed, unseed)]
