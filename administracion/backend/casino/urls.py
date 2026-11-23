from django.urls import path
from .views import BlackjackView, BlackjackActionView, SportEventsView, SportTicketsView

urlpatterns = [path('blackjack', BlackjackView.as_view()),
    path('blackjack/<int:hand_id>/action', BlackjackActionView.as_view()),
    path('sports/events', SportEventsView.as_view()), path('sports/tickets', SportTicketsView.as_view())]
