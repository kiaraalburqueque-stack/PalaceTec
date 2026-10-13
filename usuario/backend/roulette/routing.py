from django.urls import path
from .consumers import RouletteConsumer

websocket_urlpatterns = [
    path("ws/roulette/<int:room_id>/", RouletteConsumer.as_asgi()),
]
