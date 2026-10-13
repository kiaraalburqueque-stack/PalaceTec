from rest_framework.test import APITestCase
from accounts.models import User


class HU04HU20ProfileTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(email="profile@example.com", dni="12345678", birth_date="2000-01-01")
        self.client.force_authenticate(self.user)

    def test_get_public_profile(self):
        response = self.client.get("/api/v1/users/me")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(set(response.data), {"id", "email", "nickname", "avatar_url"})

    def test_update_and_normalize_nickname(self):
        response = self.client.put("/api/v1/users/profile", {"nickname": "Kiara_1", "avatar_url": "/avatars/avatar-2.svg"})
        self.assertEqual(response.status_code, 200)
        self.user.refresh_from_db()
        self.assertEqual(self.user.nickname, "kiara_1")

    def test_duplicate_nickname(self):
        User.objects.create_user(email="other@example.com", dni="87654321", birth_date="2000-01-01", nickname="taken")
        response = self.client.put("/api/v1/users/profile", {"nickname": "TAKEN", "avatar_url": "/avatars/avatar-1.svg"})
        self.assertEqual(response.status_code, 400)

    def test_invalid_avatar_or_nickname(self):
        for nickname, avatar in (("a", "/avatars/avatar-1.svg"), ("valid", "https://untrusted.test/a.svg")):
            self.assertEqual(self.client.put("/api/v1/users/profile", {"nickname": nickname, "avatar_url": avatar}).status_code, 400)

    def test_anonymous_rejected(self):
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get("/api/v1/users/me").status_code, 401)
        self.assertEqual(self.client.put("/api/v1/users/profile", {}).status_code, 401)
