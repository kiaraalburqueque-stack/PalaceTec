package com.tecsup.palacetec.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.palacetec.R
import com.tecsup.palacetec.data.remote.DailyLimit
import com.tecsup.palacetec.data.remote.Game
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.components.BalanceCard
import com.tecsup.palacetec.ui.components.DailyLimitDialog
import com.tecsup.palacetec.ui.components.ErrorBox
import com.tecsup.palacetec.ui.components.LoadingBox
import com.tecsup.palacetec.ui.components.credits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlayRoulette: () -> Unit,
    onResponsible: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = appViewModel {
        HomeViewModel(it.walletRepository, it.catalogRepository, it.dailyLimitRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) {
        viewModel.load()
        onStopOrDispose { }
    }

    if (state.loading && state.wallet == null) {
        LoadingBox(modifier)
        return
    }
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = viewModel::load,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { message -> item { ErrorBox(message, viewModel::load) } }
            state.wallet?.let { wallet -> item { BalanceCard(wallet.balance, wallet.isLowBalance) } }
            item { ResponsibleGamingCard(state.dailyLimit, viewModel::openLimitDialog, onResponsible) }
            item { Text("Catálogo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.games.isEmpty() && state.error == null) {
                item { Text("No hay juegos disponibles por ahora.") }
            }
            items(state.games, key = { it.id }) { game -> GameCard(game, onPlayRoulette) }
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
}

@Composable
private fun ResponsibleGamingCard(limit: DailyLimit?, onChange: () -> Unit, onMore: () -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Juego responsable", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            Text(
                "Solo para mayores de 18 años. Juega por diversión, pon límites y haz pausas. " +
                    "Los créditos son virtuales y no tienen valor en dinero real.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (limit != null) {
                Text(
                    if (limit.configured) {
                        "Límite diario: ${credits(limit.limit)} · Te quedan ${credits(limit.remaining)} hoy"
                    } else {
                        "Define tu límite diario para poder apostar."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                limit.pendingLimit?.let {
                    Text(
                        "Aumento a ${credits(it)} pendiente: se aplica a las 24 horas de pedirlo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onChange, contentPadding = PaddingValues(0.dp)) {
                    Text(if (limit.configured) "Cambiar límite" else "Definir límite")
                }
            }
            TextButton(onClick = onMore, contentPadding = PaddingValues(0.dp)) {
                Text("Ver pausa, autoexclusión y sesión")
            }
        }
    }
}

@Composable
private fun GameCard(game: Game, onPlayRoulette: () -> Unit) {
    // Solo la ruleta se juega desde el móvil.
    val playable = game.roomsUrl != null
    Card(onClick = onPlayRoulette, enabled = playable, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (playable) R.drawable.ic_roulette else R.drawable.ic_cards),
                contentDescription = null,
                tint = if (playable) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Apuesta de ${credits(game.minBet)} a ${credits(game.maxBet)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                game.rtp?.let { Text("RTP $it %", style = MaterialTheme.typography.bodySmall) }
            }
            Text(
                if (playable) "Jugar" else "Solo en la web",
                style = MaterialTheme.typography.labelLarge,
                color = if (playable) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
