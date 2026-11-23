"""HU69: probabilidad, pago y ventaja de la casa calculados por el backend."""
from unittest.mock import patch

from django.core.cache import cache
from rest_framework.test import APITestCase

from accounts.models import User
from assistant import providers
from assistant.odds import compute_odds, parse_bet


class HU69OddsCalculationTests(APITestCase):
    def test_hu69_straight_up_is_1_in_37_and_pays_35_to_1(self):
        odds = compute_odds("straight", 17)
        self.assertEqual((odds["probability"], odds["probability_pct"], odds["payout"]), ("1/37", "2.70", "35:1"))
        self.assertEqual((odds["total_return"], odds["house_edge"], odds["house_edge_pct"]), (36, "1/37", "2.70"))
        self.assertEqual(odds["label"], "Pleno al 17")

    def test_hu69_color_parity_dozen_and_column(self):
        cases = {("color", "rojo"): ("18/37", "48.65", "1:1"), ("parity", "impar"): ("18/37", "48.65", "1:1"),
                 ("dozen", "2"): ("12/37", "32.43", "2:1"), ("column", "3"): ("12/37", "32.43", "2:1")}
        for (bet, selection), expected in cases.items():
            with self.subTest(bet=bet):
                odds = compute_odds(bet, selection)
                self.assertEqual((odds["probability"], odds["probability_pct"], odds["payout"]), expected)

    def test_hu69_house_edge_is_2_70_percent_for_every_bet(self):
        for bet, selection in [("straight", "0"), ("color", "negro"), ("parity", "par"), ("dozen", "1"), ("column", "1")]:
            with self.subTest(bet=bet):
                self.assertEqual(compute_odds(bet, selection)["house_edge_pct"], "2.70")

    def test_hu69_detects_bets_in_questions(self):
        self.assertEqual(parse_bet("¿Qué probabilidad tiene un pleno al 17?"), ("straight", "17"))
        self.assertEqual(parse_bet("probabilidad del ROJO"), ("color", "rojo"))
        self.assertEqual(parse_bet("¿Qué posibilidad hay de acertar la segunda docena?"), ("dozen", "2"))
        self.assertEqual(parse_bet("chance de la docena 25-36"), ("dozen", "3"))
        self.assertEqual(parse_bet("probabilidad de la tercera columna"), ("column", "3"))
        self.assertEqual(parse_bet("probabilidad de impar"), ("parity", "impar"))
        self.assertIsNone(parse_bet("¿Cuánto paga el pleno al 17?"))  # sin palabra de probabilidad
        self.assertIsNone(parse_bet("probabilidad del 40"))


class HU69OddsApiTests(APITestCase):
    def setUp(self):
        cache.clear()
        self.user = User.objects.create_user(email="hu69@palacetec.pe", dni="69696969", birth_date="2000-01-01")
        self.client.force_authenticate(self.user)

    def test_hu69_odds_endpoint(self):
        response = self.client.get("/api/v1/assistant/odds", {"bet": "straight", "number": 17})
        self.assertEqual(response.status_code, 200)
        self.assertEqual((response.data["probability"], response.data["payout"], response.data["house_edge_pct"]),
                         ("1/37", "35:1", "2.70"))
        color = self.client.get("/api/v1/assistant/odds", {"bet": "color", "selection": "Rojo"}).data
        self.assertEqual(color["probability"], "18/37")
        self.assertEqual(self.client.get("/api/v1/assistant/odds", {"bet": "docena", "selection": "3"}).data["probability"], "12/37")

    def test_hu69_odds_endpoint_rejects_invalid_input(self):
        for params in [{"bet": "straight", "number": 37}, {"bet": "straight"}, {"bet": "color", "selection": "verde"},
                       {"bet": "split", "number": 5}, {}]:
            with self.subTest(params=params):
                self.assertEqual(self.client.get("/api/v1/assistant/odds", params).status_code, 400)

    def test_hu69_odds_endpoint_requires_login(self):
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get("/api/v1/assistant/odds", {"bet": "straight", "number": 1}).status_code, 401)

    def test_hu69_chat_answers_with_backend_odds_and_never_asks_the_model(self):
        with patch.object(providers, "get_provider") as get_provider:
            response = self.client.post("/api/v1/assistant/chat", {"message": "¿Cuál es la probabilidad de un pleno al 17?"}, format="json")
        get_provider.assert_not_called()
        reply = response.data["reply"]
        self.assertEqual(reply["source"], "odds")
        self.assertEqual(reply["data"]["odds"]["probability"], "1/37")
        self.assertIn("2.70 %", reply["content"])
        self.assertIn("35 a 1", reply["content"])
        self.assertIn("Mis límites", reply["content"])
