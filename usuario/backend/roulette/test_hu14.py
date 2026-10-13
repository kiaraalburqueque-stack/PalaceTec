from decimal import Decimal
from unittest import TestCase

from .constants import BLACK_NUMBERS, RED_NUMBERS
from .payout import color


class HU14Tests(TestCase):
    def test_winning_and_losing_colors_for_entire_wheel(self):
        for number in range(1, 37):
            for selection, numbers in (("rojo", RED_NUMBERS), ("negro", BLACK_NUMBERS)):
                with self.subTest(number=number, selection=selection):
                    expected = Decimal("20.50") if number in numbers else Decimal("0")
                    self.assertEqual(color("10.25", selection, number), expected)

    def test_zero_loses_for_both_colors(self):
        for selection in ("rojo", "negro"):
            self.assertEqual(color(10, selection, 0), 0)

    def test_invalid_amounts(self):
        for amount in (0, -1, "NaN", "Infinity", "abc", None):
            with self.subTest(amount=amount), self.assertRaises(ValueError):
                color(amount, "rojo", 1)

    def test_invalid_selection(self):
        for selection in ("verde", "red", "ROJO", "", None, 1, []):
            with self.subTest(selection=selection), self.assertRaises(ValueError):
                color(10, selection, 1)

    def test_invalid_winning_number(self):
        for number in (-1, 37, True, "1", 1.0):
            with self.subTest(number=number), self.assertRaises(ValueError):
                color(10, "rojo", number)
