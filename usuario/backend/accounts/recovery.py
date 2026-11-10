"""HU55: restablecimiento de contraseña por correo con token de un solo uso (15 minutos)."""
import hashlib
import secrets
from datetime import timedelta

from django.conf import settings
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError as DjangoValidationError
from django.core.mail import send_mail
from django.db import transaction
from django.utils import timezone
from rest_framework import serializers
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from realtime.events import after_commit
from .authentication import enforce_csrf
from .models import AuthSession, PasswordResetToken, User
from .serializers import ForgotPasswordSerializer, ResetPasswordSerializer
from .throttles import LoginRateThrottle

RESET_LIFETIME = timedelta(minutes=15)
GENERIC = {"detail": "Si el correo está registrado, recibirás un enlace para restablecer tu contraseña."}
INVALID = {"code": "invalid_token", "detail": "El enlace no es válido, ya se usó o venció. Solicita uno nuevo."}


def token_hash(token):
    return hashlib.sha256(token.encode()).hexdigest()


def revoke_all_sessions(user):
    """Invalida JWT y sesiones abiertas; el usuario debe volver a iniciar sesión."""
    user.auth_version += 1
    jtis = list(AuthSession.objects.filter(user=user, revoked_at__isnull=True).values_list("jti", flat=True))
    AuthSession.objects.filter(user=user, revoked_at__isnull=True).update(revoked_at=timezone.now())
    for jti in jtis:
        after_commit(f"session.{jti}", "session:revoked", {})


def send_reset_email(user, token):
    link = f"{settings.FRONTEND_URL}/reset-password/{token}"
    send_mail(
        "PalaceTec · Restablece tu contraseña",
        f"Hola {user.nickname or user.email},\n\n"
        f"Recibimos una solicitud para restablecer tu contraseña. Abre este enlace en los próximos 15 minutos:\n\n{link}\n\n"
        "Si no fuiste tú, ignora este mensaje: tu contraseña actual sigue siendo válida.\n\n— PalaceTec",
        settings.DEFAULT_FROM_EMAIL, [user.email])


class ForgotPasswordView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]
    throttle_classes = [LoginRateThrottle]

    def post(self, request):
        enforce_csrf(request)
        data = ForgotPasswordSerializer(data=request.data)
        data.is_valid(raise_exception=True)
        email = User.objects.normalize_email(data.validated_data["email"])
        user = User.objects.filter(email=email, is_active=True).first()
        if user is not None:
            token = secrets.token_urlsafe(32)
            with transaction.atomic():
                # Un enlace nuevo invalida los anteriores que no se usaron.
                PasswordResetToken.objects.filter(user=user, used_at__isnull=True).update(used_at=timezone.now())
                PasswordResetToken.objects.create(user=user, token_hash=token_hash(token),
                                                  expires_at=timezone.now() + RESET_LIFETIME)
                transaction.on_commit(lambda: send_reset_email(user, token))
        # Misma respuesta exista o no la cuenta: no revela correos registrados.
        return Response(GENERIC)


class ResetPasswordView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]
    throttle_classes = [LoginRateThrottle]

    def post(self, request):
        enforce_csrf(request)
        data = ResetPasswordSerializer(data=request.data)
        data.is_valid(raise_exception=True)
        with transaction.atomic():
            record = (PasswordResetToken.objects.select_for_update().select_related("user")
                      .filter(token_hash=token_hash(data.validated_data["token"])).first())
            now = timezone.now()
            if record is None or record.used_at or record.expires_at <= now or not record.user.is_active:
                return Response(INVALID, status=400)
            user = User.objects.select_for_update().get(pk=record.user_id)
            password = data.validated_data["new_password"]
            if user.check_password(password):
                return Response({"new_password": ["La nueva contraseña debe ser distinta de la anterior."]}, status=400)
            try:
                validate_password(password, user=user)
            except DjangoValidationError as exc:
                raise serializers.ValidationError({"new_password": exc.messages}) from exc
            user.set_password(password)
            user.failed_attempts = 0
            user.locked_until = None
            revoke_all_sessions(user)
            user.save(update_fields=["password", "failed_attempts", "locked_until", "auth_version"])
            record.used_at = now
            record.save(update_fields=["used_at"])
        return Response({"detail": "Tu contraseña fue actualizada. Ya puedes iniciar sesión."})
