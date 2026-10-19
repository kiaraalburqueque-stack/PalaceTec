from django.urls import path
from .views import ExcludedPeopleView, DailyLimitView, SelfExcludeView

urlpatterns = [path("excluded-people", ExcludedPeopleView.as_view(), name="excluded-people"),
               path("self-exclude", SelfExcludeView.as_view(), name="self-exclude"),
               path("daily-limit", DailyLimitView.as_view(), name="daily-limit")]
