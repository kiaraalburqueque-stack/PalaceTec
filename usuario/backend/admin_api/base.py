"""Piezas comunes de la API de administración (/api/v1/admin)."""
from rest_framework.pagination import PageNumberPagination
from rest_framework.permissions import IsAuthenticated
from rest_framework.views import APIView

from accounts.authentication import CookieJWTAuthentication
from accounts.permissions import IsAdminRole


class AdminPagination(PageNumberPagination):
    """Diez filas por página, como pide la HU41."""
    page_size = 10
    page_size_query_param = "page_size"
    max_page_size = 100


class AdminView(APIView):
    authentication_classes = [CookieJWTAuthentication]
    permission_classes = [IsAuthenticated, IsAdminRole]


def client_ip(request):
    # Conexión directa (NUM_PROXIES = 0): no se confía en X-Forwarded-For.
    return request.META.get("REMOTE_ADDR") or None
