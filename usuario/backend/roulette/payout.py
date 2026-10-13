"""Pagos totales: incluyen la devolucion de la apuesta original."""
from decimal import Decimal, InvalidOperation, localcontext


def _amount(value):
    try:
        amount = Decimal(str(value))
    except (InvalidOperation, ValueError, TypeError) as exc:
        raise ValueError("El monto debe ser un numero positivo finito.") from exc
    if not amount.is_finite() or amount <= 0:
        raise ValueError("El monto debe ser un numero positivo finito.")
    return amount


def _number(value):
    if type(value) is not int or not 0 <= value <= 36:
        raise ValueError("El numero debe ser un entero entre 0 y 36.")
    return value


def _total(amount, multiplier):
    # Evita redondear montos grandes por la precision global de Decimal.
    with localcontext() as context:
        context.prec = max(context.prec, len(amount.as_tuple().digits) + 2)
        return amount * multiplier


def pleno(amount, number, winning_number):
    """Paga 36 veces el monto si se acierta el numero, incluido el cero."""
    amount = _amount(amount)
    number = _number(number)
    winning_number = _number(winning_number)
    return _total(amount, 36) if number == winning_number else Decimal("0")
