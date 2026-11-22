from decimal import Decimal
from unittest import TestCase

from .payout import paridad


class HU15Tests(TestCase):
    def test_even_and_odd_for_entire_wheel(self):
        for number in range(1, 37):
            for selection, remainder in (("par", 0), ("impar", 1)):
                with self.subTest(number=number, selection=selection):
                    expected = Decimal("20.50") if number % 2 == remainder else Decimal("0")
                    self.assertEqual(paridad("10.25", selection, number), expected)

    def test_zero_loses_for_both_parities(self):
        for selection in ("par", "impar"):
            self.assertEqual(paridad(10, selection, 0), 0)

    def test_invalid_amounts(self):
        for amount in (0, -1, "NaN", "Infinity", "abc", None):
            with self.subTest(amount=amount), self.assertRaises(ValueError):
                paridad(amount, "par", 2)

    def test_invalid_selection(self):
        for selection in ("rojo", "even", "PAR", "", None, 0, []):
            with self.subTest(selection=selection), self.assertRaises(ValueError):
                paridad(10, selection, 2)

    def test_invalid_winning_number(self):
        for number in (-1, 37, True, "2", 2.0):
            with self.subTest(number=number), self.assertRaises(ValueError):
                paridad(10, "par", number)
