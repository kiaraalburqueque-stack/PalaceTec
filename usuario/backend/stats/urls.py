from django.urls import path

from .views import WinRateView

urlpatterns = [
    path("win-rate", WinRateView.as_view(), name="stats-win-rate"),
]
