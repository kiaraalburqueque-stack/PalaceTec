from decimal import Decimal

LOW_BALANCE_THRESHOLD = Decimal("10.00")


def low_balance_data(balance):
    return {"low_balance_threshold": format(LOW_BALANCE_THRESHOLD, ".2f"),
            "is_low_balance": balance < LOW_BALANCE_THRESHOLD}
