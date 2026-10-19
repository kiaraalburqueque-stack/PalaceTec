from django.db.models import Sum
from realtime.events import after_commit
from roulette.models import Bet
from .models import RiskIncident


def detect_loss_escalation(user, round):
    """El liquidador ya tiene el candado del usuario; agrupar fichas por ronda."""
    rows = list(Bet.objects.filter(user=user, round__status="SETTLED").values(
        "round_id", "round__settled_at").annotate(amount=Sum("amount"), paid=Sum("payout"))
        .order_by("-round__settled_at", "-round_id")[:4])
    rows.reverse()
    if len(rows) != 4 or rows[-1]["round_id"] != round.pk:
        return None
    if any(row["paid"] >= row["amount"] for row in rows):
        return None
    if not all(rows[i]["amount"] >= rows[i-1]["amount"]*3 for i in range(1, 4)):
        return None
    incident, created = RiskIncident.objects.get_or_create(user=user, round=round, defaults={
        "evidence": [{"round_id": row["round_id"], "amount": format(row["amount"], ".2f"),
                      "paid": format(row["paid"], ".2f")} for row in rows]})
    if created:
        after_commit(f"user.{user.pk}", "responsible:risk-alert", {"incident_id": incident.pk,
            "suggested_pause_seconds": 600, "detail": "Tus apuestas aumentaron tras varias perdidas. Te sugerimos una pausa de 10 minutos."})
    return incident
