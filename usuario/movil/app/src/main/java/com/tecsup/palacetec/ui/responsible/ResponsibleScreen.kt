package com.tecsup.palacetec.ui.responsible

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.palacetec.data.remote.DailyLimit
import com.tecsup.palacetec.data.remote.SessionSummary
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.components.DailyLimitDialog
import com.tecsup.palacetec.ui.components.ErrorBox
import com.tecsup.palacetec.ui.components.LoadingBox
import com.tecsup.palacetec.ui.components.PrimaryButton
import com.tecsup.palacetec.ui.components.credits
import com.tecsup.palacetec.ui.components.duration
import com.tecsup.palacetec.ui.components.fraction
import com.tecsup.palacetec.ui.components.limaDateTime
import com.tecsup.palacetec.ui.components.signedCredits
import com.tecsup.palacetec.ui.theme.RojoPerdida
import com.tecsup.palacetec.ui.theme.VerdeGanancia

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponsibleScreen(
    modifier: Modifier = Modifier,
    viewModel: ResponsibleViewModel = appViewModel {
        ResponsibleViewModel(it.dailyLimitRepository, it.responsibleRepository, it.authRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) {
        viewModel.load()
        onStopOrDispose { }
    }

    if (state.loading && !state.loaded) {
        LoadingBox(modifier)
        return
    }
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = viewModel::load,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { ErrorBox(it, viewModel::load) }
            state.summary?.let { SessionCard(it) }
            state.limit?.let { LimitCard(it, viewModel::openLimitDialog) }
            if (state.loaded) {
                PauseCard(state, viewModel::pause)
                ExclusionCard(viewModel::askExclusion)
            }
        }
    }
    if (state.showLimitDialog) {
        DailyLimitDialog(
            saving = state.savingLimit,
            error = state.limitError,
            onSave = viewModel::saveLimit,
            onDismiss = viewModel::closeLimitDialog,
        )
    }
    state.exclusionDays?.let { days ->
        ExclusionDialog(
            days, state.excluding, state.exclusionError, viewModel::confirmExclusion, viewModel::cancelExclusion,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun Line(label: String, value: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SessionCard(summary: SessionSummary) {
    SectionCard("Tu sesión actual") {
        Line("Tiempo conectado", duration(summary.durationSeconds))
        Line("Apostado", credits(summary.wagered))
        Line("Premios", credits(summary.paid))
        val loss = summary.net.startsWith("-")
        Line("Resultado", signedCredits(summary.net), if (loss) RojoPerdida else VerdeGanancia)
        if (summary.pendingCount > 0) {
            Line("Por resolver (${summary.pendingCount})", credits(summary.pendingWagered))
        }
        Note("Empezó el ${limaDateTime(summary.startedAt)}. Cuenta las apuestas ya resueltas.")
    }
}

@Composable
private fun LimitCard(limit: DailyLimit, onChange: () -> Unit) {
    SectionCard("Límite diario") {
        if (limit.configured) {
            Line("Límite", credits(limit.limit))
            Line("Usado hoy", credits(limit.spent))
            Line("Te quedan", credits(limit.remaining))
            LinearProgressIndicator(
                progress = { fraction(limit.spent, limit.limit) }, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Define tu límite diario para poder apostar.")
        }
        limit.pendingLimit?.let { pending ->
            val since = limit.effectiveAt?.let { " Se aplica el ${limaDateTime(it)}." }.orEmpty()
            Note("Aumento a ${credits(pending)} pendiente.$since")
        }
        OutlinedButton(onClick = onChange) { Text(if (limit.configured) "Cambiar límite" else "Definir límite") }
    }
}

@Composable
private fun PauseCard(state: ResponsibleUiState, onPause: () -> Unit) {
    SectionCard("Pausa") {
        val offer = state.pauseOffer
        if (state.pauseUntil != null) {
            Text(
                "Estás en pausa hasta el ${limaDateTime(state.pauseUntil)}. Mientras tanto no puedes apostar.",
                color = MaterialTheme.colorScheme.secondary,
            )
        } else if (offer != null) {
            Text(
                "El ${limaDateTime(offer.createdAt)} notamos un ritmo de juego intenso. " +
                    "Te sugerimos una pausa de ${duration(offer.suggestedPauseSeconds)}.",
            )
            state.pauseError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Tomar la pausa", onPause, loading = state.pausing)
        } else {
            Text("No hay pausas sugeridas por ahora.")
            Note("Cuando detectemos un ritmo de juego intenso podrás tomar aquí una pausa de 10 minutos.")
        }
    }
}

@Composable
private fun ExclusionCard(onSelect: (Int) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Autoexclusión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            Text("Bloquea tu cuenta por un plazo. Se cierran todas tus sesiones y no se puede cancelar antes.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResponsibleViewModel.EXCLUSION_DAYS.forEach { days ->
                    OutlinedButton(onClick = { onSelect(days) }, modifier = Modifier.weight(1f)) {
                        Text("$days días")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExclusionDialog(
    days: Int,
    excluding: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Autoexcluirte por $days días?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Se cerrará tu sesión ahora mismo y no podrás entrar a PalaceTec durante $days días. " +
                        "No se puede cancelar.",
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !excluding) {
                Text(if (excluding) "Confirmando…" else "Sí, excluirme", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !excluding) { Text("Cancelar") }
        },
    )
}
