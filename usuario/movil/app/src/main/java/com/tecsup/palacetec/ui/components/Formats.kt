package com.tecsup.palacetec.ui.components

import java.math.BigDecimal
import java.math.RoundingMode
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
    val shown = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
    shown.timeZone = TimeZone.getTimeZone("America/Lima")
    return isoMillis(iso)?.let { shown.format(it) } ?: iso
}

/** Milisegundos de una fecha ISO del backend, o null si no es válida. */
fun isoMillis(iso: String): Long? = try {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(iso.replace(Regex("[.][0-9]+"), ""))?.time
} catch (_: ParseException) {
    null
}

/** "1250.00" -> "+1,250.00"; las pérdidas ya traen su signo. */
fun signedCredits(value: String): String =
    if ((value.toBigDecimalOrNull() ?: BigDecimal.ZERO).signum() > 0) "+" + credits(value) else credits(value)

/** 4530 -> "1 h 15 min". */
fun duration(seconds: Long): String {
    val minutes = seconds / 60
    return when {
        minutes == 0L -> "$seconds s"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/** Parte de total, entre 0 y 1. Un total de cero ya está completo. */
fun fraction(part: String, total: String?): Float {
    val whole = total?.toBigDecimalOrNull() ?: BigDecimal.ZERO
    if (whole.signum() <= 0) return 1f
    val value = part.toBigDecimalOrNull() ?: BigDecimal.ZERO
    return value.divide(whole, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
}
