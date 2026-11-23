from django.db import migrations


def create_group(apps, schema_editor):
    Group = apps.get_model("auth", "Group")
    Group.objects.using(schema_editor.connection.alias).get_or_create(name="Auditor")


class Migration(migrations.Migration):
    """HU49: rol de auditor con acceso de solo lectura a las transacciones."""

    dependencies = [("admin_api", "0001_initial"), ("auth", "0012_alter_user_first_name_max_length")]
    operations = [migrations.RunPython(create_group, migrations.RunPython.noop)]
