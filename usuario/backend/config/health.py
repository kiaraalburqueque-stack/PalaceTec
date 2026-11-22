"""HU74: GET /healthcheck revisa la base de datos, Redis y la capa de canales de Django Channels.

Responde 200 si todo está bien y 503 indicando qué servicio falló. La latencia y el error técnico
solo se muestran a administradores; el público solo ve el estado de cada servicio.
"""
import asyncio
import logging
import time

from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from django.conf import settings
from django.db import connection
from django.http import JsonResponse
from django.utils import timezone
from django.views.decorators.http import require_GET

logger = logging.getLogger(__name__)
TIMEOUT_SECONDS = 2


def check_database():
    with connection.cursor() as cursor:
        cursor.execute("SELECT 1")
        if cursor.fetchone()[0] != 1:
            raise RuntimeError("SELECT 1 devolvió un valor inesperado.")


def check_redis():
    import redis
    client = redis.Redis.from_url(settings.REDIS_URL, socket_connect_timeout=TIMEOUT_SECONDS, socket_timeout=TIMEOUT_SECONDS)
    try:
        if not client.ping():
            raise RuntimeError("Redis no respondió al PING.")
    finally:
        client.close()


def check_channels():
    """Envía y recibe un mensaje por un canal temporal de la capa configurada."""
    layer = get_channel_layer()
    if layer is None:
        raise RuntimeError("No hay una capa de canales configurada.")

    async def roundtrip():
        name = await layer.new_channel()
        await layer.send(name, {"type": "health.ping"})
        message = await asyncio.wait_for(layer.receive(name), timeout=TIMEOUT_SECONDS)
        if message.get("type") != "health.ping":
            raise RuntimeError("La capa de canales devolvió un mensaje inesperado.")

    async_to_sync(roundtrip)()


CHECKS = {"database": check_database, "redis": check_redis, "channels": check_channels}


def run_check(check):
    started = time.perf_counter()
    try:
        check()
        error = None
    except Exception as exc:  # cualquier fallo marca el servicio como caído
        error = f"{type(exc).__name__}: {exc}"[:300]
    return {"status": "ok" if error is None else "error", "latency_ms": round((time.perf_counter() - started) * 1000, 2),
            "error": error}


def is_admin(request):
    from accounts.authentication import CookieJWTAuthentication
    try:
        result = CookieJWTAuthentication().authenticate(request)
    except Exception:
        return False
    user = result[0] if result else None
    return bool(user and user.is_active and (user.is_staff or user.is_superuser))


@require_GET
def healthcheck(request):
    results = {name: run_check(check) for name, check in CHECKS.items()}
    failed = [name for name, result in results.items() if result["status"] != "ok"]
    if failed:
        logger.error("Healthcheck con fallas: %s", ", ".join(failed))
    detailed = is_admin(request)
    services = {name: (result if detailed else {"status": result["status"]}) for name, result in results.items()}
    response = JsonResponse({"status": "ok" if not failed else "error", "failed": failed, "services": services,
                             "checked_at": timezone.now().isoformat()}, status=503 if failed else 200)
    response["Cache-Control"] = "no-store"
    return response
