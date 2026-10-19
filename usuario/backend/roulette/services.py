from datetime import timedelta
from decimal import Decimal
from math import ceil
from django.db import transaction
from django.db.models import Sum
from django.utils import timezone
from rest_framework.exceptions import ValidationError

from realtime.events import after_commit
from wallet.services import WalletService, ReferenceConflict
from wallet.models import Wallet
from . import payout, rng
from .models import Bet, GameRound, Room
from .serializers import BetInputSerializer
from .state import round_data


class BetsClosed(ValueError):
    pass


class RouletteService:
    @staticmethod
    @transaction.atomic
    def place_bet(user, room_id, data):
        serializer = BetInputSerializer(data=data)
        serializer.is_valid(raise_exception=True)
        values = serializer.validated_data
        # Todas las rutas bloquean sala -> ronda -> billetera, en ese orden.
        room = Room.objects.select_for_update().get(pk=room_id, is_active=True)
        round = GameRound.objects.select_for_update().filter(pk=values["round_id"], room=room).first()
        from accounts.models import User
        from responsible.services import ExclusionService
        user = User.objects.select_for_update().get(pk=user.pk)
        ExclusionService.ensure_allowed(user.dni)
        Wallet.objects.select_for_update().get(user=user)
        existing = Bet.objects.filter(user=user, reference=values["reference"]).select_related("round").first()
        if existing:
            if (existing.round.room_id != room.pk or existing.round_id != values["round_id"]
                    or existing.kind != values["kind"] or existing.selection != values["selection"]
                    or existing.chips != values["chips"]):
                raise ReferenceConflict("Esta referencia corresponde a otra apuesta.")
            return existing, False
        if round is None or round.status != GameRound.Status.OPEN or timezone.now() >= round.closes_at:
            raise BetsClosed("No va mas: la ronda no acepta apuestas.")
        amount = Decimal(sum(values["chips"]))
        accumulated = Bet.objects.filter(user=user, round=round).aggregate(total=Sum("amount"))["total"] or Decimal("0")
        if amount < room.min_bet or accumulated + amount > room.max_bet:
            raise ValidationError({"amount": "El monto debe respetar el minimo por apuesta y el maximo acumulado por ronda."})
        # La referencia monetaria usa el ID propio de la apuesta, no texto del cliente.
        bet = Bet.objects.create(user=user, round=round, kind=values["kind"],
            selection=values["selection"], chips=values["chips"], amount=amount, reference=values["reference"])
        WalletService.debit(user, amount, f"roulette:bet:{bet.pk}")
        return bet, True

    @staticmethod
    def calculate(bet, number):
        if bet.kind == Bet.Kind.STRAIGHT_UP:
            return payout.pleno(bet.amount, int(bet.selection), number)
        if bet.kind == Bet.Kind.COLOR:
            return payout.color(bet.amount, bet.selection, number)
        if bet.kind == Bet.Kind.PARITY:
            return payout.paridad(bet.amount, bet.selection, number)
        raise ValueError("Tipo de apuesta invalido.")

    @classmethod
    @transaction.atomic
    def tick(cls, room_id):
        room = Room.objects.select_for_update().get(pk=room_id)
        round = GameRound.objects.select_for_update().filter(room=room).exclude(status="SETTLED").first()
        now = timezone.now()
        group = f"room.{room_id}"
        # Una sala desactivada liquida lo pendiente y deja de abrir rondas nuevas.
        if round is None:
            if not room.is_active:
                return None
            round = GameRound.objects.create(room=room, opened_at=now, closes_at=now+timedelta(seconds=15))
            after_commit(group, "timer:start", round_data(round, now))
        elif round.status == GameRound.Status.OPEN and now >= round.closes_at:
            round.status = GameRound.Status.CLOSED
            round.save(update_fields=["status"])
            after_commit(group, "bets:closed", {"round_id": round.pk})
            after_commit(group, "timer:stop", {"round_id": round.pk})
        elif round.status == GameRound.Status.CLOSED:
            cls._settle_locked(round)
        if round.status == GameRound.Status.OPEN:
            after_commit(group, "timer:tick", {"round_id": round.pk, "closes_at": round.closes_at.isoformat(), "remaining_seconds": max(0, ceil((round.closes_at-now).total_seconds()))})
        return round

    @classmethod
    def _settle_locked(cls, round):
        # Solo lo llama tick con sala y ronda bloqueadas. Nunca recibe un numero del cliente.
        number = rng.spin()
        totals = {}
        for bet in round.bets.select_related("user").order_by("user_id", "pk"):
            bet.payout = cls.calculate(bet, number)
            if bet.payout:
                WalletService.credit(bet.user, bet.payout, f"roulette:win:{bet.pk}")
            bet.save(update_fields=["payout"])
            totals[bet.user_id] = totals.get(bet.user_id, Decimal("0")) + bet.payout
        round.status = GameRound.Status.SETTLED
        round.winning_number = number
        round.settled_at = timezone.now()
        round.save(update_fields=["status", "winning_number", "settled_at"])
        after_commit(f"room.{round.room_id}", "round:result", {"round_id": round.pk, "winning_number": number, "settled_at": round.settled_at.isoformat()})
        for user_id, total in totals.items():
            after_commit(f"user.{user_id}", "round:winnings", {"round_id": round.pk, "payout": format(total, ".2f")})
