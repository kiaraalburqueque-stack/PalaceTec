from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from threading import Barrier
from unittest import skipIf
from uuid import uuid4
from django.db import close_old_connections, connection
from django.utils import timezone
from rest_framework.test import APITransactionTestCase
from accounts.models import User
from wallet.models import Wallet
from roulette.models import Room, Bet
from roulette.services import RouletteService
from .models import DailyLimit, DailySpending
from .services import DailyLimitError


@skipIf(connection.vendor != 'postgresql', 'Los bloqueos por fila requieren PostgreSQL.')
class HU26ConcurrentLimitsTests(APITransactionTestCase):
    def test_bets_in_different_rooms_share_one_daily_limit(self):
        user = User.objects.create_user(email='race-limit@example.com', dni='12345678', birth_date='2000-01-01')
        Wallet.objects.create(user=user, balance=1000, bonus_balance=1000)
        DailyLimit.objects.create(user=user, amount=20)
        rooms = [Room.objects.create(name=f'limit-race-{i}') for i in range(4)]
        rounds = [RouletteService.tick(room.pk) for room in rooms]
        for round in rounds:
            round.closes_at = timezone.now()+timedelta(minutes=5)
            round.save()
        barrier = Barrier(4)
        def bet(index):
            close_old_connections()
            try:
                barrier.wait(timeout=10)
                RouletteService.place_bet(user, rooms[index].pk, {'round_id': rounds[index].pk,
                    'kind': 'COLOR', 'selection': 'rojo', 'chips': [10], 'reference': str(uuid4())})
                return True
            except DailyLimitError:
                return False
            finally:
                close_old_connections()
        with ThreadPoolExecutor(max_workers=4) as pool:
            self.assertEqual(sum(pool.map(bet, range(4))), 2)
        self.assertEqual(DailySpending.objects.get(user=user).amount, 20)
        self.assertEqual(Bet.objects.count(), 2)
        self.assertEqual(Wallet.objects.get(user=user).balance, 980)
