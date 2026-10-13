from decimal import Decimal, InvalidOperation

from django.db import IntegrityError, transaction

from .models import Wallet, WalletTransaction


class InsufficientBalance(ValueError):
    pass


class ReferenceConflict(ValueError):
    pass


class WalletService:
    @classmethod
    def debit(cls, user, amount, reference=None):
        return cls._apply(user, amount, reference, WalletTransaction.Type.BET)

    @classmethod
    def credit(cls, user, amount, reference=None):
        return cls._apply(user, amount, reference, WalletTransaction.Type.WIN)

    @staticmethod
    def _existing(reference, wallet, amount, kind):
        if reference is None:
            return None
        existing = WalletTransaction.objects.filter(reference=reference).first()
        if existing and (existing.wallet_id != wallet.pk or existing.amount != amount or existing.type != kind):
            raise ReferenceConflict("La referencia ya corresponde a otra operacion.")
        return existing

    @classmethod
    def _apply(cls, user, amount, reference, kind):
        try:
            amount = Decimal(str(amount))
            if not amount.is_finite() or amount <= 0 or amount >= Decimal("1e16") or amount != amount.quantize(Decimal("0.01")):
                raise ValueError("El monto debe ser positivo, con hasta dos decimales y menor a 1e16.")
        except (InvalidOperation, TypeError) as exc:
            raise ValueError("Monto invalido.") from exc
        if reference == "":
            reference = None
        if reference is not None and (not isinstance(reference, str) or len(reference) > 255):
            raise ValueError("Referencia invalida.")
        with transaction.atomic():
            wallet = Wallet.objects.select_for_update().get(user=user)
            existing = cls._existing(reference, wallet, amount, kind)
            if existing:
                return existing
            balance = wallet.balance - amount if kind == WalletTransaction.Type.BET else wallet.balance + amount
            if balance < 0:
                raise InsufficientBalance("Saldo insuficiente.")
            if balance >= Decimal("1e16"):
                raise ValueError("El saldo excede el limite permitido.")
            try:
                # El savepoint permite consultar la referencia si otra billetera la uso a la vez.
                with transaction.atomic():
                    wallet.balance = balance
                    wallet.save(update_fields=["balance"])
                    return WalletTransaction.objects.create(
                        wallet=wallet, type=kind, amount=amount,
                        balance_after=balance, reference=reference,
                    )
            except IntegrityError:
                existing = cls._existing(reference, wallet, amount, kind)
                if existing:
                    return existing
                raise
