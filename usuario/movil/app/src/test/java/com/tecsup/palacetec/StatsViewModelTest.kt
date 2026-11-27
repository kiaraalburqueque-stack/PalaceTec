package com.tecsup.palacetec

import com.tecsup.palacetec.data.remote.BalancePoint
import com.tecsup.palacetec.data.repository.StatsRepository
import com.tecsup.palacetec.ui.components.compact
import com.tecsup.palacetec.ui.components.dayMonth
import com.tecsup.palacetec.ui.components.percent
import com.tecsup.palacetec.ui.stats.StatsViewModel
import com.tecsup.palacetec.ui.stats.balanceChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private lateinit var server: TestServer

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default.limitedParallelism(1))
        server = TestServer()
        server.reply("GET /stats/win-rate", "17_win_rate")
        server.reply("GET /stats/biggest-win", "17b_biggest_win")
        server.reply("GET /stats/balance-history", "17c_balance_history")
        server.reply("GET /stats/top-games", "17d_top_games")
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private fun loaded() = StatsViewModel(StatsRepository(server.api)).apply {
        load()
        state.await { !it.loading && !it.loadingBalance }
    }

    @Test
    fun cargaTodo() {
        val state = loaded().state.value
        assertNull(state.error)
        assertEquals(33.33, state.winRate!!.winRate, 0.0)
        assertEquals("350.00", state.biggestWin!!.net)
        assertEquals(7, state.balance!!.points.size)
        assertEquals(6, state.selectedPoint)
        assertEquals(listOf("Ruleta Europea", "Blackjack"), state.topGames!!.results.map { it.title })
    }

    @Test
    fun unJugadorNuevoNoTieneRecordNiJuegos() {
        server.replyBody("GET /stats/biggest-win", """{"record":null}""")
        server.replyBody("GET /stats/top-games", """{"results":[],"total_rounds":0,"timezone":"America/Lima"}""")
        val state = loaded().state.value
        assertTrue(state.loaded)
        assertNull(state.biggestWin)
        assertTrue(state.topGames!!.results.isEmpty())
    }

    @Test
    fun unErrorSePuedeReintentarSinPerderElSaldo() {
        server.disconnect("GET /stats/win-rate")
        val vm = loaded()
        assertNotNull(vm.state.value.error)
        assertNotNull(vm.state.value.balance)
        server.reply("GET /stats/win-rate", "17_win_rate")
        vm.load()
        assertNull(vm.state.await { it.loaded && !it.loading }.error)
    }

    @Test
    fun cambiarElPlazoSoloPideElSaldo() {
        val vm = loaded()
        server.replyBody(
            "GET /stats/balance-history",
            """{"period":30,"opening_balance":"0.00","closing_balance":"1000.00","timezone":"America/Lima",
               "points":[{"date":"2026-10-27","balance":"0.00","movements":0},
                         {"date":"2026-11-25","balance":"1000.00","movements":1}]}""",
        )
        vm.selectPeriod(30)
        val state = vm.state.await { it.balance?.period == 30 }
        assertEquals(30, state.period)
        assertEquals(1, state.selectedPoint)
        assertEquals(1, server.count("GET /stats/win-rate"))
        assertEquals(listOf("period=7", "period=30"), server.queries("GET /stats/balance-history"))
        vm.selectPeriod(30)
        assertEquals(2, server.count("GET /stats/balance-history"))
    }

    @Test
    fun siFallaElSaldoSeMuestraSuErrorYSeReintenta() {
        server.disconnect("GET /stats/balance-history")
        val vm = loaded()
        assertNotNull(vm.state.value.balanceError)
        assertTrue(vm.state.value.loaded)
        vm.onChartTouch(0.5f)
        server.reply("GET /stats/balance-history", "17c_balance_history")
        vm.loadBalance()
        assertNull(vm.state.await { it.balance != null }.balanceError)
    }

    @Test
    fun tocarElGraficoLeeElDiaMasCercano() {
        val vm = loaded()
        vm.onChartTouch(0.5f)
        assertEquals(3, vm.state.value.selectedPoint)
        vm.onChartTouch(-2f)
        assertEquals(0, vm.state.value.selectedPoint)
        vm.onChartTouch(9f)
        assertEquals(6, vm.state.value.selectedPoint)
    }

    @Test
    fun elEjeUsaValoresRedondosYContieneTodosLosPuntos() {
        fun chart(vararg balances: String) = balanceChart(balances.map { BalancePoint("2026-11-25", it, 0) })
        assertNull(chart())
        assertEquals(listOf(900L, 1000L, 1100L, 1200L), chart("980.00", "1180.00")!!.ticks)
        // Sin cambios: la línea queda al medio; en cero, sobre la base.
        assertEquals(listOf(0.5f, 0.5f), chart("1000.00", "1000.00")!!.heights)
        assertEquals(listOf(0f), chart("0.00")!!.heights)
        listOf(chart("0.00", "0.50"), chart("12.34", "987654.32"), chart("999.99", "1000.01")).forEach { chart ->
            assertTrue(chart!!.heights.all { it in 0f..1f })
            assertTrue(chart.ticks.size in 2..5)
            assertEquals(chart.ticks.distinct(), chart.ticks)
        }
    }

    @Test
    fun formatos() {
        assertEquals("33.33 %", percent(33.33))
        assertEquals("50 %", percent(50.0))
        assertEquals("25/11", dayMonth("2026-11-25"))
        assertEquals("1,000", compact(1_000))
        assertEquals("12.5K", compact(12_500))
        assertEquals("2M", compact(2_000_000))
    }
}
