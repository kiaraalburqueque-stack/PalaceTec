package com.tecsup.palacetec

import com.tecsup.palacetec.data.repository.NotificationRepository
import com.tecsup.palacetec.ui.notifications.NotificationsViewModel
import com.tecsup.palacetec.ui.notifications.UnreadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private lateinit var server: TestServer
    private lateinit var repository: NotificationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default.limitedParallelism(1))
        server = TestServer()
        repository = NotificationRepository(server.api)
        server.reply("GET /notifications", "16_notifications")
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private fun loaded() = NotificationsViewModel(repository).apply {
        load()
        state.await { !it.loading }
    }

    @Test
    fun cargaLaListaYElContador() {
        val vm = loaded()
        assertEquals(listOf(31L, 30L, 29L), vm.state.value.items.map { it.id })
        assertEquals(listOf(false, false, true), vm.state.value.items.map { it.isRead })
        assertEquals(2L, vm.unread.value)
    }

    @Test
    fun unErrorSePuedeReintentar() {
        server.disconnect("GET /notifications")
        val vm = loaded()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.loaded)
        server.reply("GET /notifications", "16_notifications")
        vm.load()
        assertNull(vm.state.await { it.loaded }.error)
    }

    @Test
    fun marcarUnaActualizaElContadorDeLaCampana() {
        server.reply("POST /notifications/31/read", "16b_notification_read")
        val vm = loaded()
        val bell = UnreadViewModel(repository)
        vm.markRead(vm.state.value.items.first())
        assertTrue(vm.state.value.items.first().isRead)
        assertEquals(1L, bell.unread.await { it == 1L })
        // Una ya leída no se vuelve a enviar.
        vm.markRead(vm.state.value.items.first())
        assertEquals(1, server.requests.count { it.first.startsWith("POST ") })
    }

    @Test
    fun siFallaMarcarUnaVuelveAQuedarSinLeer() {
        server.disconnect("POST /notifications/31/read")
        val vm = loaded()
        vm.markRead(vm.state.value.items.first())
        val state = vm.state.await { it.actionError != null }
        assertFalse(state.items.first().isRead)
        assertEquals(2L, vm.unread.value)
    }

    @Test
    fun marcarTodasDejaElContadorEnCero() {
        server.replyBody("POST /notifications/read-all", """{"updated":2,"unread_count":0}""")
        val vm = loaded()
        vm.markAllRead()
        val state = vm.state.await { !it.markingAll }
        assertTrue(state.items.all { it.isRead })
        assertEquals(0L, vm.unread.value)
        assertEquals(1, server.count("POST /notifications/read-all"))
    }

    @Test
    fun siFallaMarcarTodasSoloVuelvenLasQueEstabanSinLeer() {
        server.replyBody("POST /notifications/read-all", """{"detail":"Fallo."}""", 500)
        val vm = loaded()
        vm.markAllRead()
        val state = vm.state.await { it.actionError != null && !it.markingAll }
        assertEquals("Fallo.", state.actionError)
        assertEquals(listOf(false, false, true), state.items.map { it.isRead })
        assertEquals(2L, vm.unread.value)
    }

    @Test
    fun laCampanaConservaSuValorSiFallaYSeReiniciaAlCerrarSesion() {
        val bell = UnreadViewModel(repository)
        bell.refresh()
        bell.unread.await { it == 2L }
        server.disconnect("GET /notifications")
        Thread.sleep(200)
        bell.refresh()
        Thread.sleep(500)
        assertTrue(server.count("GET /notifications") >= 2)
        assertEquals(2L, bell.unread.value)
        repository.reset()
        assertEquals(0L, bell.unread.value)
    }
}
