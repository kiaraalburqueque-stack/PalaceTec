from datetime import timedelta
from decimal import Decimal
from uuid import uuid4
from unittest.mock import patch

from django.db import IntegrityError, transaction
from django.utils import timezone
from rest_framework.test import APITestCase
from accounts.models import User
from wallet.models import Wallet, WalletTransaction
from wallet.services import WalletService
from .models import Bet, GameRound, Room
from .services import RouletteService


class GameTestBase(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="bet@example.com", dni="12345678", birth_date="2000-01-01")
        self.wallet = Wallet.objects.create(user=self.user, balance=1000, bonus_balance=1000)
        self.client.force_authenticate(self.user)
        self.room = Room.objects.first()
        self.round = RouletteService.tick(self.room.pk)
        self.url = f"/api/v1/rooms/{self.room.pk}/bets"

    def data(self, **changes):
        return {"round_id": self.round.pk, "kind": "STRAIGHT_UP", "selection": "1", "chips": [10], "reference": str(uuid4()), **changes}


class HU08HU12HU13BetTests(GameTestBase):
    def test_place_and_replay_only_debit_once(self):
        data = self.data()
        first = self.client.post(self.url, data, format="json")
        second = self.client.post(self.url, data, format="json")
        self.assertEqual((first.status_code, second.status_code), (201, 200))
        self.assertEqual(first.data["id"], second.data["id"])
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 990)
        self.assertEqual(Bet.objects.count(), 1)
        self.assertEqual(WalletTransaction.objects.count(), 1)

    def test_reference_conflict(self):
        data = self.data()
        self.client.post(self.url, data, format="json")
        response = self.client.post(self.url, {**data, "selection": "2"}, format="json")
        self.assertEqual(response.status_code, 409)
        self.assertEqual(Bet.objects.count(), 1)

    def test_insufficient_balance_rolls_back_bet(self):
        self.wallet.balance = 0
        self.wallet.bonus_balance = 0
        self.wallet.save()
        response = self.client.post(self.url, self.data(), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["code"], "insufficient_balance")
        self.assertFalse(Bet.objects.exists())
        self.assertFalse(WalletTransaction.objects.exists())

    def test_invalid_chips_and_selections_and_limits(self):
        cases = [{"chips": []}, {"chips": [20]}, {"chips": [10.5]}, {"chips": [-10]},
                 {"chips": [500, 10]}, {"kind": "invalid"}, {"selection": "37"},
                 {"kind": "COLOR", "selection": "verde"}, {"kind": "PARITY", "selection": "even"}]
        for changes in cases:
            with self.subTest(changes=changes):
                self.assertEqual(self.client.post(self.url, self.data(**changes), format="json").status_code, 400)
        self.assertFalse(Bet.objects.exists())

    def test_round_accumulated_limit(self):
        self.assertEqual(self.client.post(self.url, self.data(chips=[500]), format="json").status_code, 201)
        self.assertEqual(self.client.post(self.url, self.data(), format="json").status_code, 400)

    def test_minimum_for_room(self):
        self.room.min_bet = 50
        self.room.save()
        self.assertEqual(self.client.post(self.url, self.data(), format="json").status_code, 400)

    def test_history_is_private(self):
        self.client.post(self.url, self.data(), format="json")
        other = User.objects.create_user(email="other@example.com", dni="87654321", birth_date="2000-01-01")
        self.client.force_authenticate(other)
        self.assertEqual(self.client.get(self.url).data, [])
        self.client.force_authenticate(None)
        self.assertEqual(self.client.post(self.url, self.data(), format="json").status_code, 401)


class HU16HU17RoundTests(GameTestBase):
    def test_round_is_exactly_fifteen_seconds_and_unique(self):
        self.assertEqual(self.round.closes_at-self.round.opened_at, timedelta(seconds=15))
        self.assertEqual(RouletteService.tick(self.room.pk).pk, self.round.pk)
        with self.assertRaises(IntegrityError), transaction.atomic():
            GameRound.objects.create(room=self.room, opened_at=timezone.now(), closes_at=timezone.now()+timedelta(seconds=15))

    def test_deadline_rejects_bet_even_before_clock_closes(self):
        with patch("roulette.services.timezone.now", return_value=self.round.closes_at):
            response = self.client.post(self.url, self.data(), format="json")
            self.assertEqual(response.status_code, 400)
            self.assertEqual(response.data["code"], "bets_closed")
            RouletteService.tick(self.room.pk)
        self.round.refresh_from_db()
        self.assertEqual(self.round.status, "CLOSED")
        self.assertFalse(Bet.objects.exists())

    def test_wrong_round_or_room(self):
        self.assertEqual(self.client.post(self.url, self.data(round_id=999999), format="json").status_code, 400)
        other = Room.objects.exclude(pk=self.room.pk).first()
        self.assertEqual(self.client.post(f"/api/v1/rooms/{other.pk}/bets", self.data(), format="json").status_code, 400)

    def test_deactivated_room_does_not_open_new_round(self):
        self.room.is_active = False
        self.room.save()
        with patch("roulette.services.timezone.now", return_value=self.round.closes_at):
            RouletteService.tick(self.room.pk)
        RouletteService.tick(self.room.pk)
        self.assertIsNone(RouletteService.tick(self.room.pk))


class HU18HU19SettlementTests(GameTestBase):
    def close(self):
        with patch("roulette.services.timezone.now", return_value=self.round.closes_at):
            RouletteService.tick(self.room.pk)

    def test_full_round_and_idempotent_replay_after_settlement(self):
        data = self.data()
        self.client.post(self.url, data, format="json")
        self.client.post(self.url, self.data(kind="COLOR", selection="rojo"), format="json")
        self.client.post(self.url, self.data(kind="PARITY", selection="par"), format="json")
        self.close()
        with patch("roulette.services.rng.spin", return_value=1) as spin:
            RouletteService.tick(self.room.pk)
            spin.assert_called_once_with()
        self.round.refresh_from_db()
        self.assertEqual((self.round.status, self.round.winning_number), ("SETTLED", 1))
        self.assertIsNotNone(self.round.settled_at)
        self.assertEqual(list(Bet.objects.order_by("id").values_list("payout", flat=True)), [Decimal(360), Decimal(20), Decimal(0)])
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 1350)
        self.assertEqual(self.wallet.bonus_balance, 970)
        self.assertEqual(self.wallet.winnings_balance, 380)
        self.assertEqual(self.client.post(self.url, data, format="json").status_code, 200)
        RouletteService.tick(self.room.pk)
        self.assertEqual(WalletTransaction.objects.filter(type="WIN").count(), 2)

    def test_failure_rolls_back_entire_settlement(self):
        self.client.post(self.url, self.data(), format="json")
        self.client.post(self.url, self.data(kind="COLOR", selection="rojo"), format="json")
        self.close()
        real_credit = WalletService.credit
        def fail_second(user, amount, reference):
            if amount == 20:
                raise RuntimeError("fallo durante premio")
            return real_credit(user, amount, reference)
        with patch("roulette.services.rng.spin", return_value=1), patch("roulette.services.WalletService.credit", side_effect=fail_second):
            with self.assertRaises(RuntimeError):
                RouletteService.tick(self.room.pk)
        self.round.refresh_from_db()
        self.wallet.refresh_from_db()
        self.assertEqual(self.round.status, "CLOSED")
        self.assertIsNone(self.round.winning_number)
        self.assertEqual(self.wallet.balance, 980)
        self.assertFalse(WalletTransaction.objects.filter(type="WIN").exists())
        self.assertFalse(Bet.objects.filter(payout__isnull=False).exists())

    def test_events_only_after_commit(self):
        with patch("realtime.events.publish") as publish:
            with self.captureOnCommitCallbacks(execute=True):
                self.client.post(self.url, self.data(), format="json")
            events = [call.args[1] for call in publish.call_args_list]
            self.assertIn("wallet:update", events)
            publish.reset_mock()
            with self.captureOnCommitCallbacks(execute=True):
                self.close()
            self.assertEqual([call.args[1] for call in publish.call_args_list], ["bets:closed", "timer:stop"])
