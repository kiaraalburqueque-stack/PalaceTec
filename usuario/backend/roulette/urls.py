from django.urls import path
from .views import RoomListView, RoomStateView, BetView

urlpatterns = [
    path("rooms/<int:room_id>/bets", BetView.as_view(), name="room-bets"),
    path("rooms", RoomListView.as_view(), name="rooms"),
    path("rooms/<int:room_id>/state", RoomStateView.as_view(), name="room-state"),
]
