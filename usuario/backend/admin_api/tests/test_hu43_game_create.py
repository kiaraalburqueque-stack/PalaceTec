import shutil
import tempfile

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings

from roulette.models import Game, Room
from .helpers import AdminTestBase

PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 64
MEDIA = tempfile.mkdtemp()


@override_settings(MEDIA_ROOT=MEDIA)
class HU43GameCreateTests(AdminTestBase):
    url = "/api/v1/admin/games"

    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(MEDIA, ignore_errors=True)

    def payload(self, **changes):
        return {"title": "Ruleta Imperial", "slug": "ruleta-imperial", "category": "RULETA",
                "min_bet": "20.00", "max_bet": "800.00", "rtp": "97.30", **changes}

    def test_hu43_created_game_appears_in_catalog(self):
        cover = SimpleUploadedFile("portada.png", PNG, content_type="image/png")
        response = self.client.post(self.url, {**self.payload(), "cover": cover}, format="multipart")
        self.assertEqual(response.status_code, 201, response.data)
        self.assertTrue(response.data["cover_url"].endswith(".png"))
        game = Game.objects.get(slug="ruleta-imperial")
        self.assertTrue(game.is_published)
        room = Room.objects.get(game=game)
        self.assertEqual((room.min_bet, room.max_bet), (20, 800))
        self.as_player()
        catalog = self.client.get("/api/v1/games").data
        created = next(item for item in catalog if item["slug"] == "ruleta-imperial")
        self.assertEqual((created["min_bet"], created["max_bet"], created["rtp"]), ("20.00", "800.00", "97.30"))

    def test_hu43_rejects_min_bet_greater_than_max_bet(self):
        response = self.client.post(self.url, self.payload(min_bet="900.00"), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertIn("min_bet", response.data)
        self.assertFalse(Game.objects.filter(slug="ruleta-imperial").exists())

    def test_hu43_rejects_duplicated_slug_and_bad_rtp(self):
        self.assertEqual(self.client.post(self.url, self.payload(), format="json").status_code, 201)
        duplicated = self.client.post(self.url, self.payload(title="Otra"), format="json")
        self.assertEqual(duplicated.status_code, 400)
        self.assertIn("slug", duplicated.data)
        self.assertEqual(self.client.post(self.url, self.payload(slug="x-1", rtp="120"), format="json").status_code, 400)

    def test_hu43_rejects_fake_or_heavy_cover(self):
        fake = SimpleUploadedFile("portada.png", b"<script>alert(1)</script>", content_type="image/png")
        self.assertEqual(self.client.post(self.url, {**self.payload(), "cover": fake}, format="multipart").status_code, 400)
        exe = SimpleUploadedFile("portada.exe", PNG, content_type="image/png")
        self.assertEqual(self.client.post(self.url, {**self.payload(), "cover": exe}, format="multipart").status_code, 400)
        with override_settings(GAME_COVER_MAX_BYTES=10):
            heavy = SimpleUploadedFile("portada.png", PNG, content_type="image/png")
            self.assertEqual(self.client.post(self.url, {**self.payload(), "cover": heavy}, format="multipart").status_code, 400)

    def test_hu43_only_admins_create(self):
        self.as_player()
        self.assertEqual(self.client.post(self.url, self.payload(), format="json").status_code, 403)
