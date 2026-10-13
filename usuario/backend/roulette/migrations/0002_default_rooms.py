from django.db import migrations


def seed_rooms(apps, schema_editor):
    Room = apps.get_model("roulette", "Room")
    for name, minimum, maximum in (("Ruleta baja", 10, 500), ("Ruleta media", 50, 2000), ("Ruleta alta", 100, 5000)):
        Room.objects.using(schema_editor.connection.alias).create(name=name, min_bet=minimum, max_bet=maximum)


class Migration(migrations.Migration):
    dependencies = [("roulette", "0001_initial")]
    operations = [migrations.RunPython(seed_rooms, migrations.RunPython.noop)]
