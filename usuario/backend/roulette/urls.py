from django.urls import path
from .views import RoomListView, RoomStateView

urlpatterns = [
    path("rooms", RoomListView.as_view(), name="rooms"),
    path("rooms/<int:room_id>/state", RoomStateView.as_view(), name="room-state"),
]
