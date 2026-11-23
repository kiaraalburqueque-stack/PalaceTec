"""HU50: consulta del registro de auditoría (solo lectura)."""
from django.db.models import Q
from rest_framework import serializers

from .base import AdminPagination, AdminView
from .models import AuditLog


class AuditLogQuery(serializers.Serializer):
    search = serializers.CharField(required=False, allow_blank=True, max_length=254)
    method = serializers.ChoiceField(choices=["POST", "PUT", "PATCH", "DELETE"], required=False)


class AuditLogListView(AdminView):
    def get(self, request):
        query = AuditLogQuery(data=request.query_params)
        query.is_valid(raise_exception=True)
        logs = AuditLog.objects.select_related("admin")
        if query.validated_data.get("method"):
            logs = logs.filter(method=query.validated_data["method"])
        search = (query.validated_data.get("search") or "").strip()
        if search:
            logs = logs.filter(Q(admin__email__icontains=search) | Q(action__icontains=search)
                               | Q(resource__icontains=search) | Q(ip_address__startswith=search))
        pagination = AdminPagination()
        page = pagination.paginate_queryset(logs, request, view=self)
        return pagination.get_paginated_response([{
            "id": log.pk, "created_at": log.created_at.isoformat(), "admin_id": log.admin_id, "admin": log.admin.email,
            "action": log.action, "method": log.method, "resource": log.resource, "status_code": log.status_code,
            "ip_address": log.ip_address, "details": log.details} for log in page])
