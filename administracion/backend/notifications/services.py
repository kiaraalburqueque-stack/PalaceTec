"""Creación de notificaciones y envío en tiempo real (HU57)."""
from django.conf import settings
from django.core.mail import send_mail
from django.db import transaction

from realtime.events import after_commit
from .models import Notification, NotificationSetting


def preferences(user):
    setting, _ = NotificationSetting.objects.get_or_create(user=user)
    return setting


def allowed(user, category):
    """HU58: respeta las preferencias antes de crear una notificación o enviar un correo."""
    return preferences(user).allows(category)


def unread_count(user):
    return Notification.objects.filter(user=user, read_at__isnull=True).count()


def notify(user, category, title, message, email=False):
    """Guarda la notificación y la publica por WebSocket al confirmar la transacción.

    Con email=True también envía el mismo aviso por correo. Devuelve None si el jugador
    desactivó esa categoría: entonces no se guarda ni se envía nada.
    """
    if not allowed(user, category):
        return None
    notification = Notification.objects.create(user=user, category=category, title=title[:120], message=message[:500])
    after_commit(f"user.{user.pk}", "notification:new", {**notification.as_dict(), "unread_count": unread_count(user)})
    if email:
        transaction.on_commit(lambda: send_mail(f"PalaceTec · {title}", message, settings.DEFAULT_FROM_EMAIL,
                                                [user.email], fail_silently=True))
    return notification
