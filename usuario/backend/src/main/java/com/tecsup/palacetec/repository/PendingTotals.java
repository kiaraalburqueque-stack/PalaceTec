package com.tecsup.palacetec.repository;

import java.math.BigDecimal;

/** Apuestas que esperan resultado. */
public interface PendingTotals {

    long getCount();

    BigDecimal getWagered();
}
