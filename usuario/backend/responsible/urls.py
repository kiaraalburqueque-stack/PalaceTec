from django.urls import path
from .views import ExcludedPeopleView, DailyLimitView

urlpatterns = [path("excluded-people", ExcludedPeopleView.as_view(), name="excluded-people"),
               path("daily-limit", DailyLimitView.as_view(), name="daily-limit")]
