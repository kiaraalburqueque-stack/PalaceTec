package com.tecsup.palacetec.ui.notifications

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.palacetec.R
import com.tecsup.palacetec.data.remote.PlayerNotification
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.components.ErrorBox
import com.tecsup.palacetec.ui.components.LoadingBox
import com.tecsup.palacetec.ui.components.limaDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = appViewModel { NotificationsViewModel(it.notificationRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()
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
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.error?.let { message -> item { ErrorBox(message, viewModel::load) } }
            if (state.loaded) {
                item { Summary(unread, state.markingAll, viewModel::markAllRead) }
                state.actionError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (state.items.isEmpty()) {
                    item {
                        Text(
                            "No tienes notificaciones.", textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        )
                    }
                }
            }
            items(state.items, key = { it.id }) { item -> NotificationCard(item) { viewModel.markRead(item) } }
        }
    }
}

@Composable
private fun Summary(unread: Long, markingAll: Boolean, onMarkAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (unread == 0L) "Todo leído" else "$unread sin leer",
            Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onMarkAll, enabled = unread > 0 && !markingAll) { Text("Marcar todas como leídas") }
    }
}

@Composable
private fun NotificationCard(item: PlayerNotification, onClick: () -> Unit) {
    val container = if (item.isRead) MaterialTheme.colorScheme.surfaceContainer
    else MaterialTheme.colorScheme.surfaceContainerHighest
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            val (icon, category) = categoryStyle(item.category)
            Icon(
                painterResource(icon), contentDescription = category,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.Bold)
                Text(
                    item.message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "$category · ${limaDateTime(item.createdAt)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!item.isRead) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        .semantics { contentDescription = "Sin leer" },
                )
            }
        }
    }
}

private class CategoryStyle(@param:DrawableRes val icon: Int, val label: String) {
    operator fun component1() = icon
    operator fun component2() = label
}

private fun categoryStyle(category: String) = when (category) {
    "PROMOTION" -> CategoryStyle(R.drawable.ic_gift, "Promoción")
    "SECURITY" -> CategoryStyle(R.drawable.ic_lock, "Seguridad")
    "GAME" -> CategoryStyle(R.drawable.ic_roulette, "Juego")
    else -> CategoryStyle(R.drawable.ic_notifications, "Aviso")
}
