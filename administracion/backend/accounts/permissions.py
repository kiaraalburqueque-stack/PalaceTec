from rest_framework.permissions import BasePermission


class IsAdminRole(BasePermission):
    message = "No tienes permiso para acceder a administracion."

    def has_permission(self, request, view):
        return bool(request.user and request.user.is_authenticated and request.user.is_active
                    and (request.user.is_staff or request.user.is_superuser))
