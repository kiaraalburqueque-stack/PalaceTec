package com.tecsup.palacetec.repository;

import java.math.BigDecimal;

/** Créditos apostados y pagados en apuestas con resultado; cero si no hay ninguna. */
public interface SettledTotals {

    BigDecimal getWagered();

    BigDecimal getPaid();
}
