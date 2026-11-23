from django.urls import path
from .views import (FavoriteGameListView, FavoriteToggleView, FeaturedGameListView, GameCategoryCountView, GameRulesView, RecommendationView,
                    RoomListView, RoomStateView, BetView, GameListView, PreviousBetsView, RepeatBetsView)

urlpatterns = [
    path("rooms/<int:room_id>/bets/previous", PreviousBetsView.as_view(), name="previous-bets"),
    path("rooms/<int:room_id>/bets/repeat", RepeatBetsView.as_view(), name="repeat-bets"),
    path("games", GameListView.as_view(), name="games"),
    path("games/categories", GameCategoryCountView.as_view(), name="game-categories"),
    path("games/favorites", FavoriteGameListView.as_view(), name="game-favorites"),
    path("games/recommendations", RecommendationView.as_view(), name="game-recommendations"),
    path("games/featured", FeaturedGameListView.as_view(), name="games-featured"),
    path("games/<int:game_id>/favorite", FavoriteToggleView.as_view(), name="game-favorite-toggle"),
    path("games/<int:game_id>/rules", GameRulesView.as_view(), name="game-rules"),
    path("rooms/<int:room_id>/bets", BetView.as_view(), name="room-bets"),
    path("rooms", RoomListView.as_view(), name="rooms"),
    path("rooms/<int:room_id>/state", RoomStateView.as_view(), name="room-state"),
]
