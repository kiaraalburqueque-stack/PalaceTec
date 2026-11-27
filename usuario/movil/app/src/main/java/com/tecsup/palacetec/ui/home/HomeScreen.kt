package com.tecsup.palacetec.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.tecsup.palacetec.R
import com.tecsup.palacetec.data.remote.DailyLimit
import com.tecsup.palacetec.data.remote.Game
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.components.BalanceCard
import com.tecsup.palacetec.ui.components.DailyLimitDialog
import com.tecsup.palacetec.ui.components.ErrorBox
import com.tecsup.palacetec.ui.components.LoadingBox
import com.tecsup.palacetec.ui.components.SectionHeader
import com.tecsup.palacetec.ui.components.credits
import com.tecsup.palacetec.ui.components.fraction
import com.tecsup.palacetec.ui.theme.Dorado
import com.tecsup.palacetec.ui.theme.DoradoOscuro
import com.tecsup.palacetec.ui.theme.Fondo
import com.tecsup.palacetec.ui.theme.Pano
import com.tecsup.palacetec.ui.theme.PanoOscuro
import com.tecsup.palacetec.ui.theme.RojoOscuro
import com.tecsup.palacetec.ui.theme.RojoRuleta
import com.tecsup.palacetec.ui.theme.SuperficieAlta
import com.tecsup.palacetec.ui.theme.SuperficieMaxima
import com.tecsup.palacetec.ui.theme.Texto

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
    // Filtro de la vista: no cambia lo que se carga.
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    if (state.loading && state.wallet == null) {
        LoadingBox(modifier)
        return
    }
    // Los que se juegan desde el móvil van primero.
    val games = state.games.sortedByDescending { it.roomsUrl != null }
    val categories = games.map { it.category }.distinct()
    val shown = games.filter { category == null || it.category == category }
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = viewModel::load,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { message -> item { ErrorBox(message, viewModel::load) } }
            state.wallet?.let { wallet ->
                item {
                    BalanceCard(wallet.balance, wallet.isLowBalance, Modifier.padding(horizontal = 16.dp)) {
                        Button(onClick = onPlayRoulette, shape = MaterialTheme.shapes.small) { Text("Jugar") }
                    }
                }
            }
            item {
                ResponsibleGamingCard(
                    state.dailyLimit, viewModel::openLimitDialog, onResponsible, Modifier.padding(horizontal = 16.dp),
                )
            }
            if (games.isNotEmpty()) {
                item { SectionHeader("Destacados", Modifier.padding(horizontal = 16.dp)) }
                item { FeaturedCarousel(games.take(FEATURED), onPlayRoulette) }
                item { SectionHeader("Juegos", Modifier.padding(horizontal = 16.dp)) }
                item { CategoryChips(categories, category) { category = it } }
            } else if (state.error == null) {
                item {
                    Text(
                        "No hay juegos disponibles por ahora.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(shown.chunked(2), key = { row -> row.joinToString { it.id.toString() } }) { row ->
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { GameTile(it, onPlayRoulette, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
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
}

/** Sobrio a propósito: el límite y los avisos tienen que leerse, no competir con los juegos. */
@Composable
private fun ResponsibleGamingCard(limit: DailyLimit?, onChange: () -> Unit, onMore: () -> Unit, modifier: Modifier) {
    OutlinedCard(
        modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_shield), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
                )
                Text(
                    "Juego responsable", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                Text(
                    "+18", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Solo para mayores de 18 años. Juega por diversión, pon límites y haz pausas. " +
                    "Los créditos son virtuales y no tienen valor en dinero real.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (limit != null) {
                if (limit.configured) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("Límite diario: ${credits(limit.limit)}", Modifier.weight(1f))
                        Text("Te quedan ${credits(limit.remaining)}")
                    }
                    LinearProgressIndicator(
                        progress = { fraction(limit.spent, limit.limit) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Define tu límite diario para poder apostar.", Modifier.padding(top = 4.dp))
                }
                limit.pendingLimit?.let {
                    Text(
                        "Aumento a ${credits(it)} pendiente: se aplica a las 24 horas de pedirlo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (limit != null) {
                    TextButton(onClick = onChange) { Text(if (limit.configured) "Cambiar límite" else "Definir límite") }
                }
                TextButton(onClick = onMore) { Text("Pausa, autoexclusión y sesión") }
            }
        }
    }
}

@Composable
private fun FeaturedCarousel(games: List<Game>, onPlayRoulette: () -> Unit) {
    val pager = rememberPagerState { games.size }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(
            pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            key = { games[it].id },
        ) { page -> FeaturedBanner(games[page], onPlayRoulette) }
        if (games.size > 1) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                repeat(games.size) { index ->
                    val current = index == pager.currentPage
                    Box(
                        Modifier
                            .size(width = if (current) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(if (current) Dorado else MaterialTheme.colorScheme.outline),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedBanner(game: Game, onPlayRoulette: () -> Unit) {
    val art = gameArt(game)
    val playable = game.roomsUrl != null
    Card(
        onClick = onPlayRoulette,
        enabled = playable,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().height(170.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            GameArt(game, art, iconSize = 120, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Fondo.copy(alpha = 0.9f), Color.Transparent))),
            )
            Column(
                Modifier.align(Alignment.CenterStart).padding(20.dp).fillMaxWidth(0.65f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    categoryLabel(game.category).uppercase(), style = MaterialTheme.typography.labelMedium,
                    color = Dorado,
                )
                Text(
                    game.title, style = MaterialTheme.typography.headlineSmall, color = Texto,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Apuesta de ${credits(game.minBet)} a ${credits(game.maxBet)}",
                    style = MaterialTheme.typography.bodySmall, color = Texto.copy(alpha = 0.8f),
                )
                PlayBadge(playable)
            }
        }
    }
}

@Composable
private fun CategoryChips(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (listOf<String?>(null) + categories).forEach { code ->
            FilterChip(
                selected = selected == code,
                onClick = { onSelect(code) },
                label = { Text(code?.let(::categoryLabel) ?: "Todos") },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = selected == code,
                    borderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        }
    }
}

@Composable
private fun GameTile(game: Game, onPlayRoulette: () -> Unit, modifier: Modifier) {
    // Solo la ruleta se juega desde el móvil.
    val playable = game.roomsUrl != null
    Card(
        onClick = onPlayRoulette,
        enabled = playable,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
            GameArt(game, gameArt(game), iconSize = 56, modifier = Modifier.fillMaxSize())
            game.rtp?.let {
                Text(
                    "RTP $it %",
                    style = MaterialTheme.typography.labelSmall,
                    color = Texto,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Fondo.copy(alpha = 0.7f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(game.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${credits(game.minBet)} – ${credits(game.maxBet)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlayBadge(playable)
        }
    }
}

@Composable
private fun PlayBadge(playable: Boolean) {
    Text(
        if (playable) "Jugar" else "Solo en la web",
        style = MaterialTheme.typography.labelMedium,
        color = if (playable) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                if (playable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                CircleShape,
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

private class Art(val colors: List<Color>, @param:DrawableRes val icon: Int)

private fun gameArt(game: Game): Art = when (game.category) {
    "RULETA" -> Art(listOf(Pano, PanoOscuro), R.drawable.ic_roulette)
    "CARTAS" -> Art(listOf(RojoRuleta, RojoOscuro), R.drawable.ic_cards)
    "DEPORTES" -> Art(listOf(DoradoOscuro, SuperficieAlta), R.drawable.ic_stats)
    else -> Art(listOf(SuperficieMaxima, SuperficieAlta), R.drawable.ic_cards)
}

/** La portada del juego si la tiene; si no, o mientras carga, un fondo por categoría con su ícono. */
@Composable
private fun GameArt(game: Game, art: Art, iconSize: Int, modifier: Modifier) {
    Box(modifier.background(Brush.linearGradient(art.colors)), contentAlignment = Alignment.CenterEnd) {
        Icon(
            painterResource(art.icon), contentDescription = null,
            tint = Texto.copy(alpha = 0.25f),
            modifier = Modifier.padding(end = 12.dp).size(iconSize.dp),
        )
        if (game.coverUrl != null) {
            AsyncImage(
                model = game.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun categoryLabel(code: String) = when (code) {
    "RULETA" -> "Ruleta"
    "CARTAS" -> "Cartas"
    "DEPORTES" -> "Deportes virtuales"
    else -> "Otros"
}

private const val FEATURED = 5
