from django.conf import settings
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken
from .models import User


class HU38PasswordTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email='password@example.com', dni='12345678', birth_date='2000-01-01', password='OldPalace123!')
        self.token = str(AccessToken.for_user(self.user))
        self.unused_token = str(AccessToken.for_user(self.user))
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = self.token

    def test_change_revokes_all_old_tokens_even_unused(self):
        response = self.client.put('/api/v1/users/change-password', {'current_password': 'OldPalace123!', 'new_password': 'NewPalace456!'}, format='json')
        self.assertEqual(response.status_code, 200)
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password('NewPalace456!'))
        for token in [self.token, self.unused_token]:
            self.client.cookies[settings.JWT_ACCESS_COOKIE] = token
            self.assertEqual(self.client.get('/api/v1/auth/me').status_code, 401)

    def test_wrong_current_same_and_weak_password_rejected(self):
        for old, new in [('Wrong!', 'NewPalace456!'), ('OldPalace123!', 'OldPalace123!'), ('OldPalace123!', 'weak')]:
            self.assertEqual(self.client.put('/api/v1/users/change-password', {'current_password': old, 'new_password': new}, format='json').status_code, 400)
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password('OldPalace123!'))
