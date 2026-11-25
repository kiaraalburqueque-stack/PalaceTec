package com.tecsup.palacetec

import com.tecsup.palacetec.data.local.AvatarFile
import com.tecsup.palacetec.data.local.AvatarSource
import com.tecsup.palacetec.data.remote.ApiException
import com.tecsup.palacetec.data.repository.ProfileRepository
import com.tecsup.palacetec.ui.profile.ProfileViewModel
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
class ProfileViewModelTest {

    private lateinit var server: TestServer
    private var image: () -> AvatarFile = { AvatarFile("image/png", "contenido".toByteArray()) }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default.limitedParallelism(1))
        server = TestServer()
        server.reply("GET /users/me", "14_profile")
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private fun loaded(): ProfileViewModel {
        val avatars = object : AvatarSource {
            override suspend fun read(uri: String) = image()
        }
        return ProfileViewModel(ProfileRepository(server.api, avatars, "http://localhost:8080/")).apply {
            load()
            state.await { !it.loading }
        }
    }

    @Test
    fun cargaElPerfilYSeRecuperaDeUnError() {
        server.disconnect("GET /users/me")
        val vm = loaded()
        assertNotNull(vm.state.value.loadError)
        server.reply("GET /users/me", "14_profile")
        vm.load()
        val state = vm.state.await { it.profile != null && !it.loading }
        assertNull(state.loadError)
        assertNull(state.photoUrl)
        assertNull(state.profile!!.nickname)
    }

    @Test
    fun guardaSoloLoQueCambio() {
        server.reply("PUT /users/me", "14b_profile_editado")
        val vm = loaded()
        vm.startEdit()
        vm.onNickname(" Sol_21 ")
        vm.onFirstName("María José")
        vm.onAvatar("/avatars/avatar-3.svg")
        vm.save()
        val state = vm.state.await { it.form == null && !it.saving }
        assertEquals("Perfil actualizado.", state.message)
        assertEquals("sol_21", state.profile!!.nickname)
        assertEquals(
            listOf("""{"nickname":"sol_21","avatar_url":"/avatars/avatar-3.svg","first_name":"María José"}"""),
            server.bodies("PUT /users/me"),
        )
        // Sin cambios no se llama al servidor; un dato vaciado sí se envía.
        vm.startEdit()
        vm.save()
        assertNull(vm.state.value.form)
        assertEquals(1, server.count("PUT /users/me"))
        vm.startEdit()
        vm.onPhone("")
        vm.save()
        vm.state.await { it.form == null && !it.saving }
        assertEquals("""{"phone":""}""", server.bodies("PUT /users/me").last())
    }

    @Test
    fun losDatosInvalidosNoSeEnvian() {
        server.reply("GET /users/me", "14b_profile_editado")
        val vm = loaded()
        vm.startEdit()
        vm.onNickname("ñandú")
        vm.onFirstName("Ana3")
        vm.onLastName("a".repeat(81))
        vm.onPhone("123")
        vm.save()
        assertEquals(setOf("nickname", "first_name", "last_name", "phone"), vm.state.value.fieldErrors.keys)
        vm.onNickname("")
        vm.save()
        assertEquals("El apodo se puede cambiar, pero no quitar.", vm.state.value.fieldErrors["nickname"])
        vm.cancelEdit()
        assertNull(vm.state.value.form)
        assertTrue(vm.state.value.fieldErrors.isEmpty())
        assertEquals(0, server.count("PUT /users/me"))
    }

    @Test
    fun unApodoOcupadoSeMuestraEnSuCampoYOtroErrorComoGeneral() {
        server.replyBody("PUT /users/me", """{"nickname":["Este apodo ya esta registrado."]}""", 400)
        val vm = loaded()
        vm.startEdit()
        vm.onNickname("ocupado")
        vm.save()
        var state = vm.state.await { !it.saving && it.fieldErrors.isNotEmpty() }
        assertEquals("Este apodo ya esta registrado.", state.fieldErrors["nickname"])
        assertNull(state.message)
        assertEquals("ocupado", state.form!!.nickname)
        server.replyBody("PUT /users/me", """{"detail":"Fallo."}""", 500)
        vm.save()
        state = vm.state.await { !it.saving && it.message != null }
        assertEquals("Fallo.", state.message)
        assertTrue(state.messageIsError)
    }

    @Test
    fun mientrasSeEditaNoSeRecarga() {
        val vm = loaded()
        vm.startEdit()
        vm.onNickname("borrador")
        vm.load()
        assertEquals(1, server.count("GET /users/me"))
        assertEquals("borrador", vm.state.value.form!!.nickname)
    }

    @Test
    fun subeLaFotoComoMultipartYArmaSuDireccion() {
        server.reply("POST /users/avatar", "14c_profile_foto")
        val vm = loaded()
        vm.uploadPhoto("content://foto")
        val state = vm.state.await { !it.uploading && it.photoUrl != null }
        assertEquals("http://localhost:8080/media/avatars/user_7_0a1b2c3d4e5f.webp", state.photoUrl)
        assertEquals("Foto actualizada.", state.message)
        val body = server.bodies("POST /users/avatar").single()
        assertTrue(body.contains("""name="avatar"; filename="avatar.png""""))
        assertTrue(body.contains("contenido"))
    }

    @Test
    fun losErroresDeLaFotoSeMuestran() {
        server.replyBody("POST /users/avatar", """{"avatar":["La foto debe ser JPG, PNG o WEBP."]}""", 400)
        val vm = loaded()
        vm.uploadPhoto("content://foto")
        assertEquals("La foto debe ser JPG, PNG o WEBP.", vm.state.await { it.message != null }.message)
        image = { throw ApiException(null, null, "No se pudo leer la imagen. Elige otra.") }
        vm.uploadPhoto("content://otra")
        vm.state.await { it.message == "No se pudo leer la imagen. Elige otra." && !it.uploading }
        assertEquals(1, server.count("POST /users/avatar"))
    }
}
