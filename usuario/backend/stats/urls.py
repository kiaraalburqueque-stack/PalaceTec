from django.urls import path

from .views import BalanceHistoryView, BiggestWinView, WinRateView

urlpatterns = [
    path("win-rate", WinRateView.as_view(), name="stats-win-rate"),
    path("biggest-win", BiggestWinView.as_view(), name="stats-biggest-win"),
    path("balance-history", BalanceHistoryView.as_view(), name="stats-balance-history"),
]
