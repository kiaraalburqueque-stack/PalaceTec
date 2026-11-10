from datetime import timedelta
from decimal import Decimal
from uuid import uuid4

from django.utils import timezone
from rest_framework.test import APITestCase

from accounts.models import User
from roulette.models import Bet, GameRound, Room
from wallet.models import Wallet


class StatsTestBase(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="stats@palacetec.pe", dni="11111111", birth_date="2000-01-01")
        self.wallet = Wallet.objects.create(user=self.user, balance=1000, bonus_balance=0)
        self.client.force_authenticate(self.user)
        self.room = Room.objects.first()

    def bet(self, amount, payout, user=None, when=None):
        when = when or timezone.now()
        round = GameRound.objects.create(room=self.room, opened_at=when, closes_at=when + timedelta(seconds=15),
            status="SETTLED" if payout is not None else "CLOSED", winning_number=5 if payout is not None else None,
            settled_at=when + timedelta(seconds=20) if payout is not None else None)
        return Bet.objects.create(user=user or self.user, round=round, kind="STRAIGHT_UP", selection="5", chips=[10],
                                  amount=Decimal(amount), payout=None if payout is None else Decimal(payout), reference=uuid4())
