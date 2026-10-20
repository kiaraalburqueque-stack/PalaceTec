"""Django/Channels real en una BD SQLite temporal, solo para Playwright.

Los controladores de fixtures nunca se incorporan a config.urls ni a producción.
Las rondas de este recorrido son largas y se liquidan explícitamente; el plazo
de 15 segundos y las carreras pertenecen a las pruebas de backend.
"""
import json
import os
import sys
import tempfile
import types
from datetime import timedelta
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "backend"))
os.environ["DJANGO_SETTINGS_MODULE"] = "config.test_settings"
os.environ["DB_ENGINE"] = "sqlite"

import django
django.setup()

from django.conf import settings
from django.core.management import call_command
from django.db import connections
from django.http import JsonResponse
from django.urls import path
from django.utils import timezone
from django.views.decorators.csrf import csrf_exempt
from config.urls import urlpatterns
from roulette.models import GameRound, Room
from roulette.services import RouletteService


def prepare_round(room_id):
    round = RouletteService.tick(room_id)
    if round.status == "OPEN":
        round.closes_at = timezone.now() + timedelta(minutes=10)
        round.save(update_fields=["closes_at"])
    return round


@csrf_exempt
def fixture(request, action):
    if action == "health":
        return JsonResponse({"status": "ok"})
    if request.method != "POST":
        return JsonResponse({"detail": "POST only"}, status=405)
    data = json.loads(request.body or b"{}")
    room_id = Room.objects.order_by("id").first().pk
    if action == "settle":
        current = GameRound.objects.get(room_id=room_id, status="OPEN")
        with patch("roulette.services.timezone.now", return_value=current.closes_at):
            RouletteService.tick(room_id)
        with patch("roulette.services.rng.spin", return_value=12):
            RouletteService.tick(room_id)
        prepare_round(room_id)
    elif action in ("incident", "session-hour"):
        from accounts.models import User, AuthSession
        user = User.objects.get(email=data["email"])
        if action == "incident":
            from responsible.models import RiskIncident
            RiskIncident.objects.get_or_create(user=user, round=GameRound.objects.filter(room_id=room_id).latest("id"))
        else:
            AuthSession.objects.filter(user=user, revoked_at__isnull=True).update(started_at=timezone.now() - timedelta(seconds=3601))
    else:
        return JsonResponse({"detail": "Unknown fixture"}, status=404)
    return JsonResponse({"status": "ok"})


with tempfile.TemporaryDirectory(prefix="palacetec-e2e-") as directory:
    settings.DATABASES["default"]["NAME"] = str(Path(directory) / "browser.sqlite3")
    settings.DATABASES["default"]["OPTIONS"] = {"timeout": 30, "transaction_mode": "IMMEDIATE"}
    settings.ALLOWED_HOSTS = ["localhost", "127.0.0.1"]
    module = types.ModuleType("palacetec_e2e_urls")
    module.urlpatterns = [path("__e2e__/<str:action>", fixture), *urlpatterns]
    sys.modules[module.__name__] = module
    settings.ROOT_URLCONF = module.__name__
    call_command("migrate", verbosity=0)
    for room in Room.objects.available():
        prepare_round(room.pk)
    connections.close_all()
    from config.asgi import application
    from daphne.server import Server
    try:
        Server(application=application, endpoints=["tcp:port=8010:interface=127.0.0.1"]).run()
    finally:
        connections.close_all()
