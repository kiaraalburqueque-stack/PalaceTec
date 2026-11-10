from django.urls import path

from . import views

urlpatterns = [
    path("users", views.AdminUserListView.as_view(), name="admin-users"),
]
