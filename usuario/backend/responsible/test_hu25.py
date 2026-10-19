from django.conf import settings
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken
from accounts.models import User
from .models import ExcludedPerson
from .services import ExclusionService


class HU25ExclusionTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email='excluded@example.com', dni='12345678', birth_date='2000-01-01', password='Palace123!')
        ExcludedPerson.objects.create(dni_hash=ExclusionService.digest(self.user.dni), source='fixture', reason='private')

    def test_excluded_registration_login_and_existing_session(self):
        register = self.client.post('/api/v1/auth/register', {'email': 'new@example.com', 'dni': self.user.dni, 'birth_date': '2000-01-01', 'password': 'Palace123!'}, format='json')
        login = self.client.post('/api/v1/auth/login', {'email': self.user.email, 'password': 'Palace123!'}, format='json')
        self.client.cookies[settings.JWT_ACCESS_COOKIE] = str(AccessToken.for_user(self.user))
        me = self.client.get('/api/v1/auth/me')
        for response in [register, login, me]:
            self.assertEqual(response.status_code, 403)
            self.assertNotIn('private', str(response.data))
        self.assertEqual(User.objects.count(), 1)

    def test_hashed_storage_and_invalid_dni(self):
        record = ExcludedPerson.objects.get()
        self.assertEqual(len(record.dni_hash), 64)
        self.assertNotIn(self.user.dni, record.dni_hash)
        self.assertFalse(ExclusionService.is_excluded('87654321'))
        for invalid in ['123', ' 12345678', '１２３４５６７８']:
            with self.assertRaises(ValueError):
                ExclusionService.digest(invalid)

    def test_only_admin_can_add_exclusions(self):
        self.client.force_authenticate(self.user)
        self.assertEqual(self.client.post('/api/v1/responsible-gaming/excluded-people', {}).status_code, 403)
        self.user.is_staff = True
        self.user.save()
        response = self.client.post('/api/v1/responsible-gaming/excluded-people', {'dni': '87654321', 'source': 'test'}, format='json')
        self.assertEqual(response.status_code, 201)
        self.assertNotIn('dni', response.data)
