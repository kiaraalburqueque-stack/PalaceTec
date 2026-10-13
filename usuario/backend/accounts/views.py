from datetime import timedelta
from math import ceil

from django.conf import settings
from django.db import IntegrityError, transaction
from django.utils import timezone
from rest_framework import status
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import AccessToken

from .authentication import CookieJWTAuthentication
from .models import User
from .serializers import LoginSerializer, RegisterSerializer, ProfileSerializer


class LoginView(APIView):
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
            user.failed_attempts = 0
            user.locked_until = None
            user.save(update_fields=["failed_attempts", "locked_until"])
            token = AccessToken.for_user(user)
        response = Response({"id": user.pk, "email": user.email})
        response.set_cookie(
            settings.JWT_ACCESS_COOKIE, str(token),
            max_age=int(settings.SIMPLE_JWT["ACCESS_TOKEN_LIFETIME"].total_seconds()),
            httponly=True, samesite="Lax", secure=not settings.DEBUG,
        )
        return response


class MeView(APIView):
    authentication_classes = [CookieJWTAuthentication]
    permission_classes = [IsAuthenticated]

    def get(self, request):
        return Response({"id": request.user.pk, "email": request.user.email})


class RegisterView(APIView):
    permission_classes = [AllowAny]
    authentication_classes = []

    def post(self, request):
        serializer = RegisterSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        email = User.objects.normalize_email(data["email"])
        conflicts = {}
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
