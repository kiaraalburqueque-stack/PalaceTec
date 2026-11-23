from django.conf import settings
from rest_framework.authentication import CSRFCheck
from rest_framework.exceptions import PermissionDenied
from rest_framework_simplejwt.authentication import JWTAuthentication
from rest_framework.exceptions import AuthenticationFailed
from django.utils import timezone
from datetime import datetime, timezone as datetime_timezone
from .models import AuthSession
from .sessions import session_key


class CookieJWTAuthentication(JWTAuthentication):
    """Valida el JWT recibido exclusivamente mediante la cookie HttpOnly."""

    def get_user(self, validated_token):
        user = super().get_user(validated_token)
        from responsible.services import ExclusionService
        ExclusionService.ensure_user_allowed(user)
        if validated_token.get("version", 0) != user.auth_version or validated_token["exp"] <= timezone.now().timestamp():
            raise AuthenticationFailed("Sesion no disponible.")
        session, _ = AuthSession.objects.get_or_create(
            jti=session_key(validated_token), defaults={"user": user,
            "started_at": datetime.fromtimestamp(validated_token["iat"], datetime_timezone.utc),
            "expires_at": datetime.fromtimestamp(validated_token["exp"], datetime_timezone.utc)})
        if session.user_id != user.pk or session.revoked_at or session.expires_at <= timezone.now():
            raise AuthenticationFailed("Sesion no disponible.")
        return user

    def authenticate(self, request):
        raw_token = request.COOKIES.get(settings.JWT_ACCESS_COOKIE)
        if not raw_token:
            return None
        validated_token = self.get_validated_token(raw_token)
        user = self.get_user(validated_token)
        if request.method not in ("GET", "HEAD", "OPTIONS"):
            enforce_csrf(request)
        return user, validated_token


def enforce_csrf(request):
    check = CSRFCheck(lambda request: None)
    check.process_request(request)
    reason = check.process_view(request, None, (), {})
    if reason:
        raise PermissionDenied("CSRF: " + reason)
