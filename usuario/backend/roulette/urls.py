from django.urls import path
from .views import RoomListView, RoomStateView, BetView, GameListView, PreviousBetsView, RepeatBetsView

urlpatterns = [
    path("rooms/<int:room_id>/bets/previous", PreviousBetsView.as_view(), name="previous-bets"),
    path("rooms/<int:room_id>/bets/repeat", RepeatBetsView.as_view(), name="repeat-bets"),
    path("games", GameListView.as_view(), name="games"),
    path("rooms/<int:room_id>/bets", BetView.as_view(), name="room-bets"),
    path("rooms", RoomListView.as_view(), name="rooms"),
    path("rooms/<int:room_id>/state", RoomStateView.as_view(), name="room-state"),
]
