"""HU49: auditoría global de transacciones de billetera con exportación CSV."""
import csv
import re

from django.db.models import Q
from django.http import HttpResponse
from django.utils import timezone
from rest_framework import serializers
from rest_framework.permissions import BasePermission, IsAuthenticated

from roulette.models import Bet
from casino.models import Play
from roulette.queries import filter_dates
from wallet.models import WalletTransaction
from .base import AdminPagination, AdminView

AUDITOR_GROUP = "Auditor"
REFERENCE = re.compile(r"\A(roulette|casino):(bet|win):(\d+)\Z")
EXPORT_LIMIT = 10000


class IsAdminOrAuditor(BasePermission):
    message = "Solo administradores o auditores pueden revisar transacciones."

    def has_permission(self, request, view):
        user = request.user
        return bool(user and user.is_authenticated and user.is_active and (
            user.is_staff or user.is_superuser or user.groups.filter(name=AUDITOR_GROUP).exists()))


class TransactionQuery(serializers.Serializer):
    user_id = serializers.IntegerField(min_value=1, required=False)
    transaction_id = serializers.IntegerField(min_value=1, required=False)
    search = serializers.CharField(required=False, allow_blank=True, max_length=254)
    type = serializers.ChoiceField(choices=WalletTransaction.Type.choices, required=False)


def filtered_transactions(params):
    query = TransactionQuery(data=params)
    query.is_valid(raise_exception=True)
    values = query.validated_data
    rows = WalletTransaction.objects.select_related("wallet__user").order_by("-created_at", "-id")
    if "user_id" in values:
        rows = rows.filter(wallet__user_id=values["user_id"])
    if "transaction_id" in values:
        rows = rows.filter(pk=values["transaction_id"])
    if values.get("type"):
        rows = rows.filter(type=values["type"])
    search = (values.get("search") or "").strip()
    if search:
        condition = Q(wallet__user__email__icontains=search) | Q(reference__icontains=search)
        if search.isdigit():
            condition |= Q(pk=int(search)) | Q(wallet__user_id=int(search))
        rows = rows.filter(condition)
    return filter_dates(rows, {key: params[key] for key in ("startDate", "endDate") if params.get(key)})


def linked(rows):
    """Une cada movimiento con su apuesta o jugada a partir de la referencia monetaria."""
    bet_ids, play_ids = set(), set()
    for row in rows:
        match = REFERENCE.match(row.reference or "")
        if match:
            (bet_ids if match.group(1) == "roulette" else play_ids).add(int(match.group(3)))
    bets = {bet.pk: bet for bet in Bet.objects.filter(pk__in=bet_ids).select_related("round__room")}
    plays = {play.pk: play for play in Play.objects.filter(pk__in=play_ids)}
    result = []
    for row in rows:
        match = REFERENCE.match(row.reference or "")
        detail = None
        if match and match.group(1) == "roulette" and int(match.group(3)) in bets:
            bet = bets[int(match.group(3))]
            detail = {"source": "roulette", "bet_id": bet.pk, "round_id": bet.round_id, "room_id": bet.round.room_id,
                      "kind": bet.kind, "selection": bet.selection}
        elif match and match.group(1) == "casino" and int(match.group(3)) in plays:
            play = plays[int(match.group(3))]
            detail = {"source": "casino", "play_id": play.pk, "kind": play.kind, "description": play.description}
        user = row.wallet.user
        result.append({"id": row.pk, "created_at": row.created_at.isoformat(), "user_id": user.pk, "email": user.email,
                       "type": row.type, "type_label": row.get_type_display(), "amount": format(row.amount, ".2f"),
                       "balance_after": format(row.balance_after, ".2f"), "reference": row.reference, "bet": detail})
    return result


class TransactionAuditView(AdminView):
    permission_classes = [IsAuthenticated, IsAdminOrAuditor]

    def get(self, request):
        rows = filtered_transactions(request.query_params)
        pagination = AdminPagination()
        page = pagination.paginate_queryset(rows, request, view=self)
        return pagination.get_paginated_response(linked(page))


class TransactionExportView(TransactionAuditView):
    def get(self, request):
        rows = linked(list(filtered_transactions(request.query_params)[:EXPORT_LIMIT]))
        response = HttpResponse(content_type="text/csv; charset=utf-8")
        stamp = timezone.localtime().strftime("%Y%m%d-%H%M")
        response["Content-Disposition"] = f'attachment; filename="auditoria-transacciones-{stamp}.csv"'
        response.write("﻿")  # BOM: Excel abre las tildes correctamente.
        writer = csv.writer(response)
        writer.writerow(["id", "fecha", "usuario_id", "correo", "tipo", "monto", "saldo_posterior", "referencia", "apuesta_id", "ronda_id"])
        for row in rows:
            bet = row["bet"] or {}
            writer.writerow([row["id"], row["created_at"], row["user_id"], row["email"], row["type"], row["amount"],
                             row["balance_after"], row["reference"] or "", bet.get("bet_id") or bet.get("play_id") or "",
                             bet.get("round_id") or ""])
        return response
