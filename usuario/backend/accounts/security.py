"""HU56: alerta por inicio de sesión desde una IP o un dispositivo nuevo."""
import secrets
from datetime import timedelta

from django.conf import settings
from django.core.mail import send_mail
from django.db import transaction
from django.utils import timezone
from rest_framework import serializers
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from .authentication import enforce_csrf
from .models import LoginHistory, User
from .recovery import revoke_all_sessions, token_hash
from .throttles import LoginRateThrottle

LINK_MAX_AGE = timedelta(hours=24)
LOCK_DURATION = timedelta(hours=24)


def request_fingerprint(request):
    # Conexión directa (NUM_PROXIES = 0): se usa REMOTE_ADDR y no X-Forwarded-For.
    return request.META.get("REMOTE_ADDR") or None, (request.META.get("HTTP_USER_AGENT") or "")[:255]


def describe_device(user_agent):
    agent = user_agent.lower()
    browser = next((name for key, name in (("edg/", "Edge"), ("chrome/", "Chrome"), ("firefox/", "Firefox"),
                                           ("safari/", "Safari")) if key in agent), "navegador desconocido")
    system = next((name for key, name in (("windows", "Windows"), ("android", "Android"), ("iphone", "iPhone"),
                                          ("mac os", "macOS"), ("linux", "Linux")) if key in agent), "sistema desconocido")
    return f"{browser} en {system}"


def register_login(user, request):
    """Guarda el acceso y avisa si la IP o el User-Agent no se habían visto antes."""
    ip, agent = request_fingerprint(request)
    previous = LoginHistory.objects.filter(user=user)
    first_login = not previous.exists()
    new_ip = not first_login and not previous.filter(ip_address=ip).exists()
    new_device = not first_login and not previous.filter(user_agent=agent).exists()
    entry = LoginHistory.objects.create(user=user, ip_address=ip, user_agent=agent,
                                        is_new_ip=new_ip, is_new_device=new_device)
    if new_ip or new_device:
        alert_new_login(user, entry)
    return entry


def alert_new_login(user, entry):
    from notifications.models import Notification
    from notifications.services import allowed, notify
    if not allowed(user, Notification.Category.SECURITY):
        return
    when = timezone.localtime(entry.created_at).strftime("%d/%m/%Y %H:%M")
    device = describe_device(entry.user_agent)
    # Token aleatorio corto en la ruta (sin "?token="): el enlace no se corta en la consola de correo.
    token = secrets.token_urlsafe(24)
    link = f"{settings.FRONTEND_URL}/seguridad/no-fui-yo/{token}"
    notify(user, Notification.Category.SECURITY, "Nuevo acceso a tu cuenta",
           f"Iniciaste sesión el {when} desde {device} (IP {entry.ip_address or 'desconocida'}). Si no fuiste tú, cambia tu contraseña.")
    body = (f"Hola {user.nickname or user.email},\n\n"
            f"Detectamos un nuevo acceso a tu cuenta de PalaceTec:\n\n"
            f"  Fecha: {when} (hora de Lima)\n  Dispositivo: {device}\n  IP: {entry.ip_address or 'desconocida'}\n\n"
            f"Si fuiste tú, no necesitas hacer nada.\n\n"
            f"Si NO fuiste tú, pulsa \"No fui yo\" para bloquear tu cuenta temporalmente y cerrar todas las sesiones:\n\n"
            f"NO FUI YO:\n{link}\n\nDespués restablece tu contraseña desde la pantalla de inicio de sesión.\n\n— PalaceTec")
    html = (f"<p>Hola {user.nickname or user.email},</p><p>Detectamos un nuevo acceso a tu cuenta de PalaceTec:</p>"
            f"<ul><li>Fecha: {when} (hora de Lima)</li><li>Dispositivo: {device}</li><li>IP: {entry.ip_address or 'desconocida'}</li></ul>"
            f"<p>Si no fuiste tú:</p><p><a href=\"{link}\" style=\"background:#bb2737;color:#fff;padding:10px 18px;"
            f"border-radius:6px;text-decoration:none;font-weight:bold\">No fui yo</a></p>")
    transaction.on_commit(lambda: send_mail("PalaceTec · Nuevo acceso a tu cuenta", body, settings.DEFAULT_FROM_EMAIL,
                                            [user.email], html_message=html, fail_silently=True))
    LoginHistory.objects.filter(pk=entry.pk).update(alert_sent=True, report_token_hash=token_hash(token))


class NotMeInput(serializers.Serializer):
    token = serializers.CharField(max_length=512)


class NotMeView(APIView):
    """Botón "No fui yo": bloquea la cuenta 24 h y revoca todas las sesiones."""
    authentication_classes = []
    permission_classes = [AllowAny]
    throttle_classes = [LoginRateThrottle]

    def post(self, request):
        enforce_csrf(request)
        data = NotMeInput(data=request.data)
        data.is_valid(raise_exception=True)
        invalid = Response({"code": "invalid_token", "detail": "El enlace no es válido o venció."}, status=400)
        with transaction.atomic():
            entry = (LoginHistory.objects.select_for_update()
                     .filter(report_token_hash=token_hash(data.validated_data["token"])).first())
            now = timezone.now()
            if entry is None or entry.created_at + LINK_MAX_AGE <= now:
                return invalid
            user = User.objects.select_for_update().get(pk=entry.user_id)
            if entry.reported_at is None:
                entry.reported_at = now
                entry.save(update_fields=["reported_at"])
                user.locked_until = max(user.locked_until or now, now + LOCK_DURATION)
                revoke_all_sessions(user)
                user.save(update_fields=["locked_until", "auth_version"])
        return Response({"detail": "Bloqueamos tu cuenta temporalmente y cerramos todas las sesiones. "
                                   "Restablece tu contraseña para volver a entrar.",
                         "locked_until": user.locked_until.isoformat()})
