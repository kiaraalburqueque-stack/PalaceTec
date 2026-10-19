from datetime import timedelta
from math import ceil

from django.conf import settings
from django.db import IntegrityError, transaction
from django.utils import timezone
from rest_framework import status
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import AccessToken, RefreshToken

from .authentication import CookieJWTAuthentication, enforce_csrf
from .throttles import LoginRateThrottle
from django.middleware.csrf import get_token
from .models import User, AuthSession
from .serializers import LoginSerializer, RegisterSerializer, ProfileSerializer
from .sessions import session_key, set_access_cookie, clear_session_cookies


class LoginView(APIView):
    throttle_classes = [LoginRateThrottle]
    permission_classes = [AllowAny]
    authentication_classes = []

    @staticmethod
    def locked_response(user, now):
        remaining = max(1, ceil((user.locked_until - now).total_seconds()))
        return Response(
            {"detail": "Cuenta bloqueada temporalmente.", "retry_after": remaining},
            status=status.HTTP_429_TOO_MANY_REQUESTS,
            headers={"Retry-After": str(remaining)},
        )

    def post(self, request):
        enforce_csrf(request)
        serializer = LoginSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        email = User.objects.normalize_email(data["email"])
        invalid = {"detail": "Credenciales inválidas"}
        # Serializa los intentos de una misma cuenta para no perder incrementos.
        with transaction.atomic():
            user = User.objects.select_for_update().filter(email=email).first()
            if user is None:
                # Ejecuta también un hash para reducir diferencias de tiempo.
                User().set_password(data["password"])
                return Response(invalid, status=status.HTTP_401_UNAUTHORIZED)
            now = timezone.now()
            if user.locked_until and user.locked_until > now:
                return self.locked_response(user, now)
            if user.locked_until:
                user.failed_attempts = 0
                user.locked_until = None
            if not user.check_password(data["password"]) or not user.is_active:
                user.failed_attempts += 1
                if user.failed_attempts >= 5:
                    user.locked_until = now + timedelta(minutes=15)
                user.save(update_fields=["failed_attempts", "locked_until"])
                if user.locked_until:
                    return self.locked_response(user, now)
                return Response(invalid, status=status.HTTP_401_UNAUTHORIZED)
            from responsible.services import ExclusionService
            ExclusionService.ensure_user_allowed(user)
            user.failed_attempts = 0
            user.locked_until = None
            user.save(update_fields=["failed_attempts", "locked_until"])
            token = AccessToken.for_user(user)
            token["version"] = user.auth_version
            token["sid"] = token["jti"]
            refresh = RefreshToken.for_user(user)
            refresh.set_exp(lifetime=settings.AUTH_SESSION_LIFETIME)
            refresh["sid"] = token["sid"]
            refresh["version"] = user.auth_version
            from datetime import datetime, timezone as datetime_timezone
            AuthSession.objects.create(user=user, jti=token["jti"], started_at=now,
                expires_at=datetime.fromtimestamp(token["exp"], datetime_timezone.utc),
                refresh_expires_at=datetime.fromtimestamp(refresh["exp"], datetime_timezone.utc))
        response = Response({"id": user.pk, "email": user.email})
        set_access_cookie(response, token)
        response.set_cookie(settings.JWT_REFRESH_COOKIE, str(refresh), path="/api/v1/auth/",
            max_age=int(settings.AUTH_SESSION_LIFETIME.total_seconds()),
            httponly=True, samesite="Lax", secure=not settings.DEBUG)
        return response


class MeView(APIView):
    authentication_classes = [CookieJWTAuthentication]
    permission_classes = [IsAuthenticated]

    def get(self, request):
        return Response({"id": request.user.pk, "email": request.user.email,
                         "role": "ADMIN" if request.user.is_staff or request.user.is_superuser else "USER"})


class AdminStatusView(MeView):
    from .permissions import IsAdminRole
    permission_classes = [IsAuthenticated, IsAdminRole]

    def get(self, request):
        return Response({"status": "ok", "role": "ADMIN"})


class RegisterView(APIView):
    permission_classes = [AllowAny]
    authentication_classes = []

    def post(self, request):
        enforce_csrf(request)
        serializer = RegisterSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        email = User.objects.normalize_email(data["email"])
        conflicts = {}
        from responsible.services import ExclusionService
        ExclusionService.ensure_allowed(data["dni"])
        if User.objects.filter(email=email).exists():
            conflicts["email"] = ["Este correo ya está registrado."]
        if User.objects.filter(dni=data["dni"]).exists():
            conflicts["dni"] = ["Este DNI ya está registrado."]
        if conflicts:
            return Response(conflicts, status=status.HTTP_409_CONFLICT)
        try:
            # El bloque se revierte antes de responder si otra solicitud gana la carrera.
            with transaction.atomic():
                user = serializer.save()
        except IntegrityError:
            return Response(
                {"detail": "El correo o el DNI ya están registrados."},
                status=status.HTTP_409_CONFLICT,
            )
        return Response({"id": user.pk, "email": user.email}, status=status.HTTP_201_CREATED)


class ProfileView(APIView):
    authentication_classes = [CookieJWTAuthentication]
    permission_classes = [IsAuthenticated]

    def get(self, request):
        return Response(ProfileSerializer(request.user).data)

    def put(self, request):
        serializer = ProfileSerializer(request.user, data=request.data)
        serializer.is_valid(raise_exception=True)
        try:
            with transaction.atomic():
                serializer.save()
        except IntegrityError:
            return Response({"nickname": ["Este apodo ya esta registrado."]}, status=409)
        return Response(serializer.data)


class CSRFView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        return Response({"csrfToken": get_token(request)})


class ChangePasswordView(MeView):
    def put(self, request):
        from .serializers import ChangePasswordSerializer
        from realtime.events import after_commit
        with transaction.atomic():
            user = User.objects.select_for_update().get(pk=request.user.pk)
            if request.auth:
                CookieJWTAuthentication().get_user(request.auth)
            serializer = ChangePasswordSerializer(data=request.data, context={"user": user})
            serializer.is_valid(raise_exception=True)
            user.set_password(serializer.validated_data["new_password"])
            user.auth_version += 1
            user.save(update_fields=["password", "auth_version"])
            sessions = list(AuthSession.objects.filter(user=user, revoked_at__isnull=True).values_list("jti", flat=True))
            AuthSession.objects.filter(user=user, revoked_at__isnull=True).update(revoked_at=timezone.now())
            for jti in sessions:
                after_commit(f"session.{jti}", "session:revoked", {})
        response = Response({"detail": "Contraseña actualizada. Inicia sesion nuevamente."})
        clear_session_cookies(response)
        return response


class LogoutView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        enforce_csrf(request)
        from rest_framework_simplejwt.exceptions import TokenError
        from datetime import datetime, timezone as datetime_timezone
        from realtime.events import after_commit
        raw = request.COOKIES.get(settings.JWT_ACCESS_COOKIE)
        try:
            token = AccessToken(raw) if raw else None
        except TokenError:
            token = None
        if token is None:
            try:
                raw_refresh = request.COOKIES.get(settings.JWT_REFRESH_COOKIE)
                token = RefreshToken(raw_refresh) if raw_refresh else None
            except TokenError:
                token = None
        if token and User.objects.filter(pk=token["user_id"]).exists():
            with transaction.atomic():
                # El mismo bloqueo de usuario serializa logout y escrituras privadas.
                User.objects.select_for_update().get(pk=token["user_id"])
                session, _ = AuthSession.objects.get_or_create(jti=session_key(token), defaults={
                    "user_id": token["user_id"],
                    "started_at": datetime.fromtimestamp(token["iat"], datetime_timezone.utc),
                    "expires_at": datetime.fromtimestamp(token["exp"], datetime_timezone.utc)})
                session.revoked_at = timezone.now()
                session.save(update_fields=["revoked_at"])
                after_commit(f"session.{session_key(token)}", "session:revoked", {})
        response = Response({"detail": "Sesion cerrada."})
        clear_session_cookies(response)
        return response


class RefreshSessionView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]
    throttle_classes = [LoginRateThrottle]

    def get_authenticate_header(self, request):
        return 'Bearer realm="api"'

    def post(self, request):
        from datetime import datetime, timezone as datetime_timezone
        from rest_framework_simplejwt.exceptions import TokenError
        from rest_framework.exceptions import AuthenticationFailed
        from responsible.services import ExclusionService
        enforce_csrf(request)
        try:
            raw = request.COOKIES.get(settings.JWT_REFRESH_COOKIE)
            if not raw:
                raise AuthenticationFailed("Inicia sesion nuevamente.")
            refresh = RefreshToken(raw)
        except TokenError as exc:
            raise AuthenticationFailed("Inicia sesion nuevamente.") from exc
        with transaction.atomic():
            user = User.objects.select_for_update().filter(pk=refresh["user_id"]).first()
            if user is None or refresh.get("version", 0) != user.auth_version:
                raise AuthenticationFailed("Inicia sesion nuevamente.")
            ExclusionService.ensure_user_allowed(user)
            session = AuthSession.objects.filter(jti=session_key(refresh), user=user,
                revoked_at__isnull=True, refresh_expires_at__gt=timezone.now()).first()
            if session is None:
                raise AuthenticationFailed("Inicia sesion nuevamente.")
            access = AccessToken.for_user(user)
            access["sid"], access["version"] = session.jti, user.auth_version
            session.expires_at = datetime.fromtimestamp(access["exp"], datetime_timezone.utc)
            session.save(update_fields=["expires_at"])
        response = Response({"detail": "Sesion renovada.", "session_started_at": session.started_at.isoformat()})
        set_access_cookie(response, access)
        return response
