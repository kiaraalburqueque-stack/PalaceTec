package com.tecsup.palacetec

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.tecsup.palacetec.data.remote.BetKind
import com.tecsup.palacetec.data.remote.BetRequest
import com.tecsup.palacetec.data.remote.BetResponse
import com.tecsup.palacetec.data.remote.DailyLimit
import com.tecsup.palacetec.data.remote.Game
import com.tecsup.palacetec.data.remote.HistoryPage
import com.tecsup.palacetec.data.remote.LoginResponse
import com.tecsup.palacetec.data.remote.RegisterResponse
import com.tecsup.palacetec.data.remote.RoomState
import com.tecsup.palacetec.data.remote.RouletteRoom
import com.tecsup.palacetec.data.remote.WalletSummary
import com.tecsup.palacetec.data.remote.parseErrorBody
import com.tecsup.palacetec.ui.components.credits
import com.tecsup.palacetec.ui.components.limaDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los DTO contra las respuestas reales de Spring (src/test/resources/api). */
class ApiContractTest {

    private val strict: Gson = GsonBuilder().serializeNulls().create()

    /** Si sobra o falta un campo, o cambia su tipo o su nulo, el JSON de ida y vuelta no coincide. */
    private inline fun <reified T> matches(name: String): T {
        val raw = fixture(name)
        val value: T = Gson().fromJson(raw, object : TypeToken<T>() {}.type)
        assertEquals(name, JsonParser.parseString(raw), strict.toJsonTree(value))
        return value
    }

    @Test
    fun cadaRespuestaCoincideConSuDto() {
        matches<RegisterResponse>("01_register")
        matches<LoginResponse>("02_login")
        listOf("04_wallet", "10b_wallet_tras_apostar", "13_wallet_final").forEach { matches<WalletSummary>(it) }
        matches<List<Game>>("05_games")
        listOf("05b_limit_inicial", "07_set_limit", "10c_limit_tras_apostar", "e7_limite_aumento")
            .forEach { matches<DailyLimit>(it) }
        matches<List<RouletteRoom>>("06_rooms")
        listOf("08_state", "11_state_liquidado").forEach { matches<RoomState>(it) }
        listOf("09_bet_color", "09c_bet_pleno", "09d_bet_paridad").forEach { matches<BetResponse>(it) }
        listOf("06b_history_vacio", "10_history_pendiente", "12_history_resultado", "12b_history_pagina1")
            .forEach { matches<HistoryPage>(it) }
    }

    @Test
    fun estadosDeLaRonda() {
        val open = matches<RoomState>("08_state")
        assertTrue(open.round!!.acceptsBets)
        assertNull(open.round!!.winningNumber)
        assertEquals(listOf(10, 50, 100, 500), open.chipValues)
        assertEquals((0..36).toList(), open.numbers.map { it.number })
        val settled = matches<RoomState>("11_state_liquidado")
        assertFalse(settled.round!!.acceptsBets)
        assertEquals(14, settled.round!!.winningNumber)
        assertEquals(settled.round!!.id, settled.recentResults.first().roundId)
    }

    @Test
    fun salaSinRondaTodavia() {
        val json = JsonParser.parseString(fixture("08_state")).asJsonObject
        json.add("round", com.google.gson.JsonNull.INSTANCE)
        assertNull(Gson().fromJson(json, RoomState::class.java).round)
    }

    @Test
    fun laApuestaViajaConLosCamposDelBackend() {
        val request = BetRequest(7564, BetKind.STRAIGHT_UP, "0", listOf(10, 50), "11ab0e4f-6690-4558-9f3a-da60a99dd075")
        assertEquals(
            """{"round_id":7564,"kind":"STRAIGHT_UP","selection":"0","chips":[10,50],""" +
                """"reference":"11ab0e4f-6690-4558-9f3a-da60a99dd075"}""",
            Gson().toJson(request),
        )
    }

    @Test
    fun erroresConCodigo() {
        mapOf(
            "e1_sin_limite" to "daily_limit_required",
            "e2_referencia_conflicto" to "reference_conflict",
            "e5_saldo_insuficiente" to "insufficient_balance",
            "e6_ronda_cerrada" to "bets_closed",
            "e8_limite_superado" to "daily_limit_exceeded",
        ).forEach { (name, code) ->
            val error = parseErrorBody(400, fixture(name))
            assertEquals(code, error.code)
            assertTrue(error.fields.isEmpty())
        }
    }

    @Test
    fun erroresPorCampoTextoYDetalle() {
        assertEquals("El monto debe respetar el minimo por apuesta y el maximo acumulado por ronda.",
            parseErrorBody(400, fixture("e3_sobre_maximo")).message)
        assertEquals("Fichas autorizadas: 10, 50, 100 y 500.", parseErrorBody(400, fixture("e4_ficha_invalida")).message)
        assertEquals("Credenciales inválidas", parseErrorBody(401, fixture("e11_login_malo")).message)
        assertEquals("El token no es válido o venció.", parseErrorBody(401, fixture("e10_401_token")).message)
        assertEquals(setOf("email", "dni"), parseErrorBody(409, fixture("e12_correo_repetido")).fields.keys)
        assertEquals(
            setOf("email", "password", "dni", "birth_date"),
            parseErrorBody(400, fixture("e13_registro_invalido")).fields.keys,
        )
        assertEquals("Ocurrió un error (502). Inténtalo nuevamente.", parseErrorBody(502, "<html>x</html>").message)
        assertEquals("Ocurrió un error (500). Inténtalo nuevamente.", parseErrorBody(500, "").message)
    }

    @Test
    fun fechasYDecimales() {
        assertEquals("24/11/2026 12:02", limaDateTime("2026-11-24T12:02:11.277335-05:00"))
        assertEquals("24/11/2026 12:02", limaDateTime("2026-11-24T12:02:11-05:00"))
        assertEquals("24/11/2026 12:02", limaDateTime("2026-11-24T17:02:11.078Z"))
        assertEquals("1,010.00", credits("1010.00"))
        assertEquals("-10.50", credits("-10.5"))
        assertEquals("—", credits(null))
    }
}
