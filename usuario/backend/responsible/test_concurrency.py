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


@skipIf(connection.vendor != 'postgresql', 'Los bloqueos por fila requieren PostgreSQL.')
class HU35ConcurrentRepeatTests(APITransactionTestCase):
    def test_simultaneous_retries_repeat_once(self):
        from unittest.mock import patch
        from roulette.repeat import repeat_bets
        from roulette.models import RepeatOperation
        user = User.objects.create_user(email='race-repeat@example.com', dni='12345678', birth_date='2000-01-01')
        Wallet.objects.create(user=user, balance=1000, bonus_balance=1000)
        DailyLimit.objects.create(user=user, amount=1000)
        room = Room.objects.create(name='repeat-race')
        source = RouletteService.tick(room.pk)
        RouletteService.place_bet(user, room.pk, {'round_id': source.pk,
            'kind': 'COLOR', 'selection': 'rojo', 'chips': [10], 'reference': str(uuid4())})
        with patch('roulette.services.timezone.now', return_value=source.closes_at):
            RouletteService.tick(room.pk)
        with patch('roulette.services.rng.spin', return_value=0):
            RouletteService.tick(room.pk)
        target = RouletteService.tick(room.pk)
        target.closes_at = timezone.now()+timedelta(minutes=5)
        target.save()
        data = {'round_id': target.pk, 'reference': str(uuid4())}
        barrier = Barrier(4)
        def retry(_):
            close_old_connections()
            try:
                barrier.wait(timeout=10)
                return repeat_bets(user, room.pk, data)[1]
            finally:
                close_old_connections()
        with ThreadPoolExecutor(max_workers=4) as pool:
            self.assertEqual(sum(pool.map(retry, range(4))), 1)
        self.assertEqual(RepeatOperation.objects.count(), 1)
        self.assertEqual(Bet.objects.filter(round=target).count(), 1)
        self.assertEqual(Wallet.objects.get(user=user).balance, 980)
        self.assertEqual(DailySpending.objects.get(user=user, date=timezone.localdate()).amount, 20)
