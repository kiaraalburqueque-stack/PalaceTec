from django.urls import path

from . import audit_logs, audit_transactions, kpis, monitor, reports, views

urlpatterns = [
    path("users", views.AdminUserListView.as_view(), name="admin-users"),
    path("users/<int:user_id>/status", views.AdminUserStatusView.as_view(), name="admin-user-status"),
    path("users/<int:user_id>/sanctions", views.AdminUserSanctionsView.as_view(), name="admin-user-sanctions"),
    path("games", views.AdminGameListView.as_view(), name="admin-games"),
    path("games/<int:game_id>", views.AdminGameDetailView.as_view(), name="admin-game-detail"),
    path("games/<int:game_id>/soft-delete", views.AdminGameSoftDeleteView.as_view(), name="admin-game-soft-delete"),
    path("games/<int:game_id>/restore", views.AdminGameRestoreView.as_view(), name="admin-game-restore"),
    path("responsible-gaming/users", monitor.ResponsibleMonitorView.as_view(), name="admin-responsible-users"),
    path("kpis", kpis.KpiView.as_view(), name="admin-kpis"),
    path("reports/financial-flow", reports.FinancialFlowView.as_view(), name="admin-financial-flow"),
    path("audit/transactions", audit_transactions.TransactionAuditView.as_view(), name="admin-audit-transactions"),
    path("audit/transactions/export", audit_transactions.TransactionExportView.as_view(), name="admin-audit-transactions-export"),
    path("audit-logs", audit_logs.AuditLogListView.as_view(), name="admin-audit-logs"),
    path("promotions", views.AdminPromotionView.as_view(), name="admin-promotions"),
]
