from django.conf import settings
from rest_framework.throttling import SimpleRateThrottle


class LoginRateThrottle(SimpleRateThrottle):
    scope = "login"

    def get_rate(self):
        return settings.LOGIN_RATE_LIMIT

    def get_cache_key(self, request, view):
        # Limita tambien correos inexistentes, ademas del bloqueo por cuenta.
        return self.cache_format % {"scope": self.scope, "ident": self.get_ident(request)}