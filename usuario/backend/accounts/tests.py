from datetime import date
from unittest.mock import patch

from django.db import IntegrityError
from rest_framework.test import APITestCase

from .models import User


class HU01RegisterTests(APITestCase):
    url = '/api/v1/auth/register'

    def setUp(self):
        self.today_patch = patch('accounts.serializers.timezone.localdate', return_value=date(2026, 10, 9))
        self.today_patch.start()
        self.addCleanup(self.today_patch.stop)
        self.data = {
            'email': 'kiara@example.com', 'password': 'Palace123!',
            'dni': '01234567', 'birth_date': '2000-01-01',
        }

    def register(self, **changes):
        return self.client.post(self.url, {**self.data, **changes}, format='json')

    def test_success(self):
        response = self.register()
        self.assertEqual(response.status_code, 201)
        user = User.objects.get(email=self.data['email'])
        self.assertEqual(response.data, {'id': user.pk, 'email': user.email})
        self.assertEqual(user.dni, '01234567')

    def test_password_uses_bcrypt(self):
        self.assertEqual(self.register().status_code, 201)
        user = User.objects.get()
        self.assertTrue(user.password.startswith('bcrypt_sha256$'))
        self.assertTrue(user.check_password(self.data['password']))

    def test_response_excludes_password(self):
        response = self.register()
        self.assertEqual(response.status_code, 201)
        self.assertNotIn('password', response.data)

    def test_duplicate_email(self):
        self.assertEqual(self.register().status_code, 201)
        response = self.register(dni='87654321')
        self.assertEqual(response.status_code, 409)
        self.assertIn('email', response.data)
        self.assertEqual(User.objects.count(), 1)

    def test_duplicate_dni(self):
        self.assertEqual(self.register().status_code, 201)
        response = self.register(email='other@example.com')
        self.assertEqual(response.status_code, 409)
        self.assertIn('dni', response.data)

    def test_underage(self):
        self.assertEqual(self.register(birth_date='2008-10-10').status_code, 400)
        self.assertFalse(User.objects.exists())

    def test_exactly_eighteen_today(self):
        self.assertEqual(self.register(birth_date='2008-10-09').status_code, 201)

    def test_invalid_passwords(self):
        for password in ['Abc!123', 'palace123!', 'Palace123', 'Palace123 ']:
            with self.subTest(password=password):
                response = self.register(password=password)
                self.assertEqual(response.status_code, 400)
                self.assertIn('password', response.data)
        self.assertFalse(User.objects.exists())

    def test_invalid_dni(self):
        for dni in ['1234567', '123456789', '1234567a', ' 12345678 ', '１２３４５６７８']:
            with self.subTest(dni=dni):
                response = self.register(dni=dni)
                self.assertEqual(response.status_code, 400)
                self.assertIn('dni', response.data)

    def test_invalid_email(self):
        response = self.register(email='not-an-email')
        self.assertEqual(response.status_code, 400)
        self.assertIn('email', response.data)

    def test_integrity_error_returns_conflict(self):
        with patch('accounts.serializers.User.objects.create_user', side_effect=IntegrityError):
            response = self.register()
        self.assertEqual(response.status_code, 409)
        self.assertFalse(User.objects.exists())
