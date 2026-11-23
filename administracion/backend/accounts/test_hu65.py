"""HU65: subida de foto de avatar (JPG, PNG o WEBP de hasta 2 MB, recortada a 300 x 300 en WEBP)."""
import io
import shutil
import tempfile
from pathlib import Path

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings
from PIL import Image
from rest_framework.test import APITestCase

from .models import User

URL = "/api/v1/users/avatar"


def image_file(name="foto.png", fmt="PNG", size=(600, 400), content_type="image/png", color=(200, 30, 30)):
    buffer = io.BytesIO()
    Image.new("RGB", size, color).save(buffer, format=fmt)
    return SimpleUploadedFile(name, buffer.getvalue(), content_type=content_type)


class HU65AvatarTests(APITestCase):
    def setUp(self):
        self.media = tempfile.mkdtemp()
        self.settings_override = override_settings(MEDIA_ROOT=self.media)
        self.settings_override.enable()
        self.user = User.objects.create_user(email="hu65@palacetec.pe", dni="65656565", birth_date="2000-01-01")
        self.client.force_authenticate(self.user)

    def tearDown(self):
        self.settings_override.disable()
        shutil.rmtree(self.media, ignore_errors=True)

    def upload(self, upload):
        return self.client.post(URL, {"avatar": upload}, format="multipart")

    def stored(self, avatar_url):
        return Path(self.media) / avatar_url.removeprefix("/media/")

    def test_hu65_valid_png_is_cropped_to_300_webp(self):
        with self.captureOnCommitCallbacks(execute=True):
            response = self.upload(image_file())
        self.assertEqual(response.status_code, 200)
        url = response.data["avatar_url"]
        self.assertTrue(url.startswith("/media/avatars/user_") and url.endswith(".webp"))
        self.user.refresh_from_db()
        self.assertEqual(self.user.avatar_url, url)
        with Image.open(self.stored(url)) as saved:
            self.assertEqual((saved.format, saved.size), ("WEBP", (300, 300)))
        self.assertEqual(self.client.get("/api/v1/users/me").data["avatar_url"], url)

    def test_hu65_accepts_jpg_and_webp(self):
        self.assertEqual(self.upload(image_file("foto.jpg", "JPEG", content_type="image/jpeg")).status_code, 200)
        self.assertEqual(self.upload(image_file("foto.webp", "WEBP", (200, 500), "image/webp")).status_code, 200)

    def test_hu65_other_format_is_rejected_with_clear_message(self):
        response = self.upload(image_file("foto.gif", "GIF", content_type="image/gif"))
        self.assertEqual(response.status_code, 400)
        self.assertIn("JPG, PNG o WEBP", str(response.data["avatar"]))
        self.assertEqual(User.objects.get(pk=self.user.pk).avatar_url, "/avatars/avatar-1.svg")

    def test_hu65_fake_extension_is_rejected(self):
        fake = SimpleUploadedFile("foto.png", b"<script>alert(1)</script>" * 10, content_type="image/png")
        self.assertIn("imagen válida", str(self.upload(fake).data["avatar"]))
        jpg_as_png = image_file("foto.png", "JPEG", content_type="image/png")
        self.assertEqual(self.upload(jpg_as_png).status_code, 400)
        truncated = SimpleUploadedFile("foto.png", b"\x89PNG\r\n\x1a\n" + b"0" * 100, content_type="image/png")
        self.assertEqual(self.upload(truncated).status_code, 400)

    def test_hu65_file_over_2_mb_is_rejected(self):
        heavy = SimpleUploadedFile("foto.png", b"\x89PNG\r\n\x1a\n" + b"0" * (2 * 1024 * 1024), content_type="image/png")
        response = self.upload(heavy)
        self.assertEqual(response.status_code, 400)
        self.assertIn("2 MB", str(response.data["avatar"]))

    def test_hu65_new_photo_or_default_avatar_deletes_previous_file(self):
        with self.captureOnCommitCallbacks(execute=True):
            first = self.upload(image_file()).data["avatar_url"]
        with self.captureOnCommitCallbacks(execute=True):
            second = self.upload(image_file(color=(0, 0, 200))).data["avatar_url"]
        self.assertNotEqual(first, second)
        self.assertFalse(self.stored(first).exists())
        self.assertTrue(self.stored(second).exists())
        with self.captureOnCommitCallbacks(execute=True):
            response = self.client.put("/api/v1/users/profile", {"avatar_url": "/avatars/avatar-3.svg"}, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertFalse(self.stored(second).exists())

    def test_hu65_profile_only_accepts_default_avatars_or_own_photo(self):
        response = self.client.put("/api/v1/users/profile", {"avatar_url": "/media/avatars/otro.webp"}, format="json")
        self.assertEqual(response.status_code, 400)
        url = self.upload(image_file()).data["avatar_url"]
        self.assertEqual(self.client.put("/api/v1/users/profile", {"avatar_url": url}, format="json").status_code, 200)

    def test_hu65_requires_file_and_authentication(self):
        self.assertEqual(self.client.post(URL, {}, format="multipart").status_code, 400)
        self.client.force_authenticate(None)
        self.assertEqual(self.upload(image_file()).status_code, 401)
