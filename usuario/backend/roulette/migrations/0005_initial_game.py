from django.db import migrations


def seed(apps, schema_editor):
    Game = apps.get_model('roulette', 'Game')
    Room = apps.get_model('roulette', 'Room')
    game, _ = Game.objects.using(schema_editor.connection.alias).get_or_create(
        slug='ruleta-europea', defaults={'title': 'Ruleta Europea', 'is_published': True})
    Room.objects.using(schema_editor.connection.alias).filter(game__isnull=True).update(game=game)


class Migration(migrations.Migration):
    dependencies = [('roulette', '0004_game_room_game')]
    operations = [migrations.RunPython(seed, migrations.RunPython.noop)]
