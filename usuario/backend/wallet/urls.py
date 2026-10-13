from django.urls import path
from .views import BalanceView, WalletSummaryView, PreBetCheckView

urlpatterns = [
    path("balance", BalanceView.as_view(), name="wallet-balance"),
    path("summary", WalletSummaryView.as_view(), name="wallet-summary"),
    path("pre-bet-check", PreBetCheckView.as_view(), name="pre-bet-check"),
]
