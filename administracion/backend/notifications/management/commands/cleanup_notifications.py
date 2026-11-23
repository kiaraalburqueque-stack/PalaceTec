"""HU75: borra las notificaciones leídas de más de N días (30 por defecto). Nunca borra las no leídas.

Uso:  python manage.py cleanup_notifications [--days 30] [--dry-run]
Se programa una vez al día con el Programador de tareas de Windows (scripts/register_cleanup_task.ps1) o cron.
"""
import logging
from datetime import timedelta

from django.core.management.base import BaseCommand, CommandError
from django.utils import timezone

from notifications.models import Notification

logger = logging.getLogger("notifications.cleanup")
BATCH_SIZE = 1000


class Command(BaseCommand):
    help = "Borra las notificaciones leídas con más de N días de antigüedad (las no leídas se conservan)."

    def add_arguments(self, parser):
        parser.add_argument("--days", type=int, default=30, help="Antigüedad mínima en días (por defecto 30).")
        parser.add_argument("--dry-run", action="store_true", help="Solo cuenta lo que se borraría, sin borrar.")

    def handle(self, *args, days, dry_run, **options):
        if days < 1:
            raise CommandError("--days debe ser al menos 1.")
        cutoff = timezone.now() - timedelta(days=days)
        old_read = Notification.objects.filter(read_at__isnull=False, created_at__lt=cutoff)
        if dry_run:
            count = old_read.count()
            message = f"[dry-run] Se eliminarían {count} notificaciones leídas anteriores a {cutoff:%Y-%m-%d %H:%M} UTC."
        else:
            count = 0
            # Por lotes, para no bloquear la tabla mucho tiempo si hay muchas filas.
            while True:
                ids = list(old_read.order_by("id").values_list("id", flat=True)[:BATCH_SIZE])
                if not ids:
                    break
                count += Notification.objects.filter(pk__in=ids).delete()[0]
            message = f"Se eliminaron {count} notificaciones leídas anteriores a {cutoff:%Y-%m-%d %H:%M} UTC."
        logger.info(message)
        self.stdout.write(self.style.SUCCESS(message))
        return None
