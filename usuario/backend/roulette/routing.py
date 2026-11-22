from django.urls import path
from notifications.consumers import NotificationConsumer
from .consumers import RouletteConsumer

websocket_urlpatterns = [
    path("ws/roulette/<int:room_id>/", RouletteConsumer.as_asgi()),
    path("ws/notifications/", NotificationConsumer.as_asgi()),
]
