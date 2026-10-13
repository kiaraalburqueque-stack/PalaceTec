from http.cookies import SimpleCookie, CookieError

from channels.db import database_sync_to_async
from django.conf import settings
from django.contrib.auth.models import AnonymousUser
from rest_framework.exceptions import AuthenticationFailed
from rest_framework_simplejwt.exceptions import InvalidToken, TokenError
from accounts.authentication import CookieJWTAuthentication


@database_sync_to_async
def cookie_user(headers):
    cookies = SimpleCookie()
    try:
        cookies.load(dict(headers).get(b"cookie", b"").decode("latin1"))
        token = cookies.get(settings.JWT_ACCESS_COOKIE)
        if token is None:
            return AnonymousUser()
        auth = CookieJWTAuthentication()
        return auth.get_user(auth.get_validated_token(token.value))
    except (CookieError, AuthenticationFailed, InvalidToken, TokenError):
        return AnonymousUser()


class CookieJWTMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        scope = dict(scope)
        scope["user"] = await cookie_user(scope.get("headers", []))
        return await self.app(scope, receive, send)
