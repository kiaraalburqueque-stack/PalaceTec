from rest_framework import serializers
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
from accounts.authentication import CookieJWTAuthentication
from accounts.permissions import IsAdminRole
from .models import ExcludedPerson
from .services import ExclusionService


class ResponsibleView(APIView):
    authentication_classes = [CookieJWTAuthentication]
    permission_classes = [IsAuthenticated]


class DailyLimitInput(serializers.Serializer):
    amount = serializers.DecimalField(max_digits=12, decimal_places=2, min_value=0)


class DailyLimitView(ResponsibleView):
    def get(self, request):
        from .services import LimitService
        return Response(LimitService.summary(request.user))

    def post(self, request):
        from .services import LimitService
        serializer = DailyLimitInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        return Response(LimitService.set_limit(request.user, serializer.validated_data["amount"]))


class ExcludedPersonInput(serializers.Serializer):
    dni = serializers.RegexField(r"\A[0-9]{8}\Z", trim_whitespace=False, write_only=True)
    source = serializers.CharField(max_length=120)
    reason = serializers.CharField(max_length=255, required=False, allow_blank=True)


class ExcludedPeopleView(ResponsibleView):
    permission_classes = [IsAuthenticated, IsAdminRole]

    def post(self, request):
        from django.db import transaction
        from accounts.models import User, AuthSession
        from django.utils import timezone
        from realtime.events import after_commit
        serializer = ExcludedPersonInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        with transaction.atomic():
            # Mismo candado que usa place_bet: una apuesta aceptada antes se liquida.
            users = list(User.objects.select_for_update().filter(dni=data["dni"]))
            excluded, created = ExcludedPerson.objects.get_or_create(
                dni_hash=ExclusionService.digest(data["dni"]),
                defaults={"source": data["source"], "reason": data.get("reason", "")})
            for user in users:
                AuthSession.objects.filter(user=user, revoked_at__isnull=True).update(revoked_at=timezone.now())
                after_commit(f"user.{user.pk}", "session:revoked", {})
        return Response({"id": excluded.pk, "created": created}, status=201 if created else 200)
