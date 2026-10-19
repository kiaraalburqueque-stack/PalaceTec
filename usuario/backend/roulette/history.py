from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response
from .models import Bet
from .serializers import HistoryBetSerializer
from .views import AuthenticatedView


class HistoryPagination(PageNumberPagination):
    page_size = 20
    page_size_query_param = "page_size"
    max_page_size = 100


class BetHistoryView(AuthenticatedView):
    def get_queryset(self, request):
        return Bet.objects.filter(user=request.user).select_related("round__room__game").order_by("-created_at", "-id")

    def get(self, request):
        pagination = HistoryPagination()
        page = pagination.paginate_queryset(self.get_queryset(request), request, view=self)
        return pagination.get_paginated_response(HistoryBetSerializer(page, many=True).data)
