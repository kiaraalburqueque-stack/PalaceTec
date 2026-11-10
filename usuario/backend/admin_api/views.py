from django.db import transaction
from django.db.models import Q
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework.response import Response

from accounts.models import AuthSession, User
from realtime.events import after_commit
from .base import AdminPagination, AdminView
from .models import SanctionLog
from roulette.models import Game, Room
from .serializers import AdminGameSerializer, AdminUserSerializer, UserListQuery, UserStatusInput


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


class AdminUserStatusView(AdminView):
    """HU42: suspende o activa una cuenta indicando el motivo."""

    def patch(self, request, user_id):
        data = UserStatusInput(data=request.data)
        data.is_valid(raise_exception=True)
        if user_id == request.user.pk:
            return Response({"detail": "No puedes cambiar el estado de tu propia cuenta."}, status=400)
        with transaction.atomic():
            user = get_object_or_404(User.objects.select_for_update(), pk=user_id)
            active = data.validated_data["is_active"]
            if user.is_superuser and not request.user.is_superuser:
                return Response({"detail": "Solo un superusuario puede sancionar a otro superusuario."}, status=403)
            if user.is_active != active:
                user.is_active = active
                fields = ["is_active"]
                if not active:
                    # Invalida todos los JWT emitidos y revoca las sesiones abiertas.
                    user.auth_version += 1
                    fields.append("auth_version")
                    sessions = list(AuthSession.objects.filter(user=user, revoked_at__isnull=True).values_list("jti", flat=True))
                    AuthSession.objects.filter(user=user, revoked_at__isnull=True).update(revoked_at=timezone.now())
                    after_commit(f"user.{user.pk}", "FORCE_DISCONNECT", {"reason": "account_suspended"})
                    for jti in sessions:
                        after_commit(f"session.{jti}", "session:revoked", {})
                else:
                    user.failed_attempts = 0
                    user.locked_until = None
                    fields += ["failed_attempts", "locked_until"]
                user.save(update_fields=fields)
            sanction = SanctionLog.objects.create(admin=request.user, user=user, reason=data.validated_data["reason"],
                action=SanctionLog.Action.ACTIVATE if active else SanctionLog.Action.SUSPEND)
        return Response({**AdminUserSerializer(User.objects.select_related("wallet").get(pk=user.pk)).data,
                         "sanction": {"id": sanction.pk, "action": sanction.action, "reason": sanction.reason,
                                      "created_at": sanction.created_at.isoformat()}})


class AdminUserSanctionsView(AdminView):
    def get(self, request, user_id):
        get_object_or_404(User, pk=user_id)
        return Response([{"id": s.pk, "action": s.action, "reason": s.reason, "admin": s.admin.email,
                          "created_at": s.created_at.isoformat()}
                         for s in SanctionLog.objects.filter(user_id=user_id).select_related("admin")[:50]])


class AdminGameListView(AdminView):
    """HU43: catálogo completo para el administrador y creación de juegos."""

    def get(self, request):
        games = Game.objects.prefetch_related("rooms").order_by("id")
        return Response(AdminGameSerializer(games, many=True, context={"request": request}).data)

    def post(self, request):
        serializer = AdminGameSerializer(data=request.data, context={"request": request})
        serializer.is_valid(raise_exception=True)
        with transaction.atomic():
            game = serializer.save()
            # Cada juego nuevo abre una mesa principal con sus límites para aparecer en el catálogo.
            Room.objects.create(game=game, name=f"Mesa principal · {game.title}",
                                min_bet=game.min_bet, max_bet=game.max_bet)
        return Response(AdminGameSerializer(game, context={"request": request}).data, status=201)


class AdminGameDetailView(AdminView):
    """HU44: edita parámetros; las salas activas reciben los nuevos límites al instante."""

    def get(self, request, game_id):
        game = get_object_or_404(Game, pk=game_id)
        return Response(AdminGameSerializer(game, context={"request": request}).data)

    def put(self, request, game_id):
        with transaction.atomic():
            game = get_object_or_404(Game.objects.select_for_update(), pk=game_id)
            serializer = AdminGameSerializer(game, data=request.data, partial=True, context={"request": request})
            serializer.is_valid(raise_exception=True)
            game = serializer.save()
            rooms = list(Room.objects.select_for_update().filter(game=game).order_by("id"))
            for room in rooms:
                if (room.min_bet, room.max_bet) != (game.min_bet, game.max_bet):
                    room.min_bet, room.max_bet = game.min_bet, game.max_bet
                    room.save(update_fields=["min_bet", "max_bet"])
                    after_commit(f"room.{room.pk}", "room:limits", {
                        "room_id": room.pk, "min_bet": format(room.min_bet, ".2f"), "max_bet": format(room.max_bet, ".2f")})
        return Response(AdminGameSerializer(game, context={"request": request}).data)
