from django.urls import path

from .views import AssistantChatView, OddsView

urlpatterns = [
    path("chat", AssistantChatView.as_view(), name="assistant-chat"),
    path("odds", OddsView.as_view(), name="assistant-odds"),
]
