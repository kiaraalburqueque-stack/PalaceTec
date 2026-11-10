"""Creación de notificaciones y envío en tiempo real (HU57)."""
from django.conf import settings
from django.core.mail import send_mail
from django.db import transaction

from realtime.events import after_commit
from .models import Notification


def unread_count(user):
    return Notification.objects.filter(user=user, read_at__isnull=True).count()


def notify(user, category, title, message, email=False):
    """Guarda la notificación y la publica por WebSocket al confirmar la transacción.

    Con email=True también envía el mismo aviso por correo. Devuelve None si no se creó.
    """
    notification = Notification.objects.create(user=user, category=category, title=title[:120], message=message[:500])
    after_commit(f"user.{user.pk}", "notification:new", {**notification.as_dict(), "unread_count": unread_count(user)})
    if email:
        transaction.on_commit(lambda: send_mail(f"PalaceTec · {title}", message, settings.DEFAULT_FROM_EMAIL,
                                                [user.email], fail_silently=True))
    return notification
