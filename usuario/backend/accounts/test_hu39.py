from rest_framework.test import APITestCase
from .models import User


class HU39RolesTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="role@example.com", dni="12345678", birth_date="2000-01-01")

    def test_common_user_forbidden_and_admin_allowed(self):
        self.client.force_authenticate(self.user)
        self.assertEqual(self.client.get('/api/v1/auth/me').data['role'], 'USER')
        self.assertEqual(self.client.get('/api/v1/admin/status').status_code, 403)
        self.user.is_staff = True
        self.user.save()
        self.assertEqual(self.client.get('/api/v1/admin/status').status_code, 200)
        self.assertEqual(self.client.get('/api/v1/auth/me').data['role'], 'ADMIN')

    def test_anonymous_unauthorized(self):
        self.assertEqual(self.client.get('/api/v1/admin/status').status_code, 401)

    def test_public_registration_cannot_grant_admin(self):
        response = self.client.post('/api/v1/auth/register', {'email': 'new@example.com',
            'dni': '87654321', 'birth_date': '2000-01-01', 'password': 'Palace123!',
            'role': 'ADMIN', 'is_staff': True, 'is_superuser': True}, format='json')
        self.assertEqual(response.status_code, 201)
        user = User.objects.get(email='new@example.com')
        self.assertFalse(user.is_staff or user.is_superuser)
