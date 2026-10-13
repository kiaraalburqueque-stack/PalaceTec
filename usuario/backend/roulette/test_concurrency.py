from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from threading import Barrier
from unittest import skipIf
from unittest.mock import patch
from uuid import uuid4

from django.db import close_old_connections, connection
from django.utils import timezone
from rest_framework.test import APITransactionTestCase
from accounts.models import User
from wallet.models import Wallet, WalletTransaction
from wallet.services import InsufficientBalance
from .models import Room, Bet, GameRound
from .services import RouletteService


@skipIf(connection.vendor == "sqlite", "SQLite ignora select_for_update; estas carreras requieren PostgreSQL.")
class HU13HU17HU19ConcurrencyTests(APITransactionTestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="parallel@example.com", dni="12345678", birth_date="2000-01-01")
        self.wallet = Wallet.objects.create(user=self.user, balance=1000, bonus_balance=1000)
        self.room = Room.objects.create(name="concurrencia", max_bet=5000)
        self.round = RouletteService.tick(self.room.pk)
        # Plazo holgado para que el test mida bloqueos y no la velocidad de la maquina.
        self.round.closes_at = timezone.now()+timedelta(minutes=5)
        self.round.save()

    def data(self, reference):
        return {"round_id": self.round.pk, "kind": "STRAIGHT_UP", "selection": "1", "chips": [500], "reference": reference}

    def parallel(self, operation, count=4):
        barrier = Barrier(count)
        def run(index):
            close_old_connections()
            try:
                barrier.wait(timeout=10)
                return operation(index)
            finally:
                close_old_connections()
        with ThreadPoolExecutor(max_workers=count) as pool:
            return list(pool.map(run, range(count)))

    def test_parallel_bets_never_overdraw(self):
        def operation(index):
            try:
                RouletteService.place_bet(self.user, self.room.pk, self.data(str(uuid4())))
                return True
            except InsufficientBalance:
                return False
        self.assertEqual(sum(self.parallel(operation)), 2)
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 0)
        self.assertEqual(Bet.objects.count(), 2)

    def test_parallel_same_reference_debits_once(self):
        data = self.data(str(uuid4()))
        results = self.parallel(lambda index: RouletteService.place_bet(self.user, self.room.pk, data))
        self.assertEqual(sum(created for bet, created in results), 1)
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 500)
        self.assertEqual(WalletTransaction.objects.count(), 1)

    def test_parallel_clocks_credit_once(self):
        RouletteService.place_bet(self.user, self.room.pk, self.data(str(uuid4())))
        with patch("roulette.services.timezone.now", return_value=self.round.closes_at):
            RouletteService.tick(self.room.pk)
        with patch("roulette.services.rng.spin", return_value=1):
            self.parallel(lambda index: RouletteService.tick(self.room.pk))
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 18500)
        self.assertEqual(WalletTransaction.objects.filter(type="WIN").count(), 1)
        self.assertEqual(GameRound.objects.filter(room=self.room).exclude(status="SETTLED").count(), 1)
