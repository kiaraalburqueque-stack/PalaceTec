package com.tecsup.palacetec.ui.components

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** "1250.00" -> "1,250.00". Son créditos virtuales, sin símbolo de moneda. */
fun credits(value: String?): String {
    val amount = value?.toBigDecimalOrNull() ?: return "—"
    return DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(amount)
}

/** "COLOR" + "rojo" -> "Color: rojo". */
fun betLabel(kind: String, selection: String): String = when (kind) {
    "STRAIGHT_UP" -> "Pleno: $selection"
    "COLOR" -> "Color: $selection"
    "PARITY" -> "Paridad: $selection"
    "DOZEN" -> "Docena: $selection"
    "COLUMN" -> "Columna: $selection"
    else -> selection
}
