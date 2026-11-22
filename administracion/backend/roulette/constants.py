"""Casillas de la ruleta europea, agrupadas por color."""
RED_NUMBERS = frozenset({1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36})
BLACK_NUMBERS = frozenset(range(1, 37)) - RED_NUMBERS
GREEN_NUMBERS = frozenset({0})
