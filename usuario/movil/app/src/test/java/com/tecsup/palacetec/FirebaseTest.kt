package com.tecsup.palacetec

import com.tecsup.palacetec.data.local.PushStore
import com.tecsup.palacetec.data.local.Session
import com.tecsup.palacetec.data.local.SessionStore
import com.tecsup.palacetec.data.remote.BetKind
import com.tecsup.palacetec.data.remote.BetRequest
import com.tecsup.palacetec.data.repository.AuthRepository
import com.tecsup.palacetec.data.repository.HistoryRepository
import com.tecsup.palacetec.data.repository.PushPlatform
import com.tecsup.palacetec.data.repository.PushRepository
import com.tecsup.palacetec.data.repository.RouletteRepository
import com.tecsup.palacetec.push.PushContent
import com.tecsup.palacetec.push.pushContent
import com.tecsup.palacetec.ui.auth.LoginViewModel
import com.tecsup.palacetec.ui.auth.RegisterViewModel
import com.tecsup.palacetec.ui.bets.BetsViewModel
import com.tecsup.palacetec.ui.settings.PushPermissionViewModel
import com.tecsup.palacetec.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseTest {

    private lateinit var server: TestServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val analytics = FakeAnalytics()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default.limitedParallelism(1))
        server = TestServer()
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
        Dispatchers.resetMain()
    }

    private fun auth() =
        AuthRepository(server.api, SessionStore(MemoryDataStore(), scope), FakeHistoryDao(), analytics)

    // --- Analytics ---

    @Test
    fun soloUnLoginAceptadoRegistraElEvento() {
        val auth = auth()
        val vm = LoginViewModel(auth)
        vm.onEmail("a@example.com")
        vm.onPassword("x")
        server.reply("POST /auth/login", "e11_login_malo", 401)
        vm.submit()
        vm.state.await { it.error != null }
        assertTrue(analytics.events.isEmpty())
        server.reply("POST /auth/login", "02_login")
        vm.submit()
        auth.session.await { it is Session.LoggedIn }
        assertEquals(listOf("login"), analytics.events.toList())
    }

    @Test
    fun elRegistroRegistraSignUpUnaVez() {
        server.reply("POST /auth/register", "01_register", 201)
        server.reply("POST /auth/login", "02_login")
        val auth = auth()
        val vm = RegisterViewModel(auth)
        vm.onEmail("a@example.com")
        vm.onPassword("Clave-segura1")
        vm.onDni("00086267")
        vm.onBirthDate("15052000")
        vm.submit()
        auth.session.await { it is Session.LoggedIn }
        assertEquals(listOf("sign_up", "login"), analytics.events.toList())
    }

    @Test
    fun laApuestaAceptadaRegistraMontoYJuego() {
        val roulette = RouletteRepository(server.api, analytics)
        val request = BetRequest(1, BetKind.COLOR, "rojo", listOf(10), "referencia")
        server.reply("POST /rooms/1/bets", "e5_saldo_insuficiente", 400)
        runBlocking { runCatching { roulette.placeBet(1, request) } }
        assertTrue(analytics.events.isEmpty())
        server.reply("POST /rooms/1/bets", "09_bet_color", 201)
        runBlocking { roulette.placeBet(1, request) }
        assertEquals(listOf("place_bet 10.00 ruleta"), analytics.events.toList())
    }

    @Test
    fun verElHistorialRegistraUnEventoPorVisitaYNoPorRecarga() {
        server.reply("GET /users/bets-history", "12_history_resultado")
        val vm = BetsViewModel(HistoryRepository(server.api, FakeHistoryDao()) { "a@example.com" }, analytics)
        vm.onVisible()
        vm.state.await { !it.loading }
        vm.refresh()
        vm.state.await { !it.loading }
        assertEquals(listOf("view_history"), analytics.events.toList())
    }

    // --- Ajustes y Crashlytics ---

    private class FakePlatform(
        override val needsPermission: Boolean = true,
        var enabled: Boolean = false,
        private val fcmToken: String? = "token-fcm",
    ) : PushPlatform {
        override fun notificationsEnabled() = enabled
        override suspend fun token() = fcmToken
    }

    private fun push(platform: PushPlatform, store: PushStore = PushStore(MemoryDataStore())) =
        PushRepository(platform, store)

    @Test
    fun elCrashDePruebaSoloExisteEnDebug() {
        val release = SettingsViewModel(push(FakePlatform()), debug = false)
        release.onVisible()
        assertFalse(release.state.value.showDebugTools)
        release.forceCrash()
        assertNull(release.state.value.token)
        val debug = SettingsViewModel(push(FakePlatform()), debug = true)
        assertTrue(debug.state.value.showDebugTools)
        assertThrows(IllegalStateException::class.java) { debug.forceCrash() }
    }

    @Test
    fun ajustesMuestraElTokenYElPermisoActual() {
        val platform = FakePlatform()
        val vm = SettingsViewModel(push(platform), debug = true)
        vm.onVisible()
        assertEquals("token-fcm", vm.state.await { it.token != null }.token)
        assertFalse(vm.state.value.notificationsEnabled)
        platform.enabled = true
        vm.onVisible()
        assertTrue(vm.state.value.notificationsEnabled)
        val sinFirebase = SettingsViewModel(push(FakePlatform(fcmToken = null)), debug = true)
        sinFirebase.onVisible()
        assertNull(sinFirebase.state.await { !it.loadingToken }.token)
    }

    // --- Notificaciones ---

    @Test
    fun elPermisoSePideUnaSolaVezYSoloSiHaceFalta() {
        val store = PushStore(MemoryDataStore())
        val vm = PushPermissionViewModel(push(FakePlatform(), store))
        vm.ask.await { it }
        vm.onAsked()
        assertFalse(vm.ask.value)
        runBlocking {
            withTimeout(8_000) { while (!store.permissionAsked()) delay(10) }
            assertFalse(push(FakePlatform(), store).shouldAskPermission())
            assertFalse(push(FakePlatform(needsPermission = false)).shouldAskPermission())
            assertFalse(push(FakePlatform(enabled = true)).shouldAskPermission())
            assertTrue(push(FakePlatform()).shouldAskPermission())
        }
    }

    @Test
    fun elAvisoUsaLaNotificacionYSiNoLosDatos() {
        assertEquals(PushContent("Hola", "Texto"), pushContent("Hola", "Texto", emptyMap(), "PalaceTec"))
        assertEquals(PushContent("PalaceTec", "Texto"), pushContent(null, "Texto", emptyMap(), "PalaceTec"))
        assertEquals(
            PushContent("T", "B"), pushContent(null, " ", mapOf("title" to "T", "body" to "B"), "PalaceTec"),
        )
        assertNull(pushContent(null, null, mapOf("otro" to "x"), "PalaceTec"))
    }
}
