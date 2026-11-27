package com.tecsup.palacetec.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.palacetec.BuildConfig
import com.tecsup.palacetec.ui.appViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = appViewModel { SettingsViewModel(it.pushRepository, BuildConfig.DEBUG) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) {
        viewModel.onVisible()
        onStopOrDispose { }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NotificationsCard(state.notificationsEnabled)
        if (state.showDebugTools) DebugCard(state, viewModel::forceCrash)
    }
}

@Composable
private fun NotificationsCard(enabled: Boolean) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Notificaciones", fontWeight = FontWeight.Bold)
            Text(
                if (enabled) "Están activadas en este celular."
                else "Están desactivadas: no recibirás avisos en este celular.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { context.openNotificationSettings() }) {
                Text(if (enabled) "Cambiar en el sistema" else "Activar en el sistema")
            }
        }
    }
}

@Composable
private fun DebugCard(state: SettingsUiState, onCrash: () -> Unit) {
    val context = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pruebas (solo debug)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Text("Token de FCM para el mensaje de prueba de la consola:")
            SelectionContainer {
                Text(
                    state.token ?: if (state.loadingToken) "Obteniendo…" else "No disponible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.token?.let { token ->
                OutlinedButton(onClick = { context.shareText(token) }) { Text("Compartir token") }
            }
            Button(
                onClick = onCrash,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Forzar crash de prueba") }
        }
    }
}

/** Muestra el permiso del sistema cuando el ViewModel lo pide. */
@SuppressLint("InlinedApi") // El ViewModel solo lo pide desde Android 13.
@Composable
fun PushPermissionRequest(
    viewModel: PushPermissionViewModel = appViewModel { PushPermissionViewModel(it.pushRepository) },
) {
    val ask by viewModel.ask.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(ask) {
        if (ask) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            viewModel.onAsked()
        }
    }
}

private fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }
    startActivity(intent)
}

private fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, null))
}
