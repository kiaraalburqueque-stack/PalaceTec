"""HU75: limpieza automática de notificaciones leídas de más de 30 días."""
from datetime import timedelta
from io import StringIO

from django.core.management import CommandError, call_command
from django.test import TestCase
from django.utils import timezone

from accounts.models import User
from notifications.models import Notification


class HU75CleanupTests(TestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="hu75@palacetec.pe", dni="75757575", birth_date="2000-01-01")
        now = timezone.now()
        self.old_read = self.make("Leída antigua", now - timedelta(days=45), read=True)
        self.limit_read = self.make("Leída de 31 días", now - timedelta(days=31), read=True)
        self.old_unread = self.make("No leída antigua", now - timedelta(days=90), read=False)
        self.recent_read = self.make("Leída reciente", now - timedelta(days=5), read=True)
        self.recent_unread = self.make("No leída reciente", now - timedelta(days=1), read=False)

    def make(self, title, created_at, read):
        notification = Notification.objects.create(user=self.user, category="GAME", title=title, message="Mensaje")
        Notification.objects.filter(pk=notification.pk).update(
            created_at=created_at, read_at=created_at + timedelta(hours=1) if read else None)
        return notification

    def run_command(self, *args):
        out = StringIO()
        with self.assertLogs("notifications.cleanup", level="INFO") as logs:
            call_command("cleanup_notifications", *args, stdout=out)
        return out.getvalue(), logs.output

    def remaining(self):
        return set(Notification.objects.values_list("title", flat=True))

    def test_hu75_deletes_only_read_notifications_older_than_30_days(self):
        output, logs = self.run_command()
        self.assertEqual(self.remaining(), {"No leída antigua", "Leída reciente", "No leída reciente"})
        self.assertIn("Se eliminaron 2 notificaciones", output)
        self.assertIn("Se eliminaron 2 notificaciones", logs[0])

    def test_hu75_never_deletes_unread_or_recent_ones(self):
        self.run_command("--days", "1")
        self.assertIn("No leída antigua", self.remaining())
        self.assertIn("No leída reciente", self.remaining())
        self.assertNotIn("Leída reciente", self.remaining())

    def test_hu75_dry_run_only_counts(self):
        output, _ = self.run_command("--dry-run")
        self.assertIn("[dry-run] Se eliminarían 2 notificaciones", output)
        self.assertEqual(Notification.objects.count(), 5)

    def test_hu75_running_twice_deletes_nothing_more(self):
        self.run_command()
        output, _ = self.run_command()
        self.assertIn("Se eliminaron 0 notificaciones", output)

    def test_hu75_rejects_invalid_days(self):
        with self.assertRaises(CommandError):
            call_command("cleanup_notifications", "--days", "0", stdout=StringIO())

    def test_hu75_index_for_cleanup_exists(self):
        self.assertIn("notification_read_created", [index.name for index in Notification._meta.indexes])
