"""HU68: asistente virtual con proveedor de IA simulado y modo sin clave (preguntas frecuentes)."""
import io
import json
from decimal import Decimal
from unittest.mock import patch

from django.core.cache import cache
from django.test import override_settings
from rest_framework.test import APITestCase

from accounts.models import User
from responsible.models import DailyLimit
from wallet.models import Wallet
from assistant import providers
from assistant.guard import GUARD_REPLY, asks_for_strategy
from assistant.models import AssistantMessage

URL = "/api/v1/assistant/chat"


class FakeProvider:
    name = "fake"

    def __init__(self, reply="Respuesta del modelo.", error=None):
        self.reply, self.error, self.calls = reply, error, []

    def complete(self, system, history, message):
        self.calls.append((system, history, message))
        if self.error:
            raise self.error
        return self.reply


class HU68AssistantTests(APITestCase):
    def setUp(self):
        cache.clear()
        self.user = User.objects.create_user(email="hu68@palacetec.pe", dni="68686868", birth_date="2000-01-01")
        Wallet.objects.create(user=self.user, balance=Decimal("850.00"), bonus_balance=Decimal("600.00"))
        self.client.force_authenticate(self.user)

    def ask(self, message):
        return self.client.post(URL, {"message": message}, format="json")

    def test_hu68_requires_authentication(self):
        self.client.force_authenticate(None)
        self.assertEqual(self.ask("hola").status_code, 401)
        self.assertEqual(self.client.get(URL).status_code, 401)

    def test_hu68_without_key_answers_rules_with_faq(self):
        response = self.ask("¿Cómo se juega la ruleta?")
        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.data["reply"]["source"], "faq")
        self.assertIn("37 casillas", response.data["reply"]["content"])
        self.assertIn("Mis límites", response.data["reminder"])

    def test_hu68_faq_covers_payouts_blackjack_and_real_money(self):
        self.assertIn("35 a 1", self.ask("¿Cuánto paga cada apuesta?").data["reply"]["content"])
        self.assertIn("plantarte", self.ask("reglas del blackjack").data["reply"]["content"])
        self.assertIn("créditos virtuales", self.ask("¿Puedo retirar mi dinero?").data["reply"]["content"])

    def test_hu68_account_answers_use_real_balance_and_limit(self):
        self.assertIn("850.00", self.ask("¿Cuál es mi saldo?").data["reply"]["content"])
        self.assertIn("Todavía no defines", self.ask("¿cuál es mi límite diario?").data["reply"]["content"])
        DailyLimit.objects.update_or_create(user=self.user, defaults={"amount": Decimal("200.00")})
        reply = self.ask("cuanto me queda de limite").data["reply"]["content"]
        self.assertIn("200.00", reply)
        self.assertIn("te quedan 200.00", reply)

    def test_hu68_never_gives_strategies_to_win(self):
        for question in ["Dame una estrategia para ganar en la ruleta", "¿Cómo recupero lo perdido? quiero recuperar lo perdido",
                         "¿qué número va a salir?", "¿Sirve la martingala?", "¿Debo apostar más para ganar?"]:
            with self.subTest(question=question):
                reply = self.ask(question).data["reply"]
                self.assertEqual((reply["source"], reply["content"]), ("guard", GUARD_REPLY))
        self.assertFalse(asks_for_strategy("¿Cuánto paga una docena?"))

    def test_hu68_messages_are_saved_and_history_is_private(self):
        self.ask("hola")
        other = User.objects.create_user(email="otro68@palacetec.pe", dni="68686869", birth_date="2000-01-01")
        AssistantMessage.objects.create(user=other, role="USER", content="secreto")
        history = self.client.get(URL).data["messages"]
        self.assertEqual([m["role"] for m in history], ["USER", "ASSISTANT"])
        self.assertNotIn("secreto", [m["content"] for m in history])

    def test_hu68_validates_message(self):
        self.assertEqual(self.ask("   ").status_code, 400)
        self.assertEqual(self.ask("x" * 501).status_code, 400)

    @override_settings(ASSISTANT_RATE_LIMIT="20/min")
    def test_hu68_limits_to_twenty_messages_per_minute(self):
        for _ in range(20):
            self.assertEqual(self.ask("hola").status_code, 201)
        response = self.ask("hola")
        self.assertEqual(response.status_code, 429)
        self.assertEqual(response.data["code"], "assistant_rate_limited")
        self.assertEqual(self.client.get(URL).status_code, 200)  # leer el historial no consume el límite

    def test_hu68_uses_configured_provider_with_catalog_context(self):
        fake = FakeProvider("La ruleta tiene 37 casillas.")
        with patch.object(providers, "get_provider", return_value=fake):
            self.ask("hola")
            reply = self.ask("¿Qué es un pleno?").data["reply"]
        self.assertEqual((reply["source"], reply["content"]), ("ai", "La ruleta tiene 37 casillas."))
        system, history, message = fake.calls[-1]
        self.assertIn("Ruleta Europea", system)
        self.assertIn("35 a 1", system)
        self.assertEqual(message, "¿Qué es un pleno?")
        self.assertEqual([role for role, _ in history], ["USER", "ASSISTANT"])

    def test_hu68_account_and_strategy_questions_never_reach_the_provider(self):
        fake = FakeProvider()
        with patch.object(providers, "get_provider", return_value=fake):
            self.assertEqual(self.ask("mi saldo").data["reply"]["source"], "faq")
            self.assertEqual(self.ask("dame un truco").data["reply"]["source"], "guard")
        self.assertEqual(fake.calls, [])

    def test_hu68_provider_failure_or_strategy_reply_falls_back_safely(self):
        with patch.object(providers, "get_provider", return_value=FakeProvider(error=providers.ProviderError("caído"))):
            self.assertEqual(self.ask("reglas de la ruleta").data["reply"]["source"], "faq")
        with patch.object(providers, "get_provider", return_value=FakeProvider("Usa la martingala y apuesta más.")):
            self.assertEqual(self.ask("reglas de la ruleta").data["reply"]["source"], "guard")


class HU68ProviderConfigTests(APITestCase):
    def test_hu68_without_key_there_is_no_provider(self):
        with override_settings(ASSISTANT_PROVIDER="gemini", ASSISTANT_API_KEY=""):
            self.assertIsNone(providers.get_provider())
        with override_settings(ASSISTANT_PROVIDER="faq", ASSISTANT_API_KEY="clave"):
            self.assertIsNone(providers.get_provider())

    def test_hu68_provider_is_selected_from_settings(self):
        with override_settings(ASSISTANT_PROVIDER="gemini", ASSISTANT_API_KEY="clave", ASSISTANT_MODEL=""):
            self.assertIsInstance(providers.get_provider(), providers.GeminiProvider)
        with override_settings(ASSISTANT_PROVIDER="openai", ASSISTANT_API_KEY="clave", ASSISTANT_MODEL="mi-modelo"):
            provider = providers.get_provider()
            self.assertIsInstance(provider, providers.OpenAIProvider)
            self.assertEqual(provider.model, "mi-modelo")

    def fake_urlopen(self, body):
        captured = {}

        def urlopen(request, timeout):
            captured["url"], captured["headers"], captured["timeout"] = request.full_url, dict(request.headers), timeout
            captured["payload"] = json.loads(request.data)
            return io.BytesIO(json.dumps(body).encode())
        return urlopen, captured

    def test_hu68_gemini_client_builds_request_without_network(self):
        urlopen, captured = self.fake_urlopen({"candidates": [{"content": {"parts": [{"text": "Hola."}]}}]})
        with patch("assistant.providers.urllib.request.urlopen", urlopen):
            text = providers.GeminiProvider("clave", "modelo-x").complete("sistema", [("USER", "a")], "b")
        self.assertEqual(text, "Hola.")
        self.assertIn("models/modelo-x:generateContent", captured["url"])
        self.assertEqual(captured["headers"]["X-goog-api-key"], "clave")
        self.assertEqual(captured["payload"]["contents"][-1]["parts"][0]["text"], "b")

    def test_hu68_openai_client_builds_request_and_reports_bad_answers(self):
        urlopen, captured = self.fake_urlopen({"choices": [{"message": {"content": " Listo. "}}]})
        with patch("assistant.providers.urllib.request.urlopen", urlopen):
            self.assertEqual(providers.OpenAIProvider("clave").complete("sistema", [], "hola"), "Listo.")
        self.assertEqual(captured["headers"]["Authorization"], "Bearer clave")
        self.assertEqual(captured["payload"]["messages"][0], {"role": "system", "content": "sistema"})
        urlopen, _ = self.fake_urlopen({"error": "sin cuota"})
        with patch("assistant.providers.urllib.request.urlopen", urlopen), self.assertRaises(providers.ProviderError):
            providers.OpenAIProvider("clave").complete("sistema", [], "hola")
