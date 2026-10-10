from django.db import IntegrityError, transaction
from rest_framework import status
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import User
from .serializers import RegisterSerializer


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
