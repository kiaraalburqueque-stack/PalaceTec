from decimal import Decimal
from .test_game import GameTestBase
from . import payout


class HU24DozenColumnTests(GameTestBase):
    def test_all_numbers_pay_the_correct_dozen_and_column(self):
        for number in range(37):
            for selection in ['1', '2', '3']:
                dozen = Decimal(30) if number and (number-1)//12+1 == int(selection) else Decimal(0)
                column = Decimal(30) if number and (number-1)%3+1 == int(selection) else Decimal(0)
                self.assertEqual(payout.docena(10, selection, number), dozen)
                self.assertEqual(payout.columna(10, selection, number), column)

    def test_api_accepts_new_kinds_and_rejects_invalid_selection(self):
        for kind in ['DOZEN', 'COLUMN']:
            self.assertEqual(self.client.post(self.url, self.data(kind=kind, selection='2'), format='json').status_code, 201)
            self.assertEqual(self.client.post(self.url, self.data(kind=kind, selection='4'), format='json').status_code, 400)

    def test_total_stake_and_room_cap(self):
        response = self.client.post(self.url, self.data(kind='DOZEN', selection='1', chips=[10, 50, 100]), format='json')
        self.assertEqual(response.status_code, 201)
        self.assertEqual(Decimal(response.data['amount']), 160)
        self.assertEqual(self.client.post(self.url, self.data(chips=[500]), format='json').status_code, 400)
