from django.db.models import Count, Q, Min, Max
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from accounts.authentication import CookieJWTAuthentication
from .models import Room, Game
from .state import room_state


class AuthenticatedView(APIView):
    authentication_classes = [CookieJWTAuthentication]
    permission_classes = [IsAuthenticated]


class RoomListView(AuthenticatedView):
    def get(self, request):
        rooms = Room.objects.available().annotate(
            players=Count("connections__user", distinct=True, filter=Q(connections__expires_at__gt=timezone.now()))
        ).order_by("id")
        if "game" in request.query_params:
            from rest_framework import serializers
            game_id = serializers.IntegerField(min_value=1).run_validation(request.query_params["game"])
            rooms = rooms.filter(game_id=game_id)
        return Response([{
            "id": room.pk, "name": room.name, "min_bet": str(room.min_bet),
            "max_bet": str(room.max_bet), "players": room.players,
        } for room in rooms])


class RoomStateView(AuthenticatedView):
    def get(self, request, room_id):
        get_object_or_404(Room.objects.available(), pk=room_id)
        return Response(room_state(room_id))


class BetView(AuthenticatedView):
    def get(self, request, room_id):
        from .models import Bet
        from .serializers import BetSerializer
        get_object_or_404(Room.objects.available(), pk=room_id)
        bets = Bet.objects.filter(user=request.user, round__room_id=room_id).order_by("-id")[:100]
        return Response(BetSerializer(bets, many=True).data)

    def post(self, request, room_id):
        from wallet.services import InsufficientBalance, ReferenceConflict
        from .services import RouletteService, BetsClosed
        from .serializers import BetSerializer
        get_object_or_404(Room.objects.available(), pk=room_id)
        try:
            bet, created = RouletteService.place_bet(request.user, room_id, request.data, request.auth)
        except Room.DoesNotExist:
            return Response({"detail": "Sala no disponible."}, status=404)
        except InsufficientBalance as exc:
            return Response({"code": "insufficient_balance", "detail": str(exc)}, status=400)
        except BetsClosed as exc:
            return Response({"code": "bets_closed", "detail": str(exc)}, status=400)
        except ReferenceConflict as exc:
            return Response({"code": "reference_conflict", "detail": str(exc)}, status=409)
        return Response(BetSerializer(bet).data, status=201 if created else 200)


class GameListView(AuthenticatedView):
    def get(self, request):
        games = Game.objects.filter(is_published=True, rooms__is_active=True).annotate(
            min_bet=Min("rooms__min_bet", filter=Q(rooms__is_active=True)),
            max_bet=Max("rooms__max_bet", filter=Q(rooms__is_active=True))).order_by("id")
        return Response([{"id": game.pk, "slug": game.slug, "title": game.title,
            "min_bet": format(game.min_bet, ".2f"), "max_bet": format(game.max_bet, ".2f"),
            "rooms_url": f"/api/v1/rooms?game={game.pk}"} for game in games])


class PreviousBetsView(AuthenticatedView):
    def get(self, request, room_id):
        from .repeat import previous_round
        from .models import Bet
        from .serializers import BetSerializer
        get_object_or_404(Room.objects.available(), pk=room_id)
        source = previous_round(request.user, room_id)
        return Response({"source_round_id": source.pk if source else None,
            "bets": BetSerializer(Bet.objects.filter(user=request.user, round=source).order_by("id"), many=True).data if source else []})


class RepeatBetsView(AuthenticatedView):
    def post(self, request, room_id):
        from .repeat import repeat_bets
        from .services import BetsClosed
        from .serializers import BetSerializer
        from wallet.services import InsufficientBalance, ReferenceConflict
        get_object_or_404(Room.objects.available(), pk=room_id)
        try:
            operation, created = repeat_bets(request.user, room_id, request.data, request.auth)
        except Room.DoesNotExist:
            return Response({"detail": "Sala no disponible."}, status=404)
        except (InsufficientBalance, BetsClosed, ReferenceConflict) as exc:
            code = "insufficient_balance" if isinstance(exc, InsufficientBalance) else "bets_closed" if isinstance(exc, BetsClosed) else "reference_conflict"
            return Response({"code": code, "detail": str(exc)}, status=409 if code == "reference_conflict" else 400)
        return Response({"source_round_id": operation.source_round_id, "round_id": operation.target_round_id,
            "reference": str(operation.reference), "bets": BetSerializer(operation.bets.order_by("id"), many=True).data}, status=201 if created else 200)
