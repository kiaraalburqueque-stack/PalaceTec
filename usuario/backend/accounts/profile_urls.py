from django.urls import path

from notifications.views import NotificationPreferencesView

from .views import ProfileView, ChangePasswordView

urlpatterns = [
    path("notification-preferences", NotificationPreferencesView.as_view(), name="notification-preferences"),
    path("change-password", ChangePasswordView.as_view(), name="change-password"),
    path("me", ProfileView.as_view(), name="user-profile"),
    path("profile", ProfileView.as_view(), name="update-profile"),
]
