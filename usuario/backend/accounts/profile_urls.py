from django.urls import path

from .views import ProfileView

urlpatterns = [
    path("me", ProfileView.as_view(), name="user-profile"),
    path("profile", ProfileView.as_view(), name="update-profile"),
]
