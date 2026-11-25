package com.tecsup.palacetec.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.components.BalanceCard
import com.tecsup.palacetec.ui.components.ErrorBox
import com.tecsup.palacetec.ui.components.LoadingBox
import com.tecsup.palacetec.ui.components.credits
import com.tecsup.palacetec.ui.theme.VerdeGanancia

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    modifier: Modifier = Modifier,
    viewModel: WalletViewModel = appViewModel { WalletViewModel(it.walletRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) {
        viewModel.refresh()
        onStopOrDispose { }
    }

    if (state.loading && state.wallet == null) {
        LoadingBox(modifier)
        return
    }
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { ErrorBox(it, viewModel::refresh) }
            state.wallet?.let { wallet ->
                BalanceCard(wallet.balance, wallet.isLowBalance, label = "Saldo total")
                AmountCard("Bono", "Créditos de bienvenida", wallet.bonusBalance, MaterialTheme.colorScheme.secondary)
                AmountCard("Ganancias", "Premios obtenidos en el juego", wallet.winningsBalance, VerdeGanancia)
                Text(
                    "Los créditos son virtuales: no se compran, no se retiran y no equivalen a dinero real.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AmountCard(title: String, subtitle: String, amount: String, color: Color) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                credits(amount), color = color, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
