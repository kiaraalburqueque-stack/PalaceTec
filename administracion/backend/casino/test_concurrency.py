from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from decimal import Decimal
from threading import Barrier
from unittest import skipIf
from uuid import uuid4
from django.db import close_old_connections, connection
from django.utils import timezone
from rest_framework.test import APITransactionTestCase
from accounts.models import User
from wallet.models import Wallet
from responsible.models import DailyLimit, DailySpending
from responsible.services import DailyLimitError
from .models import Play, SportEvent
from .services import start_blackjack, place_sports


@skipIf(connection.vendor != 'postgresql', 'Requiere bloqueos de PostgreSQL.')
class CasinoConcurrencyTests(APITransactionTestCase):
    def setUp(self):
        self.user = User.objects.create_user(email='new-race@example.com', dni='12345678', birth_date='2000-01-01')
        Wallet.objects.create(user=self.user, balance=1000, bonus_balance=1000)
        DailyLimit.objects.create(user=self.user, amount=20)

    def parallel(self, action):
        barrier = Barrier(4)
        def worker(i):
            close_old_connections()
            try:
                barrier.wait(timeout=10)
                return action(i)
            finally:
                close_old_connections()
        with ThreadPoolExecutor(max_workers=4) as pool:
            return list(pool.map(worker, range(4)))

    def test_blackjack_same_reference_only_debits_once(self):
        ref = uuid4()
        results = self.parallel(lambda _: start_blackjack(self.user, Decimal('10'), ref)[1])
        self.assertEqual(sum(results), 1)
        self.assertEqual(Play.objects.count(), 1)
        self.assertEqual(DailySpending.objects.get().amount, 10)

    def test_sports_across_events_cannot_exceed_daily_limit(self):
        now = timezone.now()
        events = [SportEvent.objects.create(home='A', away='B', starts_at=now+timedelta(minutes=5), ends_at=now+timedelta(minutes=6)) for _ in range(4)]
        def wager(index):
            try:
                place_sports(self.user, events[index].pk, 'HOME', Decimal('10'), uuid4(), Decimal('1.90'))
                return True
            except DailyLimitError:
                return False
        self.assertEqual(sum(self.parallel(wager)), 2)
        self.assertEqual(Play.objects.count(), 2)
        self.assertEqual(DailySpending.objects.get().amount, 20)
        self.assertEqual(Wallet.objects.get(user=self.user).balance, 980)
