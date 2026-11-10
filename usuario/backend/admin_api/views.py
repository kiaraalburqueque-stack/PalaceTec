from django.db.models import Q

from accounts.models import User
from .base import AdminPagination, AdminView
from .serializers import AdminUserSerializer, UserListQuery


class AdminUserListView(AdminView):
    """HU41: listado paginado de usuarios con filtros por estado, rol y búsqueda."""

    def get(self, request):
        query = UserListQuery(data=request.query_params)
        query.is_valid(raise_exception=True)
        params = query.validated_data
        users = User.objects.select_related("wallet")
        if params.get("status") == "active":
            users = users.filter(is_active=True)
        elif params.get("status") == "suspended":
            users = users.filter(is_active=False)
        admin_q = Q(is_staff=True) | Q(is_superuser=True)
        if params.get("role") == "admin":
            users = users.filter(admin_q)
        elif params.get("role") == "user":
            users = users.exclude(admin_q)
        search = (params.get("search") or "").strip()
        if search:
            users = users.filter(Q(email__icontains=search) | Q(dni__startswith=search))
        users = users.order_by(params["ordering"], "id")
        pagination = AdminPagination()
        # LIMIT/OFFSET: la paginación solo trae las filas de la página pedida.
        page = pagination.paginate_queryset(users, request, view=self)
        return pagination.get_paginated_response(AdminUserSerializer(page, many=True).data)
