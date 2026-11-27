package com.tecsup.palacetec.ui.roulette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.tecsup.palacetec.ui.components.betLabel
import com.tecsup.palacetec.ui.components.credits
import com.tecsup.palacetec.ui.theme.Texto
import com.tecsup.palacetec.ui.theme.Dorado
import com.tecsup.palacetec.ui.theme.NegroRuleta
import com.tecsup.palacetec.ui.theme.RojoRuleta
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
        item { Text("Elige una sala", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        state.roomsError?.let { message -> item { ErrorBox(message, onRetry) } }
        if (state.rooms.isEmpty() && state.roomsError == null) {
            item { ErrorBox("No hay salas disponibles por ahora.", onRetry) }
        }
        items(state.rooms, key = { it.id }) { room ->
            Card(onClick = { onSelect(room) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(room.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "De ${credits(room.minBet)} a ${credits(room.maxBet)} por ronda",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("${room.players} en línea", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Entrar", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RouletteTable(state: RouletteUiState, viewModel: RouletteViewModel, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.room?.name.orEmpty(), fontWeight = FontWeight.Bold)
                    Text("Saldo: ${credits(state.wallet?.balance)}", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = viewModel::leaveRoom) { Text("Cambiar sala") }
            }
            state.tableError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            RoundCard(state.table?.round, state.result, state.table?.recentResults.orEmpty())
            if (state.roundBets.isNotEmpty()) MyRoundBets(state.roundBets)
            BetOptions(state, viewModel)
            Spacer(Modifier.height(4.dp))
        }
        BetBar(state, viewModel)
    }
}

@Composable
private fun RoundCard(round: Round?, result: RoundResult?, recent: List<RoundNumber>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    when {
                        round == null -> Text("Esperando una ronda…")
                        round.acceptsBets -> {
                            Text("Hagan sus apuestas")
                            Text(
                                "${round.remainingSeconds} s",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (round.remainingSeconds <= 5) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary,
                            )
                        }
                        round.status == "SETTLED" -> Text("Ronda terminada. Esperando la siguiente…")
                        else -> Text(
                            "¡No va más!",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (round != null) {
                        Text(
                            "Ronda ${round.id}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val last = result?.let { RoundNumber(it.roundId, it.number, it.color) } ?: recent.firstOrNull()
                if (last != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NumberCircle(last.number, last.color, 64)
                        Text("Número ganador", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Ronda ${last.roundId}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (result != null) ResultText(result)
            if (recent.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    recent.forEach { NumberCircle(it.number, it.color, 28) }
                }
            }
        }
    }
}

@Composable
private fun ResultText(result: RoundResult) {
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
    Column {
        Text(
            title,
            color = if (net.signum() < 0) MaterialTheme.colorScheme.error else VerdeGanancia,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Apostaste ${credits(staked.toPlainString())} y el premio fue ${credits(payout.toPlainString())}.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun NumberCircle(number: Int, color: String, size: Int) {
    Box(
        Modifier.size(size.dp).background(rouletteColor(color), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Tus apuestas en esta ronda", fontWeight = FontWeight.Bold)
            bets.forEach {
                Row(Modifier.fillMaxWidth()) {
                    Text(betLabel(it.kind, it.selection), Modifier.weight(1f))
                    Text(credits(it.amount), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun BetOptions(state: RouletteUiState, viewModel: RouletteViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BetKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind == state.kind,
                    onClick = { viewModel.selectKind(kind) },
                    label = { Text(kind.label) },
                )
            }
        }
        when (state.kind) {
            BetKind.STRAIGHT_UP -> NumberGrid(state.table?.numbers.orEmpty(), state.selection, viewModel::select)
            BetKind.COLOR -> OptionRow(
                listOf(Option("rojo", "Rojo", RojoRuleta), Option("negro", "Negro", NegroRuleta)),
                state.selection, viewModel::select,
            )
            BetKind.PARITY -> {
                val background = MaterialTheme.colorScheme.surfaceVariant
                OptionRow(
                    listOf(Option("par", "Par", background), Option("impar", "Impar", background)),
                    state.selection, viewModel::select,
                )
            }
        }
    }
}

@Composable
private fun NumberGrid(numbers: List<NumberColor>, selected: String?, onSelect: (String) -> Unit) {
    val (zero, others) = numbers.partition { it.number == 0 }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (listOf(zero) + others.chunked(6)).filter { it.isNotEmpty() }.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach {
                    OptionCell(
                        Option(it.number.toString(), it.number.toString(), rouletteColor(it.color)),
                        selected = selected == it.number.toString(),
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f).height(44.dp),
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
            OptionCell(it, selected == it.value, onSelect, Modifier.weight(1f).height(72.dp))
        }
    }
}

@Composable
private fun OptionCell(option: Option, selected: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(option.color)
            .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else option.color, shape)
            .selectable(selected = selected, role = Role.RadioButton) { onSelect(option.value) },
        contentAlignment = Alignment.Center,
    ) {
        Text(option.label, color = Texto, fontWeight = FontWeight.Bold)
    }
}

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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.table?.chipValues.orEmpty().forEach { value ->
                    Chip(value, enabled = !state.placing) { viewModel.addChip(value) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::clearChips, enabled = state.chips.isNotEmpty() && !state.placing) {
                    Text("Limpiar")
                }
            }
            val blocked = state.blockedReason
            PrimaryButton(
                text = blocked ?: "Apostar ${credits(state.total.toString())}",
                onClick = viewModel::placeBet,
                enabled = blocked == null,
                loading = state.placing,
            )
            if (state.chips.isNotEmpty()) {
                Text(
                    "Fichas: ${credits(state.total.toString())}",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Chip(value: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(3.dp, if (value >= 100) Dorado else MaterialTheme.colorScheme.primary, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Agregar ficha de $value", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(value.toString(), color = Texto, fontWeight = FontWeight.Bold)
    }
}

private fun rouletteColor(color: String): Color = when (color) {
    "rojo" -> RojoRuleta
    "verde" -> VerdeRuleta
    else -> NegroRuleta
}
