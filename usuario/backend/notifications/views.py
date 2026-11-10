from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework.response import Response

from roulette.views import AuthenticatedView
from .models import Notification
from .services import unread_count

PAGE_SIZE = 30


class NotificationListView(AuthenticatedView):
    """HU57: las últimas notificaciones y el contador de no leídas."""

    def get(self, request):
        items = Notification.objects.filter(user=request.user)[:PAGE_SIZE]
        return Response({"unread_count": unread_count(request.user), "results": [item.as_dict() for item in items]})


class NotificationReadAllView(AuthenticatedView):
    def post(self, request):
        updated = Notification.objects.filter(user=request.user, read_at__isnull=True).update(read_at=timezone.now())
        return Response({"updated": updated, "unread_count": 0})


class NotificationReadView(AuthenticatedView):
    def post(self, request, notification_id):
        item = get_object_or_404(Notification, pk=notification_id, user=request.user)
        if item.read_at is None:
            item.read_at = timezone.now()
            item.save(update_fields=["read_at"])
        return Response({**item.as_dict(), "unread_count": unread_count(request.user)})
