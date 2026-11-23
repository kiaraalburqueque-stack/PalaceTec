"""HU50: registra cada POST, PUT, PATCH y DELETE hecho por un administrador."""
import json
import logging

from django.urls import Resolver404, resolve

from .base import client_ip

logger = logging.getLogger(__name__)
MUTATING = {"POST", "PUT", "PATCH", "DELETE"}
SECRET_KEYS = ("password", "token", "secret", "cover")
SKIPPED_PATHS = ("/api/v1/auth/csrf", "/api/v1/auth/refresh")


def safe_details(request):
    """Guarda solo los campos enviados en JSON, sin contraseñas ni archivos, recortados."""
    if not request.content_type or "json" not in request.content_type:
        return {"fields": sorted(request.POST.keys()) if request.method == "POST" else []}
    try:
        body = json.loads(request._audit_body or b"{}")
    except (ValueError, TypeError):
        return {}
    if not isinstance(body, dict):
        return {}
    return {key: ("***" if any(word in key.lower() for word in SECRET_KEYS) else str(value)[:200])
            for key, value in list(body.items())[:20]}


class AdminAuditMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        if request.method in MUTATING:
            # El cuerpo se lee antes de la vista: después ya no estaría disponible.
            try:
                request._audit_body = request.body[:4096] if "json" in (request.content_type or "") else b""
            except Exception:  # RawPostDataException en subidas ya consumidas
                request._audit_body = b""
        response = self.get_response(request)
        if request.method in MUTATING and not request.path.startswith(SKIPPED_PATHS):
            user = getattr(request, "user", None)
            if user is not None and getattr(user, "is_authenticated", False) and (user.is_staff or user.is_superuser):
                self.record(request, response, user)
        return response

    def record(self, request, response, user):
        from .models import AuditLog
        try:
            match = resolve(request.path_info)
            name = match.url_name or match.view_name
        except Resolver404:
            name = "unknown"
        try:
            AuditLog.objects.create(admin=user, action=f"{request.method} {name}", method=request.method,
                                    resource=request.path[:255], status_code=response.status_code,
                                    ip_address=client_ip(request), details=safe_details(request))
        except Exception:
            logger.exception("No se pudo registrar la auditoría de %s %s", request.method, request.path)
