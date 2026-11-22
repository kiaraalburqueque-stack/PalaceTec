from roulette.models import Game
from admin_api.models import AuditLog, ImmutableRecord
from .helpers import AdminTestBase


class HU50AuditLogTests(AdminTestBase):
    def test_hu50_admin_modification_creates_audit_row(self):
        game = Game.objects.get(slug="ruleta-europea")
        response = self.client.put(f"/api/v1/admin/games/{game.pk}", {"rtp": "95.00"}, format="json",
                                   REMOTE_ADDR="10.0.0.7")
        self.assertEqual(response.status_code, 200)
        log = AuditLog.objects.get()
        self.assertEqual((log.admin, log.method, log.action, log.resource, log.status_code, log.ip_address),
                         (self.admin, "PUT", "PUT admin-game-detail", f"/api/v1/admin/games/{game.pk}", 200, "10.0.0.7"))
        self.assertEqual(log.details, {"rtp": "95.00"})
        self.assertIsNotNone(log.created_at)

    def test_hu50_reads_and_player_actions_are_not_logged_and_secrets_masked(self):
        self.client.get("/api/v1/admin/users")
        self.as_player()
        self.client.put("/api/v1/users/profile", {"nickname": "jugador1"}, format="json")
        self.assertEqual(AuditLog.objects.count(), 0)
        self.client.force_authenticate(self.admin)
        self.client.patch(f"/api/v1/admin/users/{self.player.pk}/status",
                          {"is_active": False, "reason": "Fraude detectado", "password": "x"}, format="json")
        self.assertEqual(AuditLog.objects.get().details["password"], "***")

    def test_hu50_log_cannot_be_edited_or_deleted(self):
        self.client.patch(f"/api/v1/admin/games/{Game.objects.first().pk}/soft-delete")
        log = AuditLog.objects.get()
        log.action = "otra cosa"
        with self.assertRaises(ImmutableRecord):
            log.save()
        with self.assertRaises(ImmutableRecord):
            log.delete()
        with self.assertRaises(ImmutableRecord):
            AuditLog.objects.update(action="x")
        with self.assertRaises(ImmutableRecord):
            AuditLog.objects.all().delete()

    def test_hu50_list_with_pagination_and_search(self):
        for _ in range(12):
            self.client.put(f"/api/v1/admin/games/{Game.objects.first().pk}", {"rtp": "96.00"}, format="json")
        self.client.patch(f"/api/v1/admin/users/{self.player.pk}/status", {"is_active": False, "reason": "Revisión"}, format="json")
        data = self.client.get("/api/v1/admin/audit-logs").data
        self.assertEqual((data["count"], len(data["results"])), (13, 10))
        self.assertEqual(data["results"][0]["action"], "PATCH admin-user-status")
        self.assertEqual(self.client.get("/api/v1/admin/audit-logs?search=status").data["count"], 1)
        self.as_player()
        self.assertEqual(self.client.get("/api/v1/admin/audit-logs").status_code, 403)
