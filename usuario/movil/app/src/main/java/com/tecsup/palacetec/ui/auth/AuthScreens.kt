package com.tecsup.palacetec.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.palacetec.R
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.components.FormField
import com.tecsup.palacetec.ui.components.PasswordField
import com.tecsup.palacetec.ui.components.PrimaryButton

@Composable
fun LoginScreen(
    onRegister: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = appViewModel { LoginViewModel(it.authRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    AuthLayout("Inicia sesión para jugar", modifier) {
        if (state.error == null) notice?.let { ErrorText(it) }
        FormField(
            state.email, viewModel::onEmail, "Correo electrónico", state.emailError,
            enabled = !state.loading, keyboardType = KeyboardType.Email,
        )
        PasswordField(
            state.password, viewModel::onPassword, state.passwordError,
            enabled = !state.loading, onDone = viewModel::submit,
        )
        state.error?.let { ErrorText(it) }
        PrimaryButton("Ingresar", viewModel::submit, loading = state.loading)
        TextButton(onClick = onRegister, enabled = !state.loading) { Text("¿No tienes cuenta? Regístrate") }
    }
}

@Composable
fun RegisterScreen(
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = appViewModel { RegisterViewModel(it.authRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthLayout("Crea tu cuenta de jugador", modifier) {
        if (state.registered) {
            Text("Tu cuenta fue creada. Ya puedes iniciar sesión.", textAlign = TextAlign.Center)
            PrimaryButton("Ir a iniciar sesión", onBackToLogin)
        } else {
            FormField(
                state.email, viewModel::onEmail, "Correo electrónico", state.fieldErrors["email"],
                enabled = !state.loading, keyboardType = KeyboardType.Email,
            )
            FormField(
                state.dni, viewModel::onDni, "DNI", state.fieldErrors["dni"],
                enabled = !state.loading, keyboardType = KeyboardType.Number,
            )
            FormField(
                state.birthDigits, viewModel::onBirthDate, "Fecha de nacimiento (DD/MM/AAAA)",
                state.fieldErrors["birth_date"],
                enabled = !state.loading, keyboardType = KeyboardType.Number,
                visualTransformation = DateTransformation,
            )
            PasswordField(
                state.password, viewModel::onPassword, state.fieldErrors["password"],
                enabled = !state.loading, onDone = viewModel::submit,
            )
            Text(
                "Mínimo 8 caracteres, con una mayúscula y un símbolo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let { ErrorText(it) }
            PrimaryButton("Crear cuenta", viewModel::submit, loading = state.loading)
            TextButton(onClick = onBackToLogin, enabled = !state.loading) { Text("Ya tengo una cuenta") }
        }
    }
}

@Composable
private fun AuthLayout(subtitle: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(
            painterResource(R.drawable.ic_roulette), contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "PalaceTec", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Solo mayores de 18 años. Créditos virtuales, sin dinero real.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Muestra 31122000 como 31/12/2000 sin guardar las barras. */
private object DateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = buildString {
            digits.forEachIndexed { index, digit ->
                if (index == 2 || index == 4) append('/')
                append(digit)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                else -> offset + 2
            }.coerceAtMost(shown.length)

            override fun transformedToOriginal(offset: Int) = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }.coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
