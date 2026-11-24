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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val error: String? = null,
    val loading: Boolean = false,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmail(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }

    fun onPassword(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun submit() {
        val form = _state.value
        if (form.loading) return
        val emailError = AuthValidators.email(form.email)
        val passwordError = AuthValidators.loginPassword(form.password)
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                // Al guardarse el token, la navegación pasa sola a Inicio.
                auth.login(form.email.trim(), form.password)
                _state.update { it.copy(loading = false, password = "") }
            } catch (error: ApiException) {
                val emailField = error.fields["email"]
                val passwordField = error.fields["password"]
                _state.update {
                    it.copy(
                        loading = false,
                        emailError = emailField,
                        passwordError = passwordField,
                        error = error.message.takeIf { emailField == null && passwordField == null },
                    )
                }
            }
        }
    }
}
