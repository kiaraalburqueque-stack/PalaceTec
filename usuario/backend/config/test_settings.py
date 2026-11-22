"""Pruebas aisladas: misma BD configurada, sin depender de Redis."""
from .settings import *  # noqa: F403

CHANNEL_LAYERS = {"default": {"BACKEND": "channels.layers.InMemoryChannelLayer"}}
CACHES = {"default": {"BACKEND": "django.core.cache.backends.locmem.LocMemCache"}}
# El limite real se prueba con override_settings, sin acumular intentos entre casos.
LOGIN_RATE_LIMIT = "1000/min"
# Las pruebas nunca llaman a un proveedor de IA real aunque el .env tenga una clave.
ASSISTANT_PROVIDER = "faq"
ASSISTANT_API_KEY = ""
