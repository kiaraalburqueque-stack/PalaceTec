"""HU69: probabilidad, pago y ventaja de la casa de cada apuesta de la ruleta europea.

Se calcula con fracciones exactas recorriendo los 37 números y usando las mismas funciones de pago del
juego (roulette/payout.py), así el asistente nunca contradice lo que paga la mesa.
"""
import re
from fractions import Fraction

from roulette import payout
from roulette.catalog import normalize

NUMBERS = range(37)
ORDINALS = {"primera": "1", "1ra": "1", "1": "1", "segunda": "2", "2da": "2", "2": "2", "tercera": "3", "3ra": "3", "3": "3"}
DOZEN_RANGES = {"1-12": "1", "13-24": "2", "25-36": "3"}

# tipo -> (función de pago, etiqueta, selecciones válidas)
BETS = {
    "straight": (lambda selection, n: payout.pleno(1, int(selection), n), "Pleno", [str(n) for n in NUMBERS]),
    "color": (lambda selection, n: payout.color(1, selection, n), "Color", ["rojo", "negro"]),
    "parity": (lambda selection, n: payout.paridad(1, selection, n), "Paridad", ["par", "impar"]),
    "dozen": (lambda selection, n: payout.docena(1, selection, n), "Docena", ["1", "2", "3"]),
    "column": (lambda selection, n: payout.columna(1, selection, n), "Columna", ["1", "2", "3"]),
}
ALIASES = {"pleno": "straight", "numero": "straight", "paridad": "parity", "docena": "dozen", "columna": "column"}


class OddsError(ValueError):
    pass


def percent(fraction):
    return format(round(float(fraction) * 100, 2), ".2f")


def describe(bet, selection):
    if bet == "straight":
        return f"Pleno al {selection}"
    if bet == "dozen":
        return f"Docena {selection} ({['1-12', '13-24', '25-36'][int(selection) - 1]})"
    if bet == "column":
        return f"Columna {selection}"
    return f"{BETS[bet][1]} {selection}"


def compute_odds(bet, selection):
    bet = ALIASES.get(bet, bet)
    if bet not in BETS:
        raise OddsError("Tipo de apuesta no válido. Usa straight, color, parity, dozen o column.")
    pay, label, valid = BETS[bet]
    selection = str(selection).strip().lower()
    if selection not in valid:
        raise OddsError(f"Selección no válida para {label.lower()}: usa {', '.join(valid[:3])}{'…' if len(valid) > 3 else ''}.")
    returns = [pay(selection, n) for n in NUMBERS]
    winners = [n for n, value in zip(NUMBERS, returns) if value > 0]
    total_return = int(max(returns))  # pago total por crédito apostado, incluida la apuesta
    probability = Fraction(len(winners), len(NUMBERS))
    house_edge = 1 - probability * total_return
    return {
        "bet": bet, "selection": selection, "label": describe(bet, selection),
        "winning_numbers": len(winners), "total_numbers": len(NUMBERS),
        "probability": f"{len(winners)}/{len(NUMBERS)}",
        "probability_pct": percent(probability),
        "payout": f"{total_return - 1}:1", "total_return": total_return,
        "house_edge": f"{house_edge.numerator}/{house_edge.denominator}", "house_edge_pct": percent(house_edge),
    }


def odds_text(odds):
    return (f"{odds['label']}: gana con {odds['winning_numbers']} de {odds['total_numbers']} números, una probabilidad de "
            f"{odds['winning_numbers']}/{odds['total_numbers']} ({odds['probability_pct']} %). Paga {odds['payout'].replace(':', ' a ')} "
            f"(devuelve {odds['total_return']} veces lo apostado). La ventaja de la casa es {odds['house_edge_pct']} %: "
            "a largo plazo se pierde en promedio ese porcentaje de lo apostado. Cada giro es independiente.")


ODDS_WORDS = ("probab", "posibilidad", "chance", "odds", "que tan facil", "cuantas veces sale", "ventaja de la casa")


def parse_bet(message):
    """Detecta preguntas de probabilidad sobre una apuesta concreta. Devuelve (tipo, selección) o None."""
    text = normalize(message)
    if not any(word in text for word in ODDS_WORDS):
        return None
    words = re.findall(r"[a-z0-9\-]+", text)
    if "docena" in words or any(rng in text for rng in DOZEN_RANGES):
        found = next((DOZEN_RANGES[rng] for rng in DOZEN_RANGES if rng in text), None)
        return "dozen", found or next((ORDINALS[w] for w in words if w in ORDINALS), "1")
    if "columna" in words:
        return "column", next((ORDINALS[w] for w in words if w in ORDINALS), "1")
    if "rojo" in words or "negro" in words:
        return "color", "rojo" if "rojo" in words else "negro"
    if "impar" in words or "par" in words:
        return "parity", "impar" if "impar" in words else "par"
    number = re.search(r"\b(?:pleno|numero|al|el)\s+(?:al\s+|el\s+)?(\d{1,2})\b", text) or re.search(r"\b(\d{1,2})\b", text)
    if number and 0 <= int(number.group(1)) <= 36:
        return "straight", str(int(number.group(1)))
    return None
