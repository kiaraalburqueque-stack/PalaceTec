import ast
import inspect
from unittest import TestCase
from unittest.mock import patch

from . import rng
from .constants import BLACK_NUMBERS, GREEN_NUMBERS, RED_NUMBERS


class HU18Tests(TestCase):
    def test_spin_uses_secrets_with_37_slots(self):
        for number in range(37):
            with patch("roulette.rng.secrets.randbelow", return_value=number) as randbelow:
                self.assertEqual(rng.spin(), number)
                randbelow.assert_called_once_with(37)

    def test_37000_spins_are_in_range_and_cover_all_numbers(self):
        results = [rng.spin() for _ in range(37000)]
        self.assertTrue(all(type(number) is int and 0 <= number <= 36 for number in results))
        self.assertEqual(set(results), set(range(37)))

    def test_rng_does_not_import_random(self):
        tree = ast.parse(inspect.getsource(rng))
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                self.assertTrue(all(alias.name.split(".")[0] != "random" for alias in node.names))
            elif isinstance(node, ast.ImportFrom):
                self.assertNotEqual((node.module or "").split(".")[0], "random")

    def test_colors_partition_the_wheel(self):
        self.assertEqual(RED_NUMBERS, {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36})
        self.assertEqual(len(BLACK_NUMBERS), 18)
        self.assertFalse(RED_NUMBERS & BLACK_NUMBERS)
        self.assertEqual(GREEN_NUMBERS, {0})
        self.assertEqual(RED_NUMBERS | BLACK_NUMBERS | GREEN_NUMBERS, set(range(37)))
