import re
from django.conf import settings
from django.utils.crypto import salted_hmac
from rest_framework.exceptions import PermissionDenied
from .models import ExcludedPerson
from datetime import timedelta
from decimal import Decimal
from zoneinfo import ZoneInfo
from django.db import transaction
from django.utils import timezone
from accounts.models import User
from rest_framework.exceptions import APIException
from .models import DailyLimit, DailySpending


class ExclusionService:
    @staticmethod
    def digest(dni):
        if not isinstance(dni, str) or not re.fullmatch(r"[0-9]{8}", dni):
            raise ValueError("El DNI debe tener ocho digitos.")
        return salted_hmac("palacetec.exclusions", dni, secret=settings.EXCLUSION_HMAC_KEY, algorithm="sha256").hexdigest()

    @classmethod
    def is_excluded(cls, dni):
        return ExcludedPerson.objects.filter(dni_hash=cls.digest(dni)).exists()

    @classmethod
    def ensure_allowed(cls, dni):
        if cls.is_excluded(dni):
            raise PermissionDenied({"code": "access_not_allowed", "detail": "No es posible habilitar el acceso."})

    @classmethod
    def ensure_user_allowed(cls, user):
        cls.ensure_allowed(user.dni)
        if not user.is_active or (user.self_excluded_until and user.self_excluded_until > timezone.now()):
            raise PermissionDenied({"code": "access_not_allowed", "detail": "No es posible habilitar el acceso."})


class DailyLimitError(APIException):
    status_code = 400
    default_code = "daily_limit_exceeded"

    def __init__(self, code, detail):
        super().__init__({"code": code, "detail": detail})


class LimitService:
    @staticmethod
    def day(now=None):
        return (now or timezone.now()).astimezone(ZoneInfo("America/Lima")).date()

    @staticmethod
    def activate(limit, now):
        if limit.effective_at and limit.effective_at <= now:
            limit.amount, limit.pending_amount, limit.effective_at = limit.pending_amount, None, None
            limit.save(update_fields=["amount", "pending_amount", "effective_at"])

    @classmethod
    @transaction.atomic
    def summary(cls, user):
        User.objects.select_for_update().get(pk=user.pk)
        limit, _ = DailyLimit.objects.get_or_create(user=user)
        now = timezone.now()
        cls.activate(limit, now)
        spent = DailySpending.objects.filter(user=user, date=cls.day(now)).first()
        amount = spent.amount if spent else Decimal("0")
        return {"configured": limit.amount is not None, "limit": format(limit.amount, ".2f") if limit.amount is not None else None,
                "spent": format(amount, ".2f"), "remaining": format(max(Decimal("0"), limit.amount-amount), ".2f") if limit.amount is not None else None,
                "pending_limit": format(limit.pending_amount, ".2f") if limit.pending_amount is not None else None,
                "effective_at": limit.effective_at.isoformat() if limit.effective_at else None,
                "date": cls.day(now).isoformat(), "timezone": "America/Lima"}

    @classmethod
    @transaction.atomic
    def set_limit(cls, user, amount):
        User.objects.select_for_update().get(pk=user.pk)
        limit, _ = DailyLimit.objects.get_or_create(user=user)
        now = timezone.now()
        cls.activate(limit, now)
        if limit.amount is None or amount <= limit.amount:
            limit.amount, limit.pending_amount, limit.effective_at = amount, None, None
        elif limit.pending_amount != amount:
            limit.pending_amount, limit.effective_at = amount, now+timedelta(hours=24)
        limit.save()
        return cls.summary(user)

    @classmethod
    @transaction.atomic
    def check_and_reserve(cls, user, amount):
        # Un bloqueo por usuario serializa apuestas incluso entre salas diferentes.
        User.objects.select_for_update().get(pk=user.pk)
        limit = DailyLimit.objects.filter(user=user).first()
        if limit is None or limit.amount is None:
            raise DailyLimitError("daily_limit_required", "Define tu limite diario antes de apostar.")
        now = timezone.now()
        cls.activate(limit, now)
        spending, _ = DailySpending.objects.get_or_create(user=user, date=cls.day(now))
        if spending.amount + amount > limit.amount:
            raise DailyLimitError("daily_limit_exceeded", "La apuesta supera tu limite diario disponible.")
        spending.amount += amount
        spending.save(update_fields=["amount"])
