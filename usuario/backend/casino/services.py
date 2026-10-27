import secrets
from datetime import timedelta
from decimal import Decimal, ROUND_HALF_UP
from django.db import transaction
from django.db.models import Sum
from django.utils import timezone
from rest_framework.exceptions import ValidationError
from accounts.models import User, AuthSession
from accounts.authentication import CookieJWTAuthentication
from accounts.sessions import session_key
from responsible.services import ExclusionService, LimitService, DailyLimitError
from wallet.services import WalletService, ReferenceConflict
from .models import Play, BlackjackHand, SportEvent, SportTicket, DemoSchedule


def score(cards):
    ranks = [card.split('_')[0] for card in cards]
    total = sum(11 if rank == 'A' else 10 if rank in ('J', 'Q', 'K') else int(rank) for rank in ranks)
    aces = ranks.count('A')
    while total > 21 and aces:
        total -= 10
        aces -= 1
    return total


def shuffled_deck():
    cards = [f'{rank}_{suit}' for suit in ('S', 'H', 'D', 'C') for rank in ('A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K')]
    secrets.SystemRandom().shuffle(cards)
    return cards


def locked_player(user, token=None, new_bet=True):
    user = User.objects.select_for_update().get(pk=user.pk)
    ExclusionService.ensure_user_allowed(user)
    if token is not None:
        CookieJWTAuthentication().get_user(token)
    if new_bet and user.pause_until and user.pause_until > timezone.now():
        raise DailyLimitError('pause_active', 'Tu pausa de juego sigue activa.')
    return user


def reserve_play(user, kind, amount, reference, token=None, description=''):
    LimitService.check_and_reserve(user, amount)
    session = AuthSession.objects.get(jti=session_key(token), user=user) if token is not None else None
    play = Play.objects.create(user=user, kind=kind, amount=amount, reference=reference, session=session, description=description)
    WalletService.debit(user, amount, f'casino:bet:{play.pk}')
    return play


@transaction.atomic
def settle_play(play, payout):
    if play.settled_at:
        return
    payout = Decimal(payout).quantize(Decimal('.01'), rounding=ROUND_HALF_UP)
    if payout:
        WalletService.credit(play.user, payout, f'casino:win:{play.pk}')
    play.payout, play.settled_at = payout, timezone.now()
    play.save(update_fields=['payout', 'settled_at'])
    from responsible.pattern_detector import detect_play_escalation
    detect_play_escalation(play)


def finish_hand(hand, natural=False):
    player, dealer = score(hand.player), score(hand.dealer)
    if natural:
        hand.outcome = 'PUSH' if player == dealer else 'BLACKJACK' if player == 21 else 'LOST'
    else:
        while player <= 21 and score(hand.dealer) < 17:
            hand.dealer.append(hand.deck.pop())
        dealer = score(hand.dealer)
        hand.outcome = 'LOST' if player > 21 else 'WON' if dealer > 21 or player > dealer else 'PUSH' if player == dealer else 'LOST'
    multiplier = {'BLACKJACK': Decimal('2.5'), 'WON': Decimal('2'), 'PUSH': Decimal('1'), 'LOST': Decimal('0')}[hand.outcome]
    settle_play(hand.play, hand.play.amount*multiplier)
    hand.active = False


@transaction.atomic
def start_blackjack(user, amount, reference, token=None):
    user = locked_player(user, token)
    existing = Play.objects.filter(user=user, reference=reference).first()
    if existing:
        if existing.kind != 'BLACKJACK' or existing.amount != amount:
            raise ReferenceConflict('La referencia ya corresponde a otra jugada.')
        return existing.blackjack, False
    if BlackjackHand.objects.filter(user=user, active=True).exists():
        raise ValidationError({'detail': 'Termina tu mano actual antes de comenzar otra.'})
    play = reserve_play(user, 'BLACKJACK', amount, reference, token, 'Blackjack clásico')
    deck = shuffled_deck()
    player, dealer = [deck.pop(), deck.pop()], [deck.pop(), deck.pop()]
    hand = BlackjackHand.objects.create(play=play, user=user, deck=deck, player=player, dealer=dealer, last_action_at=timezone.now())
    if score(player) == 21 or score(dealer) == 21:
        finish_hand(hand, natural=True)
        hand.save()
    return hand, True


@transaction.atomic
def act_blackjack(user, hand_id, action, revision, token=None):
    user = locked_player(user, token, new_bet=False)
    from django.shortcuts import get_object_or_404
    hand = get_object_or_404(BlackjackHand.objects.select_for_update().select_related('play'), pk=hand_id, user=user)
    if hand.revision != revision or not hand.active:
        return hand  # Reintento de una accion: no vuelve a repartir ni pagar.
    if action == 'hit':
        hand.player.append(hand.deck.pop())
        if score(hand.player) >= 21:
            finish_hand(hand)
    elif action == 'stand':
        finish_hand(hand)
    else:
        raise ValidationError({'action': 'Accion no permitida.'})
    hand.revision += 1
    hand.last_action_at = timezone.now()
    hand.save()
    return hand


@transaction.atomic
def place_sports(user, event_id, selection, amount, reference, quoted_odds, token=None):
    from django.shortcuts import get_object_or_404
    event = get_object_or_404(SportEvent.objects.select_for_update(), pk=event_id)
    user = locked_player(user, token)
    existing = Play.objects.filter(user=user, reference=reference).first()
    if existing:
        if existing.kind != 'SPORTS' or existing.amount != amount or existing.ticket.event_id != event_id or existing.ticket.selection != selection or existing.ticket.odds != quoted_odds:
            raise ReferenceConflict('La referencia ya corresponde a otro boleto.')
        return existing.ticket, False
    if timezone.now() >= event.starts_at or event.settled_at:
        raise ValidationError({'detail': 'El mercado esta cerrado. No se aceptan apuestas en vivo.'})
    odds = {'HOME': event.home_odds, 'DRAW': event.draw_odds, 'AWAY': event.away_odds}[selection]
    if odds != quoted_odds:
        raise ValidationError({'detail': 'La cuota cambio. Consulta y confirma nuevamente.'})
    play = reserve_play(user, 'SPORTS', amount, reference, token, f'{event.home} vs {event.away} · {selection}')
    return SportTicket.objects.create(play=play, event=event, selection=selection, odds=odds), True


@transaction.atomic
def settle_event(event_id):
    event = SportEvent.objects.select_for_update().get(pk=event_id)
    if event.settled_at or timezone.now() < event.ends_at:
        return
    # Simulador educativo; no consume datos ni resultados de encuentros reales.
    event.home_score, event.away_score = secrets.randbelow(5), secrets.randbelow(5)
    winner = 'HOME' if event.home_score > event.away_score else 'AWAY' if event.away_score > event.home_score else 'DRAW'
    users = User.objects.select_for_update().filter(pk__in=event.tickets.values('play__user_id')).order_by('pk')
    list(users)
    for ticket in event.tickets.select_related('play__user').order_by('play__user_id', 'pk'):
        settle_play(ticket.play, ticket.play.amount*ticket.odds if ticket.selection == winner else Decimal('0'))
    event.settled_at = timezone.now()
    event.save()


@transaction.atomic
def ensure_demo_events():
    DemoSchedule.objects.get_or_create(pk=1)
    DemoSchedule.objects.select_for_update().get(pk=1)
    now = timezone.now()
    if SportEvent.objects.filter(starts_at__gt=now, settled_at__isnull=True).exists():
        return
    for i, (home, away) in enumerate([('Cóndores FC', 'Halcones FC'), ('Titanes del Sur', 'Lobos del Norte'), ('Atlético Aurora', 'Deportivo Horizonte')]):
        start = now+timedelta(seconds=90+i*60)
        SportEvent.objects.create(home=home, away=away, starts_at=start, ends_at=start+timedelta(seconds=60))


def tick_casino():
    ensure_demo_events()
    for event_id in SportEvent.objects.filter(settled_at__isnull=True, ends_at__lte=timezone.now()).values_list('id', flat=True):
        settle_event(event_id)
    for user_id, hand_id in BlackjackHand.objects.filter(active=True, last_action_at__lte=timezone.now()-timedelta(minutes=10)).values_list('user_id', 'id'):
        with transaction.atomic():
            User.objects.select_for_update().get(pk=user_id)
            hand = BlackjackHand.objects.select_for_update().select_related('play').get(pk=hand_id)
            if hand.active and hand.last_action_at <= timezone.now()-timedelta(minutes=10):
                finish_hand(hand)
                hand.revision += 1
                hand.save()


def play_summary(plays):
    values = plays.filter(settled_at__isnull=False).aggregate(wagered=Sum('amount'), paid=Sum('payout'))
    wagered, paid = values['wagered'] or Decimal('0'), values['paid'] or Decimal('0')
    return {'total_wagered': format(wagered, '.2f'), 'total_paid': format(paid, '.2f'), 'net': format(paid-wagered, '.2f')}


def merge_summary(first, second):
    return {key: format(Decimal(first[key])+Decimal(second[key]), '.2f') for key in ('total_wagered', 'total_paid', 'net')}
