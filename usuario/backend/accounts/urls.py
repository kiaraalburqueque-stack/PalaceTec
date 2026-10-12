from django.urls import path

from .views import LoginView, MeView, RegisterView

urlpatterns = [
    path("login", LoginView.as_view(), name="login"),
    path("me", MeView.as_view(), name="me"),
    path("register", RegisterView.as_view(), name="register"),
]
