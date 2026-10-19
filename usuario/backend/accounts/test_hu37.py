from rest_framework.test import APITestCase
from .models import User


class HU37PersonalProfileTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email='personal@example.com', dni='12345678', birth_date='2000-01-01')
        self.client.force_authenticate(self.user)

    def test_update_personal_fields_preserves_identity(self):
        response = self.client.put('/api/v1/users/profile', {'first_name': 'María', 'last_name': "O'Connor", 'phone': '+51 999 123 456'}, format='json')
        self.assertEqual(response.status_code, 200)
        self.user.refresh_from_db()
        self.assertEqual(self.user.first_name, 'María')
        self.assertEqual(self.user.email, 'personal@example.com')
        self.assertEqual(self.user.phone, '+51 999 123 456')

    def test_identity_changes_rejected_atomically(self):
        for field, value in [('email', 'other@example.com'), ('dni', '87654321')]:
            response = self.client.put('/api/v1/users/profile', {field: value, 'first_name': 'Changed'}, format='json')
            self.assertEqual(response.status_code, 400)
        self.user.refresh_from_db()
        self.assertEqual(self.user.first_name, '')

    def test_invalid_values(self):
        for data in [{'first_name': '<script>alert(1)</script>'}, {'last_name': 'x'*81}, {'phone': 'javascript:123'}]:
            self.assertEqual(self.client.put('/api/v1/users/profile', data, format='json').status_code, 400)
