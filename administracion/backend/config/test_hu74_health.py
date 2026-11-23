"""HU74: monitor de salud de la base de datos, Redis y la capa de WebSockets."""
from unittest.mock import patch

from django.test import TestCase
from rest_framework.test import APIClient

from accounts.models import User
from config import health
from wallet.models import Wallet

URL = "/healthcheck"


def redis_ok():
    return None


def redis_down():
    raise ConnectionRefusedError("Error 10061 connecting to 127.0.0.1:6379")


class HU74HealthcheckTests(TestCase):
    def setUp(self):
        self.client = APIClient()

    def get(self, redis=redis_ok, **extra_checks):
        with patch.dict(health.CHECKS, {"redis": redis, **extra_checks}):
            return self.client.get(URL)

    def login_admin(self):
        user = User.objects.create_user(email="admin74@palacetec.pe", dni="74747474", birth_date="2000-01-01",
                                        password="Palace123!", is_staff=True)
        Wallet.objects.create(user=user, balance=0)
        response = self.client.post("/api/v1/auth/login", {"email": user.email, "password": "Palace123!"}, format="json")
        self.assertEqual(response.status_code, 200)

    def test_hu74_all_services_ok_returns_200(self):
        response = self.get()
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual((data["status"], data["failed"]), ("ok", []))
        self.assertEqual({name: item["status"] for name, item in data["services"].items()},
                         {"database": "ok", "redis": "ok", "channels": "ok"})
        self.assertEqual(response["Cache-Control"], "no-store")

    def test_hu74_redis_down_returns_503_and_names_the_service(self):
        response = self.get(redis=redis_down)
        self.assertEqual(response.status_code, 503)
        data = response.json()
        self.assertEqual((data["status"], data["failed"]), ("error", ["redis"]))
        self.assertEqual(data["services"]["redis"], {"status": "error"})
        self.assertEqual(data["services"]["database"]["status"], "ok")

    def test_hu74_channels_or_database_failure_is_reported(self):
        def broken():
            raise RuntimeError("sin capa")
        self.assertEqual(self.get(channels=broken).json()["failed"], ["channels"])
        self.assertEqual(self.get(database=broken).status_code, 503)

    def test_hu74_real_database_and_channel_layer_checks(self):
        self.assertIsNone(health.check_database())
        self.assertIsNone(health.check_channels())  # en pruebas la capa es InMemoryChannelLayer

    def test_hu74_real_redis_check_fails_when_redis_is_unreachable(self):
        with self.settings(REDIS_URL="redis://127.0.0.1:1/0"):
            result = health.run_check(health.check_redis)
        self.assertEqual(result["status"], "error")
        self.assertTrue(result["error"])

    def test_hu74_public_response_hides_technical_detail(self):
        services = self.get(redis=redis_down).json()["services"]
        for item in services.values():
            self.assertEqual(set(item), {"status"})

    def test_hu74_admin_sees_latency_and_error(self):
        self.login_admin()
        redis = self.get(redis=redis_down).json()["services"]["redis"]
        self.assertEqual(redis["status"], "error")
        self.assertIn("10061", redis["error"])
        self.assertIsInstance(redis["latency_ms"], float)

    def test_hu74_use_redis_false_marks_redis_disabled_without_failing(self):
        def must_not_run():
            raise AssertionError("con USE_REDIS=false no se consulta Redis")
        with self.settings(USE_REDIS=False):
            response = self.get(redis=must_not_run)
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual((data["status"], data["failed"]), ("ok", []))
        self.assertEqual(data["services"]["redis"], {"status": "disabled"})

    def test_hu74_use_redis_false_still_reports_other_failures(self):
        def broken():
            raise RuntimeError("sin capa")
        with self.settings(USE_REDIS=False):
            response = self.get(channels=broken)
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json()["failed"], ["channels"])

    def test_hu74_player_does_not_see_detail_and_post_is_not_allowed(self):
        player = User.objects.create_user(email="p74@palacetec.pe", dni="74747475", birth_date="2000-01-01", password="Palace123!")
        Wallet.objects.create(user=player, balance=0)
        self.client.post("/api/v1/auth/login", {"email": player.email, "password": "Palace123!"}, format="json")
        self.assertEqual(set(self.get().json()["services"]["database"]), {"status"})
        self.assertEqual(self.client.post(URL).status_code, 405)
