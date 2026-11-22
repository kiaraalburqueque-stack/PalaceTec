from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from decimal import Decimal
from threading import Barrier
from unittest import skipIf
from unittest.mock import patch

from django.conf import settings
from django.db import IntegrityError, close_old_connections, connection, transaction
from rest_framework.test import APITestCase, APITransactionTestCase
from rest_framework_simplejwt.tokens import AccessToken

from accounts.models import User
from .models import Wallet, WalletTransaction
from .services import InsufficientBalance, ReferenceConflict, WalletService


DATA = {"email": "wallet@example.com", "dni": "12345678", "birth_date": "2000-01-01", "password": "Palace123!"}


class HU05Tests(APITestCase):
    def test_registration_bonus(self):
        self.assertEqual(self.client.post("/api/v1/auth/register", DATA).status_code, 201)
        wallet = Wallet.objects.get()
        self.assertEqual(wallet.balance, Decimal("1000.00"))
        entry = WalletTransaction.objects.get()
        self.assertEqual(entry.wallet, wallet)
        self.assertEqual(entry.type, "WELCOME_BONUS")
        self.assertEqual(entry.amount, Decimal("1000.00"))
        self.assertEqual(entry.balance_after, wallet.balance)

    def test_registration_rolls_back_on_bonus_failure(self):
        with patch("accounts.serializers.WalletTransaction.objects.create", side_effect=RuntimeError("fallo")):
            with self.assertRaises(RuntimeError):
                self.client.post("/api/v1/auth/register", DATA)
        self.assertFalse(User.objects.exists())
        self.assertFalse(Wallet.objects.exists())
        self.assertFalse(WalletTransaction.objects.exists())

    def test_registration_rolls_back_on_wallet_failure(self):
        with patch("accounts.serializers.Wallet.objects.create", side_effect=RuntimeError("fallo")):
            with self.assertRaises(RuntimeError):
                self.client.post("/api/v1/auth/register", DATA)
        self.assertFalse(User.objects.exists())


class HU06Tests(APITestCase):
    def setUp(self):
        self.client.post("/api/v1/auth/register", DATA)

    def test_balance_with_login_cookie(self):
        response = self.client.post("/api/v1/auth/login", {"email": DATA["email"], "password": DATA["password"]})
        self.assertEqual(response.status_code, 200)
        other = User.objects.create_user(email="other@example.com", dni="87654321", birth_date="2000-01-01")
        Wallet.objects.create(user=other, balance=50)
        response = self.client.get("/api/v1/wallet/balance", {"user": other.pk})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data, {"balance": "1000.00", "low_balance_threshold": "10.00", "is_low_balance": False})

    def test_no_cookie(self):
        self.assertEqual(self.client.get("/api/v1/wallet/balance").status_code, 401)

    def test_invalid_or_expired_cookie(self):
        token = AccessToken.for_user(User.objects.get())
        token.set_exp(lifetime=timedelta(seconds=-1))
        for value in ("invalid", str(token)):
            self.client.cookies[settings.JWT_ACCESS_COOKIE] = value
            self.assertEqual(self.client.get("/api/v1/wallet/balance").status_code, 401)


class HU19Tests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(**DATA)
        self.wallet = Wallet.objects.create(user=self.user, balance=1000)

    def test_debit_and_credit(self):
        debit = WalletService.debit(self.user, "250.25", "bet-1")
        credit = WalletService.credit(self.user, "50.50", "win-1")
        self.assertEqual(debit.balance_after, Decimal("749.75"))
        self.assertEqual(credit.balance_after, Decimal("800.25"))
        self.assertEqual((debit.type, credit.type), ("BET", "WIN"))

    def test_insufficient_balance(self):
        with self.assertRaises(InsufficientBalance):
            WalletService.debit(self.user, 1001, "too-much")
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 1000)
        self.assertFalse(WalletTransaction.objects.exists())

    def test_invalid_amounts(self):
        for method in (WalletService.debit, WalletService.credit):
            for amount in (0, -1, "0.001", "NaN", "Infinity", "abc"):
                with self.subTest(method=method.__name__, amount=amount), self.assertRaises(ValueError):
                    method(self.user, amount, "invalid")
        self.assertFalse(WalletTransaction.objects.exists())

    def test_repeated_references(self):
        for method, reference in ((WalletService.debit, "bet"), (WalletService.credit, "win")):
            first = method(self.user, 100, reference)
            self.assertEqual(method(self.user, 100, reference).pk, first.pk)
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 1000)
        self.assertEqual(WalletTransaction.objects.count(), 2)

    def test_conflicting_reference(self):
        WalletService.debit(self.user, 100, "same")
        for method, amount in ((WalletService.debit, 200), (WalletService.credit, 100)):
            with self.assertRaises(ReferenceConflict):
                method(self.user, amount, "same")
        other = User.objects.create_user(email="other@example.com", dni="87654321", birth_date="2000-01-01")
        Wallet.objects.create(user=other, balance=1000)
        with self.assertRaises(ReferenceConflict):
            WalletService.debit(other, 100, "same")

    def test_balance_never_negative(self):
        WalletService.debit(self.user, 1000)
        with self.assertRaises(InsufficientBalance):
            WalletService.debit(self.user, "0.01")
        with self.assertRaises(IntegrityError), transaction.atomic():
            Wallet.objects.filter(pk=self.wallet.pk).update(balance=-1)
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 0)

    def test_transaction_failure_rolls_back_balance(self):
        with patch("wallet.services.WalletTransaction.objects.create", side_effect=RuntimeError):
            with self.assertRaises(RuntimeError):
                WalletService.debit(self.user, 100)
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 1000)


class HU19ConcurrencyTests(APITransactionTestCase):
    @skipIf(connection.vendor == "sqlite", "SQLite no soporta bloqueos de fila con select_for_update.")
    def test_concurrent_debits_never_overdraw(self):
        user = User.objects.create_user(**DATA)
        wallet = Wallet.objects.create(user=user, balance=1000)
        barrier = Barrier(4)

        def debit(index):
            close_old_connections()
            try:
                barrier.wait(timeout=10)
                try:
                    WalletService.debit(user, 400, f"concurrent-{index}")
                    return True
                except InsufficientBalance:
                    return False
            finally:
                close_old_connections()

        with ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(debit, range(4)))
        wallet.refresh_from_db()
        self.assertEqual(sum(results), 2)
        self.assertEqual(wallet.balance, 200)
        self.assertEqual(wallet.transactions.count(), 2)
