"""HTTP y WebSockets con cookie JWT y validacion del origen."""
import os
from django.core.asgi import get_asgi_application

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
django_asgi_application = get_asgi_application()

from channels.routing import ProtocolTypeRouter, URLRouter
from channels.security.websocket import OriginValidator
from django.conf import settings
from realtime.authentication import CookieJWTMiddleware
from roulette.routing import websocket_urlpatterns

application = ProtocolTypeRouter({
    "http": django_asgi_application,
    "websocket": OriginValidator(
        CookieJWTMiddleware(URLRouter(websocket_urlpatterns)),
        settings.WEBSOCKET_ALLOWED_ORIGINS,
    ),
})
