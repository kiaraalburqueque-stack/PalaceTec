from django.urls import path

from .recovery import ForgotPasswordView, ResetPasswordView
from .views import LoginView, MeView, RegisterView, CSRFView, LogoutView, RefreshSessionView

urlpatterns = [
    path("forgot-password", ForgotPasswordView.as_view(), name="forgot-password"),
    path("reset-password", ResetPasswordView.as_view(), name="reset-password"),
    path("refresh", RefreshSessionView.as_view(), name="refresh-session"),
    path("logout", LogoutView.as_view(), name="logout"),
    path("csrf", CSRFView.as_view(), name="csrf"),
    path("login", LoginView.as_view(), name="login"),
    path("me", MeView.as_view(), name="me"),
    path("register", RegisterView.as_view(), name="register"),
]
