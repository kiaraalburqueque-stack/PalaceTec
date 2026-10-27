"""Reloj del servidor, independiente de los navegadores conectados."""
import logging
import time
from django.core.management.base import BaseCommand
from django.db import close_old_connections
from django.utils import timezone
from roulette.models import Room, RoomConnection
from roulette.services import RouletteService

logger = logging.getLogger(__name__)


class Command(BaseCommand):
    help = "Procesa rondas de 15 segundos; requiere Redis compartido con ASGI."

    def add_arguments(self, parser):
        parser.add_argument("--once", action="store_true", help="Procesa un solo tick por sala.")

    def handle(self, *args, **options):
        self.stdout.write("Reloj de ruleta iniciado. Ctrl+C para detener.")
        try:
            while True:
                started = time.monotonic()
                close_old_connections()
                RoomConnection.objects.filter(expires_at__lte=timezone.now()).delete()
                for room_id in Room.objects.values_list("id", flat=True):
                    try:
                        RouletteService.tick(room_id)
                    except Exception:
                        logger.exception("Fallo al procesar sala %s; se reintentara sin pagos parciales.", room_id)
                        if options["once"]:
                            raise
                from casino.services import tick_casino
                try:
                    tick_casino()
                except Exception:
                    logger.exception('Fallo al procesar juegos y deportes simulados; se reintentara.')
                    if options['once']:
                        raise
                if options["once"]:
                    return
                time.sleep(max(0, 1-(time.monotonic()-started)))
        except KeyboardInterrupt:
            self.stdout.write("Reloj detenido.")
        finally:
            close_old_connections()
