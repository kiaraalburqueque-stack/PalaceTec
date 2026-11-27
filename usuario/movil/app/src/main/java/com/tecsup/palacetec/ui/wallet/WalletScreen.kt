package com.tecsup.palacetec.ui.wallet

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.palacetec.R
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.components.BalanceCard
import com.tecsup.palacetec.ui.components.ErrorBox
import com.tecsup.palacetec.ui.components.LoadingBox
import com.tecsup.palacetec.ui.components.SectionHeader
import com.tecsup.palacetec.ui.components.credits
import com.tecsup.palacetec.ui.theme.Dorado
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
                BalanceCard(wallet.balance, wallet.isLowBalance, label = "Saldo total") {
                    Icon(
                        painterResource(R.drawable.ic_wallet), contentDescription = null,
                        tint = Dorado.copy(alpha = 0.8f), modifier = Modifier.size(48.dp),
                    )
                }
                SectionHeader("Detalle del saldo", Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmountCard(
                        "Bono", "Créditos de bienvenida", wallet.bonusBalance, Dorado, R.drawable.ic_gift,
                        Modifier.weight(1f),
                    )
                    AmountCard(
                        "Ganancias", "Premios obtenidos en el juego", wallet.winningsBalance, VerdeGanancia,
                        R.drawable.ic_stats, Modifier.weight(1f),
                    )
                }
                OutlinedCard(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_shield), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Los créditos son virtuales: no se compran, no se retiran y no equivalen a dinero real.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountCard(
    title: String,
    subtitle: String,
    amount: String,
    color: Color,
    @DrawableRes icon: Int,
    modifier: Modifier,
) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.size(36.dp).background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(credits(amount), color = color, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
