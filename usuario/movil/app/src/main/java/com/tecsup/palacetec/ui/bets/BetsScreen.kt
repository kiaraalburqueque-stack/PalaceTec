package com.tecsup.palacetec.ui.bets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.palacetec.data.remote.HistoryItem
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.components.ErrorBox
import com.tecsup.palacetec.ui.components.LoadingBox
import com.tecsup.palacetec.ui.components.betLabel
import com.tecsup.palacetec.ui.components.credits
import com.tecsup.palacetec.ui.components.limaDateTime
import com.tecsup.palacetec.ui.theme.RojoPerdida
import com.tecsup.palacetec.ui.theme.VerdeGanancia

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetsScreen(
    modifier: Modifier = Modifier,
    viewModel: BetsViewModel = appViewModel { BetsViewModel(it.historyRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) {
        viewModel.refresh()
        onStopOrDispose { }
    }

    if (state.loading && state.items.isEmpty()) {
        LoadingBox(modifier)
        return
    }
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.items.isNotEmpty()) {
                item {
                    Text("${state.total} apuestas en total", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (state.error == null) {
                item {
                    Text(
                        "Todavía no tienes apuestas.", textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }
            }
            items(state.items, key = { "${it.source}-${it.id}" }) { BetCard(it) }
            if (state.hasMore) {
                item {
                    // Al llegar al final se pide la página siguiente.
                    LaunchedEffect(state.items.size) { viewModel.loadMore() }
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            state.error?.let { message -> item { ErrorBox(message, viewModel::refresh) } }
        }
    }
}

@Composable
private fun BetCard(item: HistoryItem) {
    val (label, color) = statusStyle(item.status)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(item.game, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(label, color = color, fontWeight = FontWeight.Bold)
            }
            Text(betLabel(item.kind, item.selection))
            Row(Modifier.fillMaxWidth()) {
                Text("Apostado: ${credits(item.amount)}", Modifier.weight(1f))
                if (item.payout != null) Text("Premio: ${credits(item.payout)}", color = color)
            }
            Text(
                limaDateTime(item.createdAt) + (item.roundId?.let { " · Ronda $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusStyle(status: String): Pair<String, Color> = when (status) {
    "WON" -> "Ganada" to VerdeGanancia
    "LOST" -> "Perdida" to RojoPerdida
    "PUSH" -> "Empate" to MaterialTheme.colorScheme.primary
    else -> "Pendiente" to MaterialTheme.colorScheme.secondary
}
