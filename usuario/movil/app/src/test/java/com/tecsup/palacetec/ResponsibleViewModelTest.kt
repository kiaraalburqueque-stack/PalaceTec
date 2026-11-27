package com.tecsup.palacetec

import com.tecsup.palacetec.data.local.Session
import com.tecsup.palacetec.data.local.SessionStore
import com.tecsup.palacetec.data.repository.AuthRepository
import com.tecsup.palacetec.data.repository.DailyLimitRepository
import com.tecsup.palacetec.data.repository.ResponsibleRepository
import com.tecsup.palacetec.ui.components.duration
import com.tecsup.palacetec.ui.components.fraction
import com.tecsup.palacetec.ui.components.isoMillis
import com.tecsup.palacetec.ui.components.signedCredits
import com.tecsup.palacetec.ui.responsible.ResponsibleViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
class ResponsibleViewModelTest {

    private lateinit var server: TestServer
    private lateinit var session: SessionStore
    private lateinit var auth: AuthRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dao = FakeHistoryDao()
    private var now = checkNotNull(isoMillis("2026-11-25T15:00:00+00:00"))

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default.limitedParallelism(1))
        server = TestServer()
        server.reply("GET /responsible-gaming/daily-limit", "07_set_limit")
        server.reply("GET /responsible-gaming/session-summary", "15_session_summary")
        server.reply("GET /responsible-gaming/risk-alerts", "15b_risk_alerts")
        session = SessionStore(MemoryDataStore(), scope)
        auth = AuthRepository(server.api, session, dao, FakeAnalytics())
        runBlocking { session.save("token-de-prueba", "a@example.com") }
        session.session.await { it is Session.LoggedIn }
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
        Dispatchers.resetMain()
    }

    private fun loaded() =
        ResponsibleViewModel(DailyLimitRepository(server.api), ResponsibleRepository(server.api), auth) { now }.apply {
            load()
            state.await { !it.loading }
        }

    @Test
    fun cargaLimiteResumenYAlertas() {
        val state = loaded().state.value
        assertNull(state.error)
        assertEquals("5000.50", state.limit!!.limit)
        assertEquals("-20.00", state.summary!!.net)
        assertEquals(4530L, state.summary.durationSeconds)
        // La más reciente sin usar, aunque haya otra posterior ya usada.
        assertEquals(12L, state.pauseOffer!!.incidentId)
        assertNull(state.pauseUntil)
    }

    @Test
    fun unErrorSePuedeReintentar() {
        server.disconnect("GET /responsible-gaming/risk-alerts")
        val vm = loaded()
        assertNotNull(vm.state.value.error)
        server.replyBody("GET /responsible-gaming/risk-alerts", """{"pause_until":null,"results":[]}""")
        vm.load()
        val state = vm.state.await { it.loaded }
        assertNull(state.error)
        assertNull(state.pauseOffer)
    }

    @Test
    fun guardaElLimiteYMuestraElRechazo() {
        server.reply("POST /responsible-gaming/daily-limit", "e7_limite_aumento", 400)
        val vm = loaded()
        vm.openLimitDialog()
        vm.saveLimit("9000")
        assertTrue(vm.state.await { it.limitError != null }.showLimitDialog)
        server.reply("POST /responsible-gaming/daily-limit", "07_set_limit")
        vm.saveLimit("5000.50")
        assertEquals("5000.50", vm.state.await { !it.showLimitDialog }.limit!!.limit)
    }

    @Test
    fun aceptaLaPausaDeLaAlerta() {
        server.replyBody("POST /responsible-gaming/pause", """{"detail":"No encontrado."}""", 404)
        val vm = loaded()
        vm.pause()
        assertEquals("No encontrado.", vm.state.await { it.pauseError != null }.pauseError)
        server.replyBody("POST /responsible-gaming/pause", """{"pause_until":"2026-11-25T15:10:00+00:00"}""")
        server.replyBody(
            "GET /responsible-gaming/risk-alerts",
            """{"pause_until":"2026-11-25T15:10:00+00:00","results":[{"incident_id":12,
               "created_at":"2026-11-25T14:50:00+00:00","pause_started_at":"2026-11-25T15:00:00+00:00",
               "suggested_pause_seconds":600}]}""",
        )
        vm.pause()
        val paused = vm.state.await { it.pauseUntil != null && !it.loading && !it.pausing }
        assertNull(paused.pauseOffer)
        assertNull(paused.pauseError)
        assertEquals("""{"incident_id":12}""", server.bodies("POST /responsible-gaming/pause").last())
        // Al pasar la hora, la pausa deja de mostrarse.
        now += 11 * 60 * 1000
        vm.load()
        assertNull(vm.state.await { it.pauseUntil == null && !it.loading }.pauseOffer)
    }

    @Test
    fun laAutoexclusionPideConfirmarYCierraLaSesionConElMotivo() {
        server.reply("POST /responsible-gaming/self-exclude", "15c_self_exclude")
        val vm = loaded()
        vm.confirmExclusion()
        assertEquals(0, server.count("POST /responsible-gaming/self-exclude"))
        vm.askExclusion(30)
        vm.cancelExclusion()
        assertNull(vm.state.value.exclusionDays)
        vm.askExclusion(30)
        vm.confirmExclusion()
        session.session.await { it is Session.LoggedOut }
        assertEquals(listOf("""{"days":30}"""), server.bodies("POST /responsible-gaming/self-exclude"))
        assertEquals(
            "Autoexclusión confirmada: no podrás entrar hasta el 25/12/2026 10:00.",
            session.notice.await { it != null },
        )
    }

    @Test
    fun siFallaLaAutoexclusionLaSesionSigue() {
        server.disconnect("POST /responsible-gaming/self-exclude")
        val vm = loaded()
        vm.askExclusion(7)
        vm.confirmExclusion()
        val state = vm.state.await { it.exclusionError != null && !it.excluding }
        assertEquals(7, state.exclusionDays)
        assertTrue(session.session.value is Session.LoggedIn)
    }

    @Test
    fun formatos() {
        assertEquals("45 s", duration(45))
        assertEquals("10 min", duration(600))
        assertEquals("1 h 15 min", duration(4530))
        assertEquals("+1,250.00", signedCredits("1250.00"))
        assertEquals("-20.00", signedCredits("-20.00"))
        assertEquals("0.00", signedCredits("0.00"))
        assertEquals(0.25f, fraction("25.00", "100.00"))
        assertEquals(1f, fraction("25.00", "10.00"))
        assertEquals(1f, fraction("0.00", "0.00"))
    }
}
