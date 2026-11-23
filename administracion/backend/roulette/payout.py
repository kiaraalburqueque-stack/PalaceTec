"""Pagos totales: incluyen la devolucion de la apuesta original."""
from decimal import Decimal, InvalidOperation, localcontext

from .constants import BLACK_NUMBERS, RED_NUMBERS


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


def color(amount, selection, winning_number):
    """Paga el doble por rojo o negro; el cero siempre pierde."""
    amount = _amount(amount)
    if selection not in ("rojo", "negro"):
        raise ValueError("El color debe ser rojo o negro.")
    winning_number = _number(winning_number)
    numbers = RED_NUMBERS if selection == "rojo" else BLACK_NUMBERS
    return _total(amount, 2) if winning_number in numbers else Decimal("0")


def paridad(amount, selection, winning_number):
    """Paga el doble por par o impar; el cero siempre pierde."""
    amount = _amount(amount)
    if selection not in ("par", "impar"):
        raise ValueError("La paridad debe ser par o impar.")
    winning_number = _number(winning_number)
    wins = winning_number != 0 and winning_number % 2 == (0 if selection == "par" else 1)
    return _total(amount, 2) if wins else Decimal("0")


def docena(amount, selection, winning_number):
    amount = _amount(amount)
    if selection not in ("1", "2", "3"):
        raise ValueError("Docena invalida.")
    number = _number(winning_number)
    return _total(amount, 3) if number and (number-1)//12+1 == int(selection) else Decimal("0")


def columna(amount, selection, winning_number):
    amount = _amount(amount)
    if selection not in ("1", "2", "3"):
        raise ValueError("Columna invalida.")
    number = _number(winning_number)
    return _total(amount, 3) if number and (number-1)%3+1 == int(selection) else Decimal("0")
