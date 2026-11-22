from rest_framework.test import APITestCase

from accounts.models import User
from wallet.models import Wallet


def make_user(email, dni, **extra):
    user = User.objects.create_user(email=email, dni=dni, birth_date="2000-01-01", password="Palace123!", **extra)
    Wallet.objects.create(user=user, balance=1000, bonus_balance=1000)
    return user


class AdminTestBase(APITestCase):
    def setUp(self):
        self.admin = make_user("admin@palacetec.pe", "10000000", is_staff=True)
        self.player = make_user("jugador@palacetec.pe", "20000000")
        self.client.force_authenticate(self.admin)

    def as_player(self):
        self.client.force_authenticate(self.player)
