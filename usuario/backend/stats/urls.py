from django.urls import path

from .views import BiggestWinView, WinRateView

urlpatterns = [
    path("win-rate", WinRateView.as_view(), name="stats-win-rate"),
    path("biggest-win", BiggestWinView.as_view(), name="stats-biggest-win"),
]
