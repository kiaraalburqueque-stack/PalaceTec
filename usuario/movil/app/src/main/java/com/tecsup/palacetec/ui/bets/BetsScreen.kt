package com.tecsup.palacetec.ui.bets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.palacetec.R
import com.tecsup.palacetec.data.remote.HistoryItem
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.components.ErrorBox
import com.tecsup.palacetec.ui.components.LoadingBox
import com.tecsup.palacetec.ui.components.betLabel
import com.tecsup.palacetec.ui.components.credits
import com.tecsup.palacetec.ui.components.limaDateTime
import com.tecsup.palacetec.ui.theme.Dorado
import com.tecsup.palacetec.ui.theme.RojoPerdida
import com.tecsup.palacetec.ui.theme.VerdeGanancia

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetsScreen(
    modifier: Modifier = Modifier,
    viewModel: BetsViewModel = appViewModel { BetsViewModel(it.historyRepository, it.analytics) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) {
        viewModel.onVisible()
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
                    val summary = when {
                        !state.fromCache -> "${state.total} apuestas en total"
                        state.loading -> "Actualizando…"
                        else -> "Sin conexión: estas son las últimas apuestas guardadas."
                    }
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.fromCache) Dorado else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (state.error == null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_history), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp),
                        )
                        Text("Todavía no tienes apuestas.", textAlign = TextAlign.Center)
                        Text(
                            "Cuando juegues en la ruleta, tus apuestas aparecerán aquí.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
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

/** Boleto de apuesta: franja de color según el estado y los montos a la derecha. */
@Composable
private fun BetCard(item: HistoryItem) {
    val (label, color) = statusStyle(item.status)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.game, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        modifier = Modifier
                            .background(color.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                Text(betLabel(item.kind, item.selection), color = MaterialTheme.colorScheme.onSurface)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Amount("Apostado", credits(item.amount), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    if (item.payout != null) Amount("Premio", credits(item.payout), color, horizontal = Alignment.End)
                }
                Text(
                    limaDateTime(item.createdAt) + (item.roundId?.let { " · Ronda $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Amount(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    horizontal: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier, horizontalAlignment = horizontal) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun statusStyle(status: String): Pair<String, Color> = when (status) {
    "WON" -> "Ganada" to VerdeGanancia
    "LOST" -> "Perdida" to RojoPerdida
    "PUSH" -> "Empate" to MaterialTheme.colorScheme.onSurfaceVariant
    else -> "Pendiente" to Dorado
}
