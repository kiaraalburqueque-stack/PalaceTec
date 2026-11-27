package com.tecsup.palacetec.ui.roulette

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.palacetec.R
import com.tecsup.palacetec.data.remote.BetKind
import com.tecsup.palacetec.data.remote.BetResponse
import com.tecsup.palacetec.data.remote.NumberColor
import com.tecsup.palacetec.data.remote.Round
import com.tecsup.palacetec.data.remote.RoundNumber
import com.tecsup.palacetec.data.remote.RouletteRoom
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.components.DailyLimitDialog
import com.tecsup.palacetec.ui.components.ErrorBox
import com.tecsup.palacetec.ui.components.LoadingBox
import com.tecsup.palacetec.ui.components.PrimaryButton
import com.tecsup.palacetec.ui.components.SectionHeader
import com.tecsup.palacetec.ui.components.betLabel
import com.tecsup.palacetec.ui.components.credits
import com.tecsup.palacetec.ui.components.isoMillis
import com.tecsup.palacetec.ui.theme.Dorado
import com.tecsup.palacetec.ui.theme.Fondo
import com.tecsup.palacetec.ui.theme.NegroRuleta
import com.tecsup.palacetec.ui.theme.Pano
import com.tecsup.palacetec.ui.theme.PanoOscuro
import com.tecsup.palacetec.ui.theme.RojoPerdida
import com.tecsup.palacetec.ui.theme.RojoRuleta
import com.tecsup.palacetec.ui.theme.SuperficieMaxima
import com.tecsup.palacetec.ui.theme.Texto
import com.tecsup.palacetec.ui.theme.Verde
import com.tecsup.palacetec.ui.theme.VerdeGanancia
import com.tecsup.palacetec.ui.theme.VerdeRuleta

@Composable
fun RouletteScreen(
    modifier: Modifier = Modifier,
    viewModel: RouletteViewModel = appViewModel {
        RouletteViewModel(it.rouletteRepository, it.walletRepository, it.historyRepository, it.dailyLimitRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // La consulta por segundo vive solo mientras la pantalla está visible.
    LifecycleStartEffect(Unit) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }

    if (state.room == null) {
        RoomList(state, viewModel::loadRooms, viewModel::selectRoom, modifier)
    } else {
        RouletteTable(state, viewModel, modifier)
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

// --- Salas ---

@Composable
private fun RoomList(
    state: RouletteUiState,
    onRetry: () -> Unit,
    onSelect: (RouletteRoom) -> Unit,
    modifier: Modifier,
) {
    if (state.loadingRooms && state.rooms.isEmpty()) {
        LoadingBox(modifier)
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("Elige una mesa") }
        state.roomsError?.let { message -> item { ErrorBox(message, onRetry) } }
        if (state.rooms.isEmpty() && state.roomsError == null) {
            item { ErrorBox("No hay salas disponibles por ahora.", onRetry) }
        }
        items(state.rooms, key = { it.id }) { room -> RoomCard(room) { onSelect(room) } }
    }
}

@Composable
private fun RoomCard(room: RouletteRoom, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Dorado.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .background(Brush.linearGradient(listOf(Pano, PanoOscuro)))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_roulette), contentDescription = null,
                tint = Dorado, modifier = Modifier.size(44.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(room.name, style = MaterialTheme.typography.titleMedium, color = Texto)
                Text(
                    "De ${credits(room.minBet)} a ${credits(room.maxBet)} por ronda",
                    style = MaterialTheme.typography.bodySmall, color = Texto.copy(alpha = 0.8f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Verde, CircleShape))
                    Text(
                        "${room.players} en línea", style = MaterialTheme.typography.bodySmall,
                        color = Texto.copy(alpha = 0.8f), modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Button(onClick = onClick, shape = MaterialTheme.shapes.small) { Text("Entrar") }
        }
    }
}

// --- Mesa ---

@Composable
private fun RouletteTable(state: RouletteUiState, viewModel: RouletteViewModel, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        // La ronda y su cuenta regresiva quedan fijas arriba mientras se elige la jugada.
        TableHeader(state, viewModel::leaveRoom)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.tableError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.result?.let { ResultBanner(it) }
            if (state.roundBets.isNotEmpty()) MyRoundBets(state.roundBets)
            BetOptions(state, viewModel)
        }
        BetBar(state, viewModel)
    }
}

@Composable
private fun TableHeader(state: RouletteUiState, onLeave: () -> Unit) {
    val round = state.table?.round
    val recent = state.table?.recentResults.orEmpty()
    val result = state.result
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Pano, PanoOscuro)))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.room?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Texto)
                Text(
                    "Saldo ${credits(state.wallet?.balance)}",
                    style = MaterialTheme.typography.labelLarge, color = Dorado,
                )
            }
            TextButton(onClick = onLeave) { Text("Cambiar mesa", color = Texto) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Countdown(round)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                when {
                    round == null -> Text("Esperando una ronda…", color = Texto)
                    round.acceptsBets -> Text(
                        "Hagan sus apuestas", style = MaterialTheme.typography.titleMedium, color = Texto,
                    )
                    round.status == "SETTLED" -> Text("Ronda terminada. Esperando la siguiente…", color = Texto)
                    else -> Text("¡No va más!", style = MaterialTheme.typography.titleLarge, color = Dorado)
                }
                if (round != null) {
                    Text(
                        "Ronda ${round.id}", style = MaterialTheme.typography.bodySmall,
                        color = Texto.copy(alpha = 0.7f),
                    )
                }
            }
            val last = result?.let { RoundNumber(it.roundId, it.number, it.color) } ?: recent.firstOrNull()
            if (last != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NumberCircle(last.number, last.color, 52)
                    Text("Ganador", style = MaterialTheme.typography.labelSmall, color = Texto.copy(alpha = 0.8f))
                }
            }
        }
        if (recent.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Últimos", style = MaterialTheme.typography.labelSmall, color = Texto.copy(alpha = 0.7f))
                recent.forEach { NumberCircle(it.number, it.color, 26) }
            }
        }
    }
}

/** Anillo que se vacía con los segundos que quedan para apostar. */
@Composable
private fun Countdown(round: Round?) {
    val seconds = round?.takeIf { it.acceptsBets }?.remainingSeconds
    val open = seconds != null
    val total = round?.let { roundSeconds(it) } ?: 1
    val urgent = seconds != null && seconds <= 5
    val color = if (urgent) RojoRuleta else Dorado
    Box(
        Modifier.size(72.dp).semantics {
            contentDescription = if (seconds != null) "Quedan $seconds segundos para apostar" else "Apuestas cerradas"
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val arc = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawCircle(Fondo.copy(alpha = 0.55f))
            drawArc(Texto.copy(alpha = 0.15f), 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            val sweep = if (seconds != null) 360f * (seconds.toFloat() / total).coerceIn(0f, 1f) else 0f
            drawArc(color, -90f, sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                seconds?.toString() ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (open) color else Texto,
            )
            if (open) Text("seg", style = MaterialTheme.typography.labelSmall, color = Texto.copy(alpha = 0.7f))
        }
    }
}

/** Duración de la ronda según sus fechas; si no se pueden leer, la cuenta parte de lo que queda. */
private fun roundSeconds(round: Round): Long {
    val opened = isoMillis(round.openedAt)
    val closes = isoMillis(round.closesAt)
    val total = if (opened != null && closes != null) (closes - opened) / 1_000 else 0
    return maxOf(total, round.remainingSeconds, 1)
}

@Composable
private fun ResultBanner(result: RoundResult) {
    val staked = result.staked ?: return
    val payout = result.payout ?: return
    if (staked.signum() == 0) return
    // Cobrar un premio menor que lo apostado no es ganar.
    val net = payout - staked
    val title = when {
        net.signum() > 0 -> "¡Ganaste ${credits(net.toPlainString())}!"
        net.signum() == 0 -> "Recuperaste lo apostado."
        else -> "Perdiste ${credits(net.negate().toPlainString())}."
    }
    val color = if (net.signum() < 0) RojoPerdida else VerdeGanancia
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, color),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberCircle(result.number, result.color, 40)
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, color = color, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Apostaste ${credits(staked.toPlainString())} y el premio fue ${credits(payout.toPlainString())}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NumberCircle(number: Int, color: String, size: Int) {
    Box(
        Modifier.size(size.dp).background(rouletteColor(color), CircleShape)
            .border(if (size >= 40) 2.dp else 1.dp, Dorado.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(), color = Texto, fontWeight = FontWeight.Bold,
            style = if (size >= 48) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun MyRoundBets(bets: List<BetResponse>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tus apuestas en esta ronda", style = MaterialTheme.typography.titleSmall)
            bets.forEach {
                Row(Modifier.fillMaxWidth()) {
                    Text(betLabel(it.kind, it.selection), Modifier.weight(1f))
                    Text(credits(it.amount), color = Dorado, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- Paño ---

@Composable
private fun BetOptions(state: RouletteUiState, viewModel: RouletteViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BetKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind == state.kind,
                    onClick = { viewModel.selectKind(kind) },
                    label = { Text(kind.label) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = kind == state.kind,
                        borderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
            }
        }
        Felt {
            when (state.kind) {
                BetKind.STRAIGHT_UP -> NumberGrid(state.table?.numbers.orEmpty(), state.selection, viewModel::select)
                BetKind.COLOR -> OptionRow(
                    listOf(Option("rojo", "Rojo", RojoRuleta), Option("negro", "Negro", NegroRuleta)),
                    state.selection, viewModel::select,
                )
                BetKind.PARITY -> OptionRow(
                    listOf(Option("par", "Par", Color.Transparent), Option("impar", "Impar", Color.Transparent)),
                    state.selection, viewModel::select,
                )
            }
        }
    }
}

/** Paño verde con borde dorado, como la mesa. */
@Composable
private fun Felt(content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.radialGradient(listOf(Pano, PanoOscuro), radius = 900f))
            .border(2.dp, Dorado.copy(alpha = 0.6f), shape)
            .padding(10.dp),
    ) { content() }
}

/** Como en la mesa real girada: el cero arriba y luego filas de tres números. */
@Composable
private fun NumberGrid(numbers: List<NumberColor>, selected: String?, onSelect: (String) -> Unit) {
    val (zero, others) = numbers.sortedBy { it.number }.partition { it.number == 0 }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (listOf(zero) + others.chunked(3)).filter { it.isNotEmpty() }.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach {
                    OptionCell(
                        Option(it.number.toString(), it.number.toString(), rouletteColor(it.color)),
                        selected = selected == it.number.toString(),
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f).height(40.dp),
                    )
                }
            }
        }
    }
}

private data class Option(val value: String, val label: String, val color: Color)

@Composable
private fun OptionRow(options: List<Option>, selected: String?, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach {
            OptionCell(it, selected == it.value, onSelect, Modifier.weight(1f).height(88.dp))
        }
    }
}

@Composable
private fun OptionCell(option: Option, selected: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .clip(shape)
            .background(option.color)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) Dorado else Texto.copy(alpha = 0.35f),
                shape,
            )
            .selectable(selected = selected, role = Role.RadioButton) { onSelect(option.value) },
        contentAlignment = Alignment.Center,
    ) {
        Text(option.label, color = Texto, fontWeight = FontWeight.Bold)
        // La ficha marca la jugada elegida.
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(3.dp).size(12.dp)
                    .background(Dorado, CircleShape).border(2.dp, Texto, CircleShape),
            )
        }
    }
}

// --- Fichas y apuesta ---

@Composable
private fun BetBar(state: RouletteUiState, viewModel: RouletteViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.message?.let {
                Text(
                    it,
                    color = if (state.messageIsError) MaterialTheme.colorScheme.error else VerdeGanancia,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val values = state.table?.chipValues.orEmpty()
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    values.forEachIndexed { index, value ->
                        CasinoChip(value, CHIP_COLORS[index % CHIP_COLORS.size], enabled = !state.placing) {
                            viewModel.addChip(value)
                        }
                    }
                }
                TextButton(onClick = viewModel::clearChips, enabled = state.chips.isNotEmpty() && !state.placing) {
                    Text("Limpiar")
                }
            }
            if (state.chips.isNotEmpty()) {
                Text(
                    "En la mesa: ${credits(state.total.toString())} en ${state.chips.size} fichas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val blocked = state.blockedReason
            PrimaryButton(
                text = blocked ?: "Apostar ${credits(state.total.toString())}",
                onClick = viewModel::placeBet,
                enabled = blocked == null,
                loading = state.placing,
            )
        }
    }
}

/** Ficha de casino: color por valor y borde blanco punteado. */
@Composable
private fun CasinoChip(value: Int, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Agregar ficha de $value", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(color)
            drawCircle(
                Texto,
                radius * 0.82f,
                style = Stroke(radius * 0.16f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f))),
            )
            drawCircle(Fondo.copy(alpha = 0.35f), radius * 0.6f)
            drawCircle(Texto.copy(alpha = 0.6f), radius * 0.6f, style = Stroke(1.5f))
        }
        Text(
            chipLabel(value), color = Texto, fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun chipLabel(value: Int) = if (value >= 1_000 && value % 1_000 == 0) "${value / 1_000}K" else "$value"

private val CHIP_COLORS = listOf(SuperficieMaxima, RojoRuleta, VerdeRuleta, Color(0xFF1E5AA8), Color(0xFFB8860B))

private fun rouletteColor(color: String): Color = when (color) {
    "rojo" -> RojoRuleta
    "verde" -> VerdeRuleta
    else -> NegroRuleta
}
