package com.tecsup.palacetec

import com.tecsup.palacetec.data.remote.ApiException
import com.tecsup.palacetec.data.remote.BetKind
import com.tecsup.palacetec.data.remote.LoginRequest
import com.tecsup.palacetec.data.remote.RouletteRoom
import com.tecsup.palacetec.data.repository.CatalogRepository
import com.tecsup.palacetec.data.repository.DailyLimitRepository
import com.tecsup.palacetec.data.repository.HistoryRepository
import com.tecsup.palacetec.data.repository.RouletteRepository
import com.tecsup.palacetec.data.repository.WalletRepository
import com.tecsup.palacetec.data.remote.apiCall
import com.tecsup.palacetec.ui.auth.AuthValidators
import com.tecsup.palacetec.ui.bets.BetsViewModel
import com.tecsup.palacetec.ui.home.HomeViewModel
import com.tecsup.palacetec.ui.roulette.RouletteViewModel
import com.tecsup.palacetec.ui.wallet.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

    private lateinit var server: TestServer
    private val dao = FakeHistoryDao()
    private val analytics = FakeAnalytics()
    private val room = RouletteRoom(1, "Ruleta baja", "10.00", "500.00", 0)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default.limitedParallelism(1))
        server = TestServer()
        server.reply("GET /wallet/summary", "04_wallet")
        server.reply("GET /games", "05_games")
        server.reply("GET /responsible-gaming/daily-limit", "05b_limit_inicial")
        server.reply("GET /rooms", "06_rooms")
        server.reply("GET /rooms/1/state", "08_state")
        server.reply("GET /users/bets-history", "12_history_resultado")
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private fun history() = HistoryRepository(server.api, dao) { "a@example.com" }

    private fun roulette() = RouletteViewModel(
        RouletteRepository(server.api, analytics), WalletRepository(server.api), history(), DailyLimitRepository(server.api),
    )

    private fun readyToBet() = roulette().apply {
        onVisible()
        selectRoom(room)
        selectKind(BetKind.COLOR)
        select("rojo")
        addChip(10)
        state.await { it.table != null && it.wallet != null && it.blockedReason == null }
    }

    // --- Sesión ---

    @Test
    fun soloLasRutasPrivadasLlevanElToken() {
        server.reply("POST /auth/login", "02_login")
        runBlocking {
            apiCall { server.api.login(LoginRequest("a@example.com", "x")) }
            assertNull(server.lastAuthorization)
            apiCall { server.api.walletSummary() }
            assertEquals("Bearer token-de-prueba", server.lastAuthorization)
        }
    }

    @Test
    fun un401CierraLaSesionYConservaElMensaje() {
        server.reply("GET /wallet/summary", "e10_401_token", 401)
        val vm = WalletViewModel(WalletRepository(server.api))
        vm.refresh()
        val state = vm.state.await { !it.loading }
        assertEquals("El token no es válido o venció.", state.error)
        assertEquals(listOf("token-de-prueba" to "El token no es válido o venció."), server.rejected.toList())
    }

    @Test
    fun laExclusionCierraLaSesionPeroOtro403No() {
        server.replyBody("GET /games", """{"code":"access_not_allowed","detail":"Acceso no permitido."}""", 403)
        server.replyBody("GET /rooms", """{"detail":"Prohibido."}""", 403)
        assertThrows(ApiException::class.java) { runBlocking { apiCall { server.api.rooms() } } }
        assertTrue(server.rejected.isEmpty())
        assertThrows(ApiException::class.java) { runBlocking { apiCall { server.api.games() } } }
        assertEquals(listOf("token-de-prueba" to "Acceso no permitido."), server.rejected.toList())
    }

    @Test
    fun unLoginFallidoNoCierraOtraSesion() {
        server.reply("POST /auth/login", "e11_login_malo", 401)
        val error = assertThrows(ApiException::class.java) {
            runBlocking { apiCall { server.api.login(LoginRequest("a@example.com", "x")) } }
        }
        assertEquals("Credenciales inválidas", error.message)
        assertTrue(server.rejected.isEmpty())
    }

    // --- Inicio y billetera ---

    @Test
    fun inicioCargaTodoYSeRecuperaDeUnError() {
        server.disconnect("GET /games")
        val vm = HomeViewModel(
            WalletRepository(server.api), CatalogRepository(server.api), DailyLimitRepository(server.api),
        )
        assertTrue(vm.state.value.loading)
        vm.load()
        assertNotNull(vm.state.await { !it.loading }.error)
        server.reply("GET /games", "05_games")
        vm.load()
        val state = vm.state.await { !it.loading && it.wallet != null }
        assertNull(state.error)
        assertEquals("1000.00", state.wallet?.balance)
        assertEquals(3, state.games.size)
        assertEquals(1, state.games.count { it.roomsUrl != null })
        assertFalse(state.dailyLimit!!.configured)
    }

    // --- Ruleta ---

    @Test
    fun elSondeoSeDetieneAlSalirYVuelveAlEntrar() {
        val vm = readyToBet()
        Thread.sleep(2_300)
        assertTrue(server.count("GET /rooms/1/state") in 3..4)
        vm.onHidden()
        Thread.sleep(300)
        val stopped = server.count("GET /rooms/1/state")
        Thread.sleep(2_200)
        assertEquals(stopped, server.count("GET /rooms/1/state"))
        vm.onVisible()
        Thread.sleep(500)
        assertEquals(stopped + 1, server.count("GET /rooms/1/state"))
        vm.leaveRoom()
        Thread.sleep(1_500)
        assertEquals(stopped + 1, server.count("GET /rooms/1/state"))
    }

    @Test
    fun rondaCerradaYSaldoInsuficienteBloqueanElBoton() {
        val vm = readyToBet()
        repeat(2) { vm.addChip(500) }
        assertEquals("El máximo por ronda es 500.00", vm.state.value.blockedReason)
        vm.clearChips()
        assertEquals("Agrega fichas", vm.state.value.blockedReason)
        vm.addChip(10)
        server.replyBody("GET /wallet/summary", fixture("04_wallet").replace("1000.00", "5.00"))
        vm.onVisible()
        vm.state.await { it.blockedReason == "Saldo insuficiente" }
        server.reply("GET /rooms/1/state", "11_state_liquidado")
        vm.state.await { it.blockedReason == "Espera la próxima ronda" }
        server.disconnect("GET /rooms/1/state")
        vm.state.await { it.blockedReason == "Sin conexión con la mesa" }
        vm.placeBet()
        assertEquals(0, server.count("POST /rooms/1/bets"))
        vm.onHidden()
    }

    @Test
    fun sinLimiteDiarioAbreElDialogoYPermiteApostar() {
        server.reply("POST /rooms/1/bets", "e1_sin_limite", 400)
        server.reply("POST /responsible-gaming/daily-limit", "07_set_limit")
        val vm = readyToBet()
        vm.placeBet()
        vm.state.await { it.showLimitDialog && !it.placing }
        vm.saveLimit("5000.50")
        vm.state.await { !it.showLimitDialog }
        assertEquals(listOf("""{"amount":"5000.50"}"""), server.bodies("POST /responsible-gaming/daily-limit"))
        assertEquals(listOf(10), vm.state.value.chips)
        vm.onHidden()
    }

    @Test
    fun losRechazosMuestranElMensajeDelBackendYRefrescanElSaldo() {
        val vm = readyToBet()
        mapOf(
            "e5_saldo_insuficiente" to "Saldo insuficiente.",
            "e6_ronda_cerrada" to "No va mas: la ronda no acepta apuestas.",
            "e8_limite_superado" to "La apuesta supera tu limite diario disponible.",
        ).forEach { (name, message) ->
            server.reply("POST /rooms/1/bets", name, 400)
            val wallets = server.count("GET /wallet/summary")
            vm.placeBet()
            vm.state.await { it.message == message && it.messageIsError && !it.placing }
            Thread.sleep(300)
            assertTrue(server.count("GET /wallet/summary") > wallets)
        }
        vm.onHidden()
    }

    @Test
    fun unaApuestaSinRespuestaSeReintentaConLaMismaReferencia() {
        val vm = readyToBet()
        server.disconnect("POST /rooms/1/bets")
        vm.placeBet()
        vm.state.await { it.messageIsError && !it.placing }
        server.reply("POST /rooms/1/bets", "09_bet_color", 201)
        vm.placeBet()
        vm.state.await { it.roundBets.isNotEmpty() }
        assertEquals(1, server.bodies("POST /rooms/1/bets").toSet().size)
        assertTrue(vm.state.value.chips.isEmpty())
        // Una apuesta nueva e igual ya es otra: lleva otra referencia.
        vm.addChip(10)
        vm.placeBet()
        vm.state.await { it.roundBets.size == 2 }
        assertEquals(2, server.bodies("POST /rooms/1/bets").toSet().size)
        vm.onHidden()
    }

    @Test
    fun alLiquidarseMuestraNumeroPremioYSaldo() {
        server.reply("POST /rooms/1/bets", "09_bet_color", 201)
        val vm = readyToBet()
        vm.placeBet()
        vm.state.await { it.roundBets.size == 1 }
        server.reply("GET /wallet/summary", "13_wallet_final")
        server.reply("GET /rooms/1/state", "11_state_liquidado")
        val state = vm.state.await { it.result?.payout != null }
        assertEquals(14, state.result!!.number)
        assertEquals("rojo", state.result!!.color)
        assertEquals("30.00", state.result!!.staked!!.toPlainString())
        assertEquals("40.00", state.result!!.payout!!.toPlainString())
        assertEquals("1010.00", state.wallet!!.balance)
        vm.onHidden()
    }

    @Test
    fun siVuelveTrasVariasRondasMuestraLaSuya() {
        server.reply("POST /rooms/1/bets", "09_bet_color", 201)
        val vm = readyToBet()
        vm.placeBet()
        vm.state.await { it.roundBets.size == 1 }
        vm.onHidden()
        server.replyBody(
            "GET /rooms/1/state",
            fixture("11_state_liquidado").replace(
                "\"recent_results\":[", "\"recent_results\":[{\"round_id\":7570,\"number\":0,\"color\":\"verde\"},",
            ),
        )
        vm.onVisible()
        val result = vm.state.await { it.result?.payout != null }.result!!
        assertEquals(7564L, result.roundId)
        assertEquals(14, result.number)
        vm.onHidden()
    }

    // --- Historial ---

    @Test
    fun sinConexionElHistorialSaleDeLaCacheDelMismoJugador() {
        BetsViewModel(history(), analytics).run {
            refresh()
            assertEquals(3, state.await { !it.loading }.items.size)
        }
        server.disconnect("GET /users/bets-history")
        BetsViewModel(history(), analytics).run {
            refresh()
            val offline = state.await { !it.loading }
            assertTrue(offline.fromCache)
            assertEquals(listOf(62L, 61L, 60L), offline.items.map { it.id })
            assertEquals(listOf(10), offline.items.first().chips)
            assertNotNull(offline.error)
        }
        BetsViewModel(HistoryRepository(server.api, dao) { "otro@example.com" }, analytics).run {
            refresh()
            assertTrue(state.await { !it.loading }.items.isEmpty())
        }
    }

    @Test
    fun elHistorialPaginaSinRepetir() {
        server.reply("GET /users/bets-history", "12b_history_pagina1")
        val vm = BetsViewModel(history(), analytics)
        vm.refresh()
        assertTrue(vm.state.await { !it.loading }.hasMore)
        server.reply("GET /users/bets-history", "12_history_resultado")
        vm.loadMore()
        val state = vm.state.await { it.items.size == 3 }
        assertFalse(state.hasMore)
    }

    // --- Validaciones ---

    @Test
    fun validacionesDeRegistro() {
        assertNull(AuthValidators.email(" prueba.movil@example.com "))
        assertNotNull(AuthValidators.email("sin-arroba"))
        assertNotNull(AuthValidators.email("a@b"))
        assertNull(AuthValidators.newPassword("Clave-segura1"))
        listOf("Corta-1", "sinmayuscula-1", "SinSimbolo1").forEach { assertNotNull(AuthValidators.newPassword(it)) }
        assertNull(AuthValidators.dni("00086267"))
        assertNotNull(AuthValidators.dni("1234567"))
        assertEquals("2000-05-15", AuthValidators.isoDate("15052000"))
        assertNull(AuthValidators.isoDate("31022000"))
        assertNotNull(AuthValidators.birthDate("01012020"))
    }
}
