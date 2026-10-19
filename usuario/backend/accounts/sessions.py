from django.conf import settings


def session_key(token):
    return token.get("sid", token["jti"])


def set_access_cookie(response, token):
    response.set_cookie(settings.JWT_ACCESS_COOKIE, str(token),
        max_age=int(settings.SIMPLE_JWT["ACCESS_TOKEN_LIFETIME"].total_seconds()),
        httponly=True, samesite="Lax", secure=not settings.DEBUG)


def clear_session_cookies(response):
    response.delete_cookie(settings.JWT_ACCESS_COOKIE, samesite="Lax")
    response.delete_cookie(settings.JWT_REFRESH_COOKIE, path="/api/v1/auth/", samesite="Lax")
