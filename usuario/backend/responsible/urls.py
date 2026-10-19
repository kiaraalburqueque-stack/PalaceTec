from django.urls import path
from .views import ExcludedPeopleView

urlpatterns = [path("excluded-people", ExcludedPeopleView.as_view(), name="excluded-people")]
