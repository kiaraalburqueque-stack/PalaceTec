from decimal import Decimal
from rest_framework import serializers
from rest_framework.response import Response
from django.utils import timezone
from roulette.views import AuthenticatedView
from wallet.services import InsufficientBalance, ReferenceConflict
from .models import BlackjackHand, SportEvent, SportTicket
from . import services


class StakeInput(serializers.Serializer):
    amount = serializers.DecimalField(max_digits=12, decimal_places=2, min_value=Decimal('10'), max_value=Decimal('500'))
    reference = serializers.UUIDField()


class ActionInput(serializers.Serializer):
    action = serializers.ChoiceField(choices=['hit', 'stand'])
    revision = serializers.IntegerField(min_value=0)


class SportsInput(StakeInput):
    event_id = serializers.IntegerField(min_value=1)
    selection = serializers.ChoiceField(choices=['HOME', 'DRAW', 'AWAY'])
    quoted_odds = serializers.DecimalField(max_digits=5, decimal_places=2, min_value=Decimal('1.01'))


def hand_data(hand):
    visible = hand.dealer if not hand.active else hand.dealer[:1]
    return {'id': hand.pk, 'amount': format(hand.play.amount, '.2f'), 'player': hand.player,
        'dealer': visible, 'dealer_hidden': hand.active, 'player_score': services.score(hand.player),
        'dealer_score': services.score(visible), 'active': hand.active, 'revision': hand.revision,
        'outcome': hand.outcome, 'payout': format(hand.play.payout, '.2f') if hand.play.payout is not None else None}


def ticket_data(ticket):
    return {'id': ticket.pk, 'event_id': ticket.event_id, 'description': ticket.play.description,
        'selection': ticket.selection, 'odds': format(ticket.odds, '.2f'), 'amount': format(ticket.play.amount, '.2f'),
        'payout': format(ticket.play.payout, '.2f') if ticket.play.payout is not None else None,
        'created_at': ticket.play.created_at.isoformat(), 'settled_at': ticket.play.settled_at.isoformat() if ticket.play.settled_at else None}


class CasinoView(AuthenticatedView):
    def handle_exception(self, exc):
        if isinstance(exc, InsufficientBalance):
            return Response({'code': 'insufficient_balance', 'detail': str(exc)}, status=400)
        if isinstance(exc, ReferenceConflict):
            return Response({'code': 'reference_conflict', 'detail': str(exc)}, status=409)
        return super().handle_exception(exc)


class BlackjackView(CasinoView):
    def get(self, request):
        hand = BlackjackHand.objects.filter(user=request.user).select_related('play').order_by('-id').first()
        return Response({'hand': hand_data(hand) if hand else None})

    def post(self, request):
        serializer = StakeInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        hand, created = services.start_blackjack(request.user, token=request.auth, **serializer.validated_data)
        return Response({'hand': hand_data(hand)}, status=201 if created else 200)


class BlackjackActionView(CasinoView):
    def post(self, request, hand_id):
        serializer = ActionInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        hand = services.act_blackjack(request.user, hand_id, token=request.auth, **serializer.validated_data)
        return Response({'hand': hand_data(hand)})


class SportEventsView(CasinoView):
    def get(self, request):
        now = timezone.now()
        events = SportEvent.objects.order_by('-starts_at')[:12]
        return Response({'simulation': True, 'server_time': now.isoformat(), 'events': [{
            'id': event.pk, 'home': event.home, 'away': event.away,
            'starts_at': event.starts_at.isoformat(), 'ends_at': event.ends_at.isoformat(),
            'phase': 'SETTLED' if event.settled_at else 'OPEN' if now < event.starts_at else 'LIVE',
            'odds': {'HOME': format(event.home_odds, '.2f'), 'DRAW': format(event.draw_odds, '.2f'), 'AWAY': format(event.away_odds, '.2f')},
            'score': [event.home_score, event.away_score] if event.settled_at else None,
        } for event in events]})


class SportTicketsView(CasinoView):
    def get(self, request):
        return Response([ticket_data(ticket) for ticket in SportTicket.objects.filter(play__user=request.user).select_related('play').order_by('-id')[:50]])

    def post(self, request):
        serializer = SportsInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        ticket, created = services.place_sports(request.user, token=request.auth, **serializer.validated_data)
        return Response(ticket_data(ticket), status=201 if created else 200)


def history_data(play):
    return {'id': play.pk, 'source': 'casino', 'round_id': None, 'room_id': None,
        'game': 'Blackjack' if play.kind == 'BLACKJACK' else 'Deportes simulados',
        'kind': play.kind, 'selection': play.description, 'chips': [], 'reference': str(play.reference),
        'created_at': play.created_at, 'amount': format(play.amount, '.2f'),
        'payout': format(play.payout, '.2f') if play.payout is not None else None,
        'net': format(play.payout-play.amount, '.2f') if play.payout is not None else None,
        'status': 'PENDING' if play.payout is None else 'PUSH' if play.payout == play.amount else 'WON' if play.payout > play.amount else 'LOST'}
