package com.tecsup.palacetec.ui.auth

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Las mismas reglas que valida el backend (Emails, StrongPassword y Adult). */
object AuthValidators {

    private val EMAIL = Regex("[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)*\\.[A-Za-z0-9-]{2,63}")
    private val LIMA = TimeZone.getTimeZone("America/Lima")

    fun email(value: String): String? = when {
        value.isBlank() -> "Ingresa tu correo."
        value.trim().length > 254 || !EMAIL.matches(value.trim()) -> "Escribe un correo válido."
        else -> null
    }

    fun loginPassword(value: String): String? = if (value.isEmpty()) "Ingresa tu contraseña." else null

    fun newPassword(value: String): String? = when {
        value.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
        value.none { it.isUpperCase() } -> "La contraseña debe incluir una mayúscula."
        value.none { !it.isLetterOrDigit() && !it.isWhitespace() } -> "La contraseña debe incluir un símbolo."
        else -> null
    }

    fun dni(value: String): String? =
        if (value.length == 8 && value.all { it in '0'..'9' }) null else "El DNI debe tener 8 dígitos."

    /** Recibe los ocho dígitos DDMMAAAA. */
    fun birthDate(digits: String): String? {
        val iso = isoDate(digits) ?: return "Escribe una fecha válida (día/mes/año)."
        val today = Calendar.getInstance(LIMA)
        val (year, month, day) = iso.split("-").map { it.toInt() }
        val thisMonth = today.get(Calendar.MONTH) + 1
        val hadBirthday = thisMonth > month ||
            (thisMonth == month && today.get(Calendar.DAY_OF_MONTH) >= day)
        val age = today.get(Calendar.YEAR) - year - if (hadBirthday) 0 else 1
        return if (age >= 18) null else "Debes tener al menos 18 años cumplidos."
    }

    /** DDMMAAAA -> AAAA-MM-DD, o null si la fecha no existe. */
    fun isoDate(digits: String): String? {
        if (digits.length != 8) return null
        val iso = "${digits.substring(4)}-${digits.substring(2, 4)}-${digits.substring(0, 2)}"
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        return try {
            format.parse(iso)
            iso.takeIf { digits.substring(4).toInt() >= 1900 }
        } catch (_: ParseException) {
            null
        }
    }
}
