from django.db.models import Count, Q
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from accounts.authentication import CookieJWTAuthentication
from .models import Room
from .state import room_state


class AuthenticatedView(APIView):
    authentication_classes = [CookieJWTAuthentication]
    permission_classes = [IsAuthenticated]


class RoomListView(AuthenticatedView):
    def get(self, request):
        rooms = Room.objects.filter(is_active=True).annotate(
            players=Count("connections__user", distinct=True, filter=Q(connections__expires_at__gt=timezone.now()))
        ).order_by("id")
        return Response([{
            "id": room.pk, "name": room.name, "min_bet": str(room.min_bet),
            "max_bet": str(room.max_bet), "players": room.players,
        } for room in rooms])


class RoomStateView(AuthenticatedView):
    def get(self, request, room_id):
        get_object_or_404(Room, pk=room_id, is_active=True)
        return Response(room_state(room_id))
