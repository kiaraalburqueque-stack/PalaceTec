from decimal import Decimal
from unittest import TestCase

from .payout import pleno


class HU13Tests(TestCase):
    def test_pleno_total_includes_stake(self):
        self.assertEqual(pleno("10.25", 17, 17), Decimal("369.00"))
        self.assertEqual(pleno(10, 0, 0), Decimal("360"))

    def test_losing_pleno(self):
        self.assertEqual(pleno(10, 17, 18), 0)

    def test_invalid_amounts(self):
        for amount in (0, -1, "NaN", "Infinity", "abc", None, True):
            with self.subTest(amount=amount), self.assertRaises(ValueError):
                pleno(amount, 1, 1)

    def test_invalid_numbers(self):
        for number in (-1, 37, "1", 1.0, True, None):
            with self.subTest(number=number):
                with self.assertRaises(ValueError):
                    pleno(10, number, 1)
                with self.assertRaises(ValueError):
                    pleno(10, 1, number)
