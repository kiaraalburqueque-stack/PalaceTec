from .helpers import AdminTestBase, make_user

URL = "/api/v1/admin/users"


class HU41UserListTests(AdminTestBase):
    def test_hu41_paginates_ten_per_page(self):
        for index in range(12):
            make_user(f"p{index}@palacetec.pe", f"3000{index:04d}")
        first = self.client.get(URL)
        self.assertEqual(first.status_code, 200)
        self.assertEqual(first.data["count"], 14)
        self.assertEqual(len(first.data["results"]), 10)
        second = self.client.get(URL + "?page=2")
        self.assertEqual(len(second.data["results"]), 4)
        row = first.data["results"][0]
        self.assertEqual(set(row) >= {"email", "dni", "role", "status", "balance"}, True)

    def test_hu41_filters_by_status_role_and_search(self):
        suspended = make_user("baja@palacetec.pe", "40000000", is_active=False)
        data = self.client.get(URL + "?status=suspended").data
        self.assertEqual([row["id"] for row in data["results"]], [suspended.pk])
        data = self.client.get(URL + "?role=admin").data
        self.assertEqual([row["email"] for row in data["results"]], ["admin@palacetec.pe"])
        data = self.client.get(URL + "?role=user&status=active").data
        self.assertEqual([row["email"] for row in data["results"]], ["jugador@palacetec.pe"])
        self.assertEqual(self.client.get(URL + "?search=JUGADOR").data["count"], 1)
        self.assertEqual(self.client.get(URL + "?search=4000").data["results"][0]["email"], "baja@palacetec.pe")

    def test_hu41_ordering_and_invalid_filters(self):
        data = self.client.get(URL + "?ordering=email").data
        self.assertEqual([row["email"] for row in data["results"]], ["admin@palacetec.pe", "jugador@palacetec.pe"])
        self.assertEqual(self.client.get(URL + "?status=otro").status_code, 400)

    def test_hu41_only_admins(self):
        self.as_player()
        self.assertEqual(self.client.get(URL).status_code, 403)
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get(URL).status_code, 401)
