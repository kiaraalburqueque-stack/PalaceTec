import re
from django.conf import settings
from django.utils.crypto import salted_hmac
from rest_framework.exceptions import PermissionDenied
from .models import ExcludedPerson


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
