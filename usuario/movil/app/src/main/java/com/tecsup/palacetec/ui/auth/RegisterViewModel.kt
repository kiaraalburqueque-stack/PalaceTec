package com.tecsup.palacetec.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.palacetec.data.remote.ApiException
import com.tecsup.palacetec.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val dni: String = "",
    val birthDigits: String = "",
    /** Claves iguales a las del backend: email, password, dni y birth_date. */
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val loading: Boolean = false,
    val registered: Boolean = false,
)

class RegisterViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onEmail(value: String) = edit("email") { it.copy(email = value) }

    fun onPassword(value: String) = edit("password") { it.copy(password = value) }

    fun onDni(value: String) = edit("dni") { it.copy(dni = value.digits()) }

    fun onBirthDate(value: String) = edit("birth_date") { it.copy(birthDigits = value.digits()) }

    private fun edit(field: String, change: (RegisterUiState) -> RegisterUiState) =
        _state.update { change(it).copy(fieldErrors = it.fieldErrors - field, error = null) }

    private fun String.digits() = filter { it in '0'..'9' }.take(8)

    fun submit() {
        val form = _state.value
        if (form.loading) return
        val errors = listOfNotNull(
            AuthValidators.email(form.email)?.let { "email" to it },
            AuthValidators.newPassword(form.password)?.let { "password" to it },
            AuthValidators.dni(form.dni)?.let { "dni" to it },
            AuthValidators.birthDate(form.birthDigits)?.let { "birth_date" to it },
        ).toMap()
        val birthDate = AuthValidators.isoDate(form.birthDigits)
        if (errors.isNotEmpty() || birthDate == null) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val email = form.email.trim()
            try {
                auth.register(email, form.password, form.dni, birthDate)
            } catch (error: ApiException) {
                _state.update {
                    it.copy(
                        loading = false,
                        fieldErrors = error.fields,
                        // Un campo que no está en el formulario se muestra como error general.
                        error = error.message.takeIf { error.fields.keys.none { name -> name in FIELDS } },
                    )
                }
                return@launch
            }
            try {
                auth.login(email, form.password)
            } catch (_: ApiException) {
                // La cuenta ya existe: que inicie sesión a mano.
                _state.update { it.copy(loading = false, registered = true) }
            }
        }
    }

    private companion object {
        val FIELDS = setOf("email", "password", "dni", "birth_date")
    }
}
