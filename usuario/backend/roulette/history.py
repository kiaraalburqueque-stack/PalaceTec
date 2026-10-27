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
        from .queries import filter_dates
        bets = Bet.objects.filter(user=request.user).select_related("round__room__game").order_by("-created_at", "-id")
        return filter_dates(bets, request.query_params)

    def get(self, request):
        from django.db.models import Value, CharField
        from casino.models import Play
        from casino.views import history_data
        from .queries import filter_dates
        bets = self.get_queryset(request)
        plays = filter_dates(Play.objects.filter(user=request.user), request.query_params)
        rows = bets.order_by().annotate(source=Value('roulette', output_field=CharField())).values('source', 'id', 'created_at').union(
            plays.order_by().annotate(source=Value('casino', output_field=CharField())).values('source', 'id', 'created_at')).order_by('-created_at', '-source', '-id')
        pagination = HistoryPagination()
        page = pagination.paginate_queryset(rows, request, view=self)
        roulette_objects = {bet.pk: bet for bet in bets.filter(pk__in=[r['id'] for r in page if r['source'] == 'roulette'])}
        casino_objects = {play.pk: play for play in plays.filter(pk__in=[r['id'] for r in page if r['source'] == 'casino'])}
        results = [{**HistoryBetSerializer(roulette_objects[row['id']]).data, 'source': 'roulette'} if row['source'] == 'roulette'
                   else history_data(casino_objects[row['id']]) for row in page]
        return pagination.get_paginated_response(results)


class StatsSummaryView(AuthenticatedView):
    def get(self, request):
        from .queries import filter_dates, settled_summary
        bets = filter_dates(Bet.objects.filter(user=request.user), request.query_params)
        from casino.models import Play
        from casino.services import play_summary, merge_summary
        plays = filter_dates(Play.objects.filter(user=request.user), request.query_params)
        return Response({**merge_summary(settled_summary(bets), play_summary(plays)), "scope": "settled_bets", "timezone": "America/Lima"})
