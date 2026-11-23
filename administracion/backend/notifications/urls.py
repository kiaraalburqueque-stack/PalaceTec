from django.urls import path

from .views import NotificationReadAllView, NotificationReadView

urlpatterns = [
    path("read-all", NotificationReadAllView.as_view(), name="notifications-read-all"),
    path("<int:notification_id>/read", NotificationReadView.as_view(), name="notification-read"),
]
