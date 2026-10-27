from datetime import timedelta
from decimal import Decimal
from unittest.mock import patch
from uuid import uuid4
from django.utils import timezone
from rest_framework.test import APITestCase
from accounts.models import User
from wallet.models import Wallet, WalletTransaction
from responsible.models import DailyLimit, DailySpending, RiskIncident
from .models import Play, BlackjackHand, SportEvent
from . import services


def deck_for(player, dealer, next_card='K_C'):
    tail = [next_card, dealer[1], dealer[0], player[1], player[0]]
    return [card for card in services.shuffled_deck() if card not in tail]+tail


class NewGamesTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email='casino@example.com', dni='12345678', birth_date='2000-01-01')
        self.wallet = Wallet.objects.create(user=self.user, balance=1000, bonus_balance=1000)
        DailyLimit.objects.create(user=self.user, amount=1000)
        self.client.force_authenticate(self.user)

    def start(self, **changes):
        return self.client.post('/api/v1/casino/blackjack', {'amount': '10.00', 'reference': str(uuid4()), **changes}, format='json')

    def test_blackjack_natural_pays_three_to_two_once(self):
        cards = deck_for(['A_S', 'K_S'], ['9_H', '8_H'])
        reference = str(uuid4())
        with patch('casino.services.shuffled_deck', return_value=cards):
            response = self.start(reference=reference)
        self.assertEqual(response.status_code, 201)
        hand = response.data['hand']
        self.assertEqual((hand['outcome'], hand['payout'], hand['active']), ('BLACKJACK', '25.00', False))
        self.assertEqual(self.start(reference=reference).status_code, 200)
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 1015)
        self.assertEqual(WalletTransaction.objects.filter(type='WIN').count(), 1)

    def test_dealer_hole_card_and_deck_never_exposed_and_hit_retries_safe(self):
        cards = deck_for(['9_S', '7_S'], ['10_H', '8_H'], 'K_C')
        with patch('casino.services.shuffled_deck', return_value=cards):
            response = self.start()
        hand = response.data['hand']
        self.assertTrue(hand['dealer_hidden'])
        self.assertEqual(hand['dealer'], ['10_H'])
        self.assertNotIn('deck', hand)
        self.assertEqual(self.start().status_code, 400)
        url = f"/api/v1/casino/blackjack/{hand['id']}/action"
        response = self.client.post(url, {'action': 'hit', 'revision': 0}, format='json')
        self.assertEqual(response.data['hand']['outcome'], 'LOST')
        self.assertEqual(response.data['hand']['player_score'], 26)
        self.assertEqual(self.client.post(url, {'action': 'hit', 'revision': 0}, format='json').data, response.data)
        self.assertEqual(DailySpending.objects.get(user=self.user).amount, 10)

    def test_aces_soft_seventeen_push_and_dealer_blackjack(self):
        self.assertEqual(services.score(['A_S', 'A_H', '9_D']), 21)
        for player, dealer, outcome in [(['10_S', '8_S'], ['10_H', '8_H'], 'PUSH'),
                                       (['9_S', '8_S'], ['A_H', '6_H'], 'PUSH'),
                                       (['9_S', '8_S'], ['A_H', 'K_H'], 'LOST')]:
            with patch('casino.services.shuffled_deck', return_value=deck_for(player, dealer)):
                hand = self.start().data['hand']
            if hand['active']:
                hand = self.client.post(f"/api/v1/casino/blackjack/{hand['id']}/action", {'action': 'stand', 'revision': 0}, format='json').data['hand']
            self.assertEqual(hand['outcome'], outcome)

    def event(self):
        now = timezone.now()
        return SportEvent.objects.create(home='Cóndores', away='Halcones', starts_at=now+timedelta(minutes=2), ends_at=now+timedelta(minutes=3))

    def sports(self, event, **changes):
        return self.client.post('/api/v1/casino/sports/tickets', {'event_id': event.pk, 'selection': 'HOME',
            'amount': '10.00', 'quoted_odds': '1.90', 'reference': str(uuid4()), **changes}, format='json')

    def test_sports_quote_snapshot_deadline_and_idempotent_payment(self):
        event = self.event()
        ref = str(uuid4())
        self.assertEqual(self.sports(event, quoted_odds='9.99').status_code, 400)
        self.assertEqual(self.sports(event, reference=ref).status_code, 201)
        self.assertEqual(self.sports(event, reference=ref).status_code, 200)
        self.assertEqual(self.sports(event, reference=ref, selection='AWAY', quoted_odds='2.40').status_code, 409)
        with patch('casino.services.timezone.now', return_value=event.starts_at):
            self.assertEqual(self.sports(event).status_code, 400)
        with patch('casino.services.timezone.now', return_value=event.ends_at), patch('casino.services.secrets.randbelow', side_effect=[2, 1]):
            services.settle_event(event.pk)
            services.settle_event(event.pk)
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 1009)
        self.assertEqual(Play.objects.get().payout, Decimal('19.00'))
        self.assertEqual(WalletTransaction.objects.filter(type='WIN').count(), 1)

    def test_controls_apply_to_both_games_and_roll_back_spending(self):
        event = self.event()
        DailyLimit.objects.filter(user=self.user).update(amount=0)
        self.assertEqual(self.start().data['code'], 'daily_limit_exceeded')
        self.assertEqual(self.sports(event).data['code'], 'daily_limit_exceeded')
        DailyLimit.objects.filter(user=self.user).update(amount=1000)
        self.wallet.balance = self.wallet.bonus_balance = 0
        self.wallet.save()
        self.assertEqual(self.start().data['code'], 'insufficient_balance')
        self.assertFalse(Play.objects.exists())
        self.assertFalse(DailySpending.objects.exists())
        self.user.self_excluded_until = timezone.now()+timedelta(days=7)
        self.user.save()
        self.assertEqual(self.sports(event).status_code, 403)

    def test_history_statistics_and_private_hands(self):
        with patch('casino.services.shuffled_deck', return_value=deck_for(['A_S', 'K_S'], ['9_H', '8_H'])):
            hand = self.start().data['hand']
        history = self.client.get('/api/v1/users/bets-history').data
        self.assertEqual(history['count'], 1)
        self.assertEqual(history['results'][0]['game'], 'Blackjack')
        self.assertEqual(history['results'][0]['net'], '15.00')
        self.assertEqual(self.client.get('/api/v1/stats/summary').data['net'], '15.00')
        other = User.objects.create_user(email='stranger@example.com', dni='87654321', birth_date='2000-01-01')
        self.client.force_authenticate(other)
        self.assertIsNone(self.client.get('/api/v1/casino/blackjack').data['hand'])
        self.assertEqual(self.client.post(f"/api/v1/casino/blackjack/{hand['id']}/action", {'action': 'stand', 'revision': 0}, format='json').status_code, 404)
        self.assertEqual(self.client.get('/api/v1/users/bets-history').data['count'], 0)

    def test_idle_hand_settles_without_new_stake(self):
        with patch('casino.services.shuffled_deck', return_value=deck_for(['10_S', '8_S'], ['10_H', '8_H'])):
            hand = self.start().data['hand']
        BlackjackHand.objects.filter(pk=hand['id']).update(last_action_at=timezone.now()-timedelta(minutes=11))
        services.tick_casino()
        self.assertFalse(BlackjackHand.objects.get(pk=hand['id']).active)
        self.wallet.refresh_from_db()
        self.assertEqual(self.wallet.balance, 1000)

    def test_natural_and_pending_sports_are_in_authenticated_session_summary(self):
        from django.conf import settings
        from rest_framework_simplejwt.tokens import AccessToken
        self.client.force_authenticate(None)
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = str(AccessToken.for_user(self.user))
        with patch('casino.services.shuffled_deck', return_value=deck_for(['A_S', 'K_S'], ['9_H', '8_H'])):
            self.assertEqual(self.start().status_code, 201)
        self.assertEqual(self.sports(self.event()).status_code, 201)
        summary = self.client.get('/api/v1/responsible-gaming/session-summary').data
        self.assertEqual((summary['total_wagered'], summary['total_paid'], summary['net'], summary['pending_wagered']), ('10.00', '25.00', '15.00', '10.00'))

    def test_sports_draw_pays_only_draw_tickets(self):
        event = self.event()
        for selection, odds in [('HOME', '1.90'), ('DRAW', '3.20'), ('AWAY', '2.40')]:
            self.assertEqual(self.sports(event, selection=selection, quoted_odds=odds).status_code, 201)
        with patch('casino.services.timezone.now', return_value=event.ends_at), patch('casino.services.secrets.randbelow', side_effect=[1, 1]):
            services.settle_event(event.pk)
        self.assertEqual(list(Play.objects.order_by('id').values_list('payout', flat=True)), [Decimal(0), Decimal(32), Decimal(0)])

    def test_new_games_keep_risk_alert_and_private_recovery(self):
        for amount in [10, 30, 90, 270]:
            play = Play.objects.create(user=self.user, kind='BLACKJACK', amount=amount, reference=uuid4())
            services.settle_play(play, Decimal('0'))
        incident = RiskIncident.objects.get(user=self.user)
        self.assertEqual(incident.play_id, play.pk)
        self.assertIsNone(incident.round_id)
        alerts = self.client.get('/api/v1/responsible-gaming/risk-alerts').data['results']
        self.assertEqual(alerts[0]['incident_id'], incident.pk)
