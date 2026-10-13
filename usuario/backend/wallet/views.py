from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from accounts.authentication import CookieJWTAuthentication
from .models import Wallet


class BalanceView(APIView):
    authentication_classes = [CookieJWTAuthentication]
    permission_classes = [IsAuthenticated]

    def get(self, request):
        wallet = Wallet.objects.get(user=request.user)
        return Response({"balance": format(wallet.balance, ".2f")})


class WalletSummaryView(BalanceView):
    def get(self, request):
        wallet = Wallet.objects.get(user=request.user)
        return Response({
            "balance": format(wallet.balance, ".2f"),
            "bonus_balance": format(wallet.bonus_balance, ".2f"),
            "winnings_balance": format(wallet.winnings_balance, ".2f"),
        })


class PreBetCheckView(BalanceView):
    def post(self, request):
        from .services import WalletService
        try:
            return Response(WalletService.pre_bet_check(request.user, request.data.get("amount")))
        except ValueError as exc:
            return Response({"detail": str(exc)}, status=400)
