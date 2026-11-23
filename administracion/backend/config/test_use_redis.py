"""USE_REDIS: elige Redis o memoria para Channels y la cache al cargar config.settings."""
import json
import os
import subprocess
import sys
from pathlib import Path

from django.test import SimpleTestCase

BASE_DIR = Path(__file__).resolve().parent.parent
PROBE = (
    "import json; from config import settings as s; "
    "print(json.dumps([s.USE_REDIS, s.CHANNEL_LAYERS['default']['BACKEND'], s.CACHES['default']['BACKEND']]))"
)
MEMORY = ["channels.layers.InMemoryChannelLayer", "django.core.cache.backends.locmem.LocMemCache"]
REDIS = ["channels_redis.core.RedisChannelLayer", "django.core.cache.backends.redis.RedisCache"]


def load_settings(**env):
    """Carga config.settings en un proceso aparte, con solo las variables indicadas (sin el .env real)."""
    clean = {k: v for k, v in os.environ.items() if k not in ("USE_REDIS", "REDIS_URL", "DB_ENGINE")}
    clean.update(env)
    # load_dotenv no pisa variables existentes: las vacías evitan que el .env local cambie el resultado.
    for name in ("USE_REDIS", "REDIS_URL", "DB_ENGINE"):
        clean.setdefault(name, "")
    output = subprocess.run([sys.executable, "-c", PROBE], cwd=BASE_DIR, env=clean, capture_output=True,
                            text=True, check=True).stdout
    use_redis, *backends = json.loads(output.strip().splitlines()[-1])
    return use_redis, backends


class UseRedisSettingsTests(SimpleTestCase):
    def test_default_uses_redis_with_postgresql(self):
        self.assertEqual(load_settings(DB_ENGINE="postgresql", USE_REDIS="true"), (True, REDIS))

    def test_empty_use_redis_counts_as_default_true(self):
        self.assertEqual(load_settings(DB_ENGINE="postgresql")[1], REDIS)

    def test_use_redis_false_uses_memory_with_postgresql(self):
        for value in ("false", "False", "0", "no", "off"):
            with self.subTest(value=value):
                self.assertEqual(load_settings(DB_ENGINE="postgresql", USE_REDIS=value), (False, MEMORY))

    def test_use_redis_false_wins_over_redis_url(self):
        self.assertEqual(load_settings(DB_ENGINE="postgresql", USE_REDIS="false",
                                       REDIS_URL="redis://127.0.0.1:6379/0"), (False, MEMORY))

    def test_sqlite_without_redis_url_keeps_memory_fallback(self):
        self.assertEqual(load_settings(DB_ENGINE="sqlite")[1], MEMORY)

    def test_sqlite_with_redis_url_uses_redis(self):
        self.assertEqual(load_settings(DB_ENGINE="sqlite", REDIS_URL="redis://127.0.0.1:6379/0")[1], REDIS)
