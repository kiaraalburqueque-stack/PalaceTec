package com.tecsup.palacetec.ui.components

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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

/** Fecha ISO del backend (con o sin microsegundos) -> "24/11/2026 11:30" en hora de Lima. */
fun limaDateTime(iso: String): String {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    val shown = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
    shown.timeZone = TimeZone.getTimeZone("America/Lima")
    return try {
        parser.parse(iso.replace(Regex("[.][0-9]+"), ""))?.let { shown.format(it) } ?: iso
    } catch (_: ParseException) {
        iso
    }
}
