from django.urls import path

from . import views

urlpatterns = [
    path("users", views.AdminUserListView.as_view(), name="admin-users"),
    path("users/<int:user_id>/status", views.AdminUserStatusView.as_view(), name="admin-user-status"),
    path("users/<int:user_id>/sanctions", views.AdminUserSanctionsView.as_view(), name="admin-user-sanctions"),
    path("games", views.AdminGameListView.as_view(), name="admin-games"),
    path("games/<int:game_id>", views.AdminGameDetailView.as_view(), name="admin-game-detail"),
    path("games/<int:game_id>/soft-delete", views.AdminGameSoftDeleteView.as_view(), name="admin-game-soft-delete"),
    path("games/<int:game_id>/restore", views.AdminGameRestoreView.as_view(), name="admin-game-restore"),
]
