package com.tecsup.palacetec.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.palacetec.data.remote.ApiException
import com.tecsup.palacetec.data.remote.Profile
import com.tecsup.palacetec.data.remote.ProfileUpdate
import com.tecsup.palacetec.data.repository.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileForm(
    val nickname: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val avatarUrl: String,
)

data class ProfileUiState(
    val loading: Boolean = true,
    val profile: Profile? = null,
    /** Dirección de la foto subida, o null si usa un avatar predeterminado. */
    val photoUrl: String? = null,
    val loadError: String? = null,
    /** Distinto de null mientras se edita. */
    val form: ProfileForm? = null,
    /** Claves iguales a las del backend. */
    val fieldErrors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val uploading: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    val busy: Boolean get() = saving || uploading
}

class ProfileViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** Mientras se edita o se sube una foto no se recarga, para no pisar lo que hizo el jugador. */
    fun load() {
        val current = _state.value
        if (loadJob?.isActive == true || current.form != null || current.busy) return
        _state.update { it.copy(loading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                show(repository.get())
                _state.update { it.copy(loading = false) }
            } catch (error: ApiException) {
                _state.update { it.copy(loading = false, loadError = error.message) }
            }
        }
    }

    private fun show(profile: Profile) =
        _state.update { it.copy(profile = profile, photoUrl = repository.photoUrl(profile.avatarUrl)) }

    private fun stopLoading() {
        loadJob?.cancel()
        _state.update { it.copy(loading = false) }
    }

    fun startEdit() {
        val profile = _state.value.profile ?: return
        if (_state.value.busy) return
        stopLoading()
        _state.update {
            it.copy(
                form = ProfileForm(
                    profile.nickname.orEmpty(), profile.firstName, profile.lastName, profile.phone, profile.avatarUrl,
                ),
                fieldErrors = emptyMap(), message = null,
            )
        }
    }

    fun cancelEdit() {
        if (!_state.value.saving) _state.update { it.copy(form = null, fieldErrors = emptyMap(), message = null) }
    }

    fun onNickname(value: String) = edit("nickname") { it.copy(nickname = value) }

    fun onFirstName(value: String) = edit("first_name") { it.copy(firstName = value) }

    fun onLastName(value: String) = edit("last_name") { it.copy(lastName = value) }

    fun onPhone(value: String) = edit("phone") { it.copy(phone = value) }

    fun onAvatar(avatarUrl: String) = edit("avatar_url") { it.copy(avatarUrl = avatarUrl) }

    private fun edit(field: String, change: (ProfileForm) -> ProfileForm) = _state.update {
        it.copy(form = it.form?.let(change), fieldErrors = it.fieldErrors - field, message = null)
    }

    fun save() {
        val profile = _state.value.profile ?: return
        val form = _state.value.form ?: return
        if (_state.value.busy) return
        val errors = ProfileValidators.check(form, profile)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        val changes = changes(form, profile)
        if (changes == ProfileUpdate()) {
            cancelEdit()
            return
        }
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                show(repository.update(changes))
                _state.update { it.copy(saving = false, form = null, message = "Perfil actualizado.", messageIsError = false) }
            } catch (error: ApiException) {
                _state.update {
                    it.copy(
                        saving = false,
                        fieldErrors = error.fields,
                        // Un campo que no está en el formulario se muestra como error general.
                        message = error.message.takeIf { error.fields.keys.none { name -> name in FIELDS } },
                        messageIsError = true,
                    )
                }
            }
        }
    }

    /** Solo lo que cambió: lo demás no se envía. */
    private fun changes(form: ProfileForm, profile: Profile) = ProfileUpdate(
        // El backend guarda el apodo en minúsculas; vacío significa que nunca tuvo.
        nickname = form.nickname.trim().lowercase().takeIf { it.isNotEmpty() && it != profile.nickname },
        avatarUrl = form.avatarUrl.takeIf { it != profile.avatarUrl },
        firstName = form.firstName.trim().takeIf { it != profile.firstName },
        lastName = form.lastName.trim().takeIf { it != profile.lastName },
        phone = form.phone.trim().takeIf { it != profile.phone },
    )

    /** La foto se guarda al elegirla, sin pasar por el formulario. */
    fun uploadPhoto(uri: String) {
        if (_state.value.profile == null || _state.value.busy) return
        stopLoading()
        _state.update { it.copy(uploading = true, message = null) }
        viewModelScope.launch {
            try {
                val profile = repository.uploadAvatar(uri)
                show(profile)
                _state.update {
                    it.copy(
                        uploading = false, form = it.form?.copy(avatarUrl = profile.avatarUrl),
                        message = "Foto actualizada.", messageIsError = false,
                    )
                }
            } catch (error: ApiException) {
                _state.update { it.copy(uploading = false, message = error.message, messageIsError = true) }
            }
        }
    }

    private companion object {
        val FIELDS = setOf("nickname", "first_name", "last_name", "phone", "avatar_url")
    }
}

/** Las mismas reglas de ProfileChanges en el backend, que responde parte de los errores en inglés. */
object ProfileValidators {

    private val NICKNAME = Regex("[a-zA-Z0-9_]{3,30}")
    private val PHONE = Regex("\\+?[0-9 ()-]{7,20}")
    private const val MAX_NAME = 80
    private const val MAX_PHONE = 20

    fun check(form: ProfileForm, profile: Profile): Map<String, String> = listOfNotNull(
        nickname(form.nickname.trim(), hadOne = profile.nickname != null)?.let { "nickname" to it },
        name(form.firstName.trim())?.let { "first_name" to it },
        name(form.lastName.trim())?.let { "last_name" to it },
        phone(form.phone.trim())?.let { "phone" to it },
    ).toMap()

    private fun nickname(value: String, hadOne: Boolean): String? = when {
        value.isEmpty() -> if (hadOne) "El apodo se puede cambiar, pero no quitar." else null
        !NICKNAME.matches(value) -> "Usa de 3 a 30 letras sin tilde, números o guion bajo."
        else -> null
    }

    private fun name(value: String): String? = when {
        value.length > MAX_NAME -> "Usa como máximo $MAX_NAME caracteres."
        value.any { !it.isLetter() && it !in " .'-" } -> "Usa solo letras, espacios, puntos, apóstrofos y guiones."
        else -> null
    }

    private fun phone(value: String): String? = when {
        value.isEmpty() -> null
        value.length > MAX_PHONE || !PHONE.matches(value) -> "Escribe un teléfono de 7 a 20 dígitos."
        else -> null
    }
}
