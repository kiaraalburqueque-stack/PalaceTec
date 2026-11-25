package com.tecsup.palacetec.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.palacetec.data.remote.BiggestWin
import com.tecsup.palacetec.data.remote.TopGames
import com.tecsup.palacetec.data.remote.WinRate
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.components.ErrorBox
import com.tecsup.palacetec.ui.components.LoadingBox
import com.tecsup.palacetec.ui.components.compact
import com.tecsup.palacetec.ui.components.credits
import com.tecsup.palacetec.ui.components.dayMonth
import com.tecsup.palacetec.ui.components.limaDateTime
import com.tecsup.palacetec.ui.components.percent
import com.tecsup.palacetec.ui.components.signedCredits
import com.tecsup.palacetec.ui.theme.MarfilTenue
import com.tecsup.palacetec.ui.theme.RojoPerdida
import com.tecsup.palacetec.ui.theme.VerdeGanancia

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = appViewModel { StatsViewModel(it.statsRepository) },
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
            state.winRate?.let { WinRateCard(it) }
            if (state.loaded) BiggestWinCard(state.biggestWin)
            BalanceCard(state, viewModel)
            state.topGames?.let { TopGamesCard(it) }
        }
    }
}

@Composable
private fun StatCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --- Porcentaje de victorias ---

@Composable
private fun WinRateCard(rate: WinRate) {
    StatCard("Porcentaje de victorias") {
        if (rate.total == 0L) {
            Text("Todavía no tienes apuestas resueltas.")
            return@StatCard
        }
        Text(
            percent(rate.winRate), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        val parts = listOf(
            Triple("Ganadas", rate.won, VerdeGanancia),
            Triple("Empates", rate.push, MarfilTenue),
            Triple("Perdidas", rate.lost, RojoPerdida),
        )
        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.filter { it.second > 0 }.forEach { (_, count, color) ->
                Box(Modifier.weight(count.toFloat()).fillMaxSize().background(color, CircleShape))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            parts.forEach { (label, count, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(color, CircleShape))
                    Text(
                        "$label: $count", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        Note("Sobre ${rate.total} apuestas resueltas.")
    }
}

// --- Mayor ganancia ---

@Composable
private fun BiggestWinCard(win: BiggestWin?) {
    StatCard("Mayor ganancia") {
        if (win == null) {
            Text("Todavía no registras una ganancia. Tu récord aparecerá aquí.")
            return@StatCard
        }
        Text(
            signedCredits(win.net), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = VerdeGanancia,
        )
        Text(win.game + (win.roundId?.let { " · Ronda $it" } ?: ""))
        Note("Apostaste ${credits(win.amount)} y cobraste ${credits(win.payout)} el ${limaDateTime(win.createdAt)}.")
    }
}

// --- Historial de saldo ---

@Composable
private fun BalanceCard(state: StatsUiState, viewModel: StatsViewModel) {
    StatCard("Historial de saldo") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsViewModel.PERIODS.forEach { days ->
                FilterChip(
                    selected = state.period == days,
                    onClick = { viewModel.selectPeriod(days) },
                    label = { Text("$days días") },
                )
            }
        }
        state.balanceError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = viewModel::loadBalance) { Text("Reintentar") }
        }
        val balance = state.balance
        val chart = state.chart
        val point = balance?.points?.getOrNull(state.selectedPoint)
        if (balance == null || chart == null || point == null) {
            if (state.loadingBalance) Note("Consultando saldo…")
            return@StatCard
        }
        Column {
            Text(credits(point.balance), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Note(
                dayMonth(point.date) + when (point.movements) {
                    0 -> " · sin movimientos"
                    1 -> " · 1 movimiento"
                    else -> " · ${point.movements} movimientos"
                },
            )
        }
        LineChart(
            chart, state.selectedPoint, viewModel::onChartTouch,
            description = "Saldo de los últimos ${balance.period} días: de ${credits(balance.openingBalance)} " +
                "a ${credits(balance.closingBalance)}",
        )
        Row(Modifier.fillMaxWidth().padding(start = AXIS_WIDTH), horizontalArrangement = Arrangement.SpaceBetween) {
            Note(dayMonth(balance.points.first().date))
            Note(dayMonth(balance.points.last().date))
        }
        Note("Saldo al cierre de cada día. Toca o arrastra para ver otro día.")
    }
}

private val AXIS_WIDTH = 52.dp

@Composable
private fun LineChart(
    chart: BalanceChart,
    selected: Int,
    onTouch: (Float) -> Unit,
    description: String,
) {
    val line = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val labels = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val touch by rememberUpdatedState(onTouch)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                val left = AXIS_WIDTH.toPx()
                detectTapGestures { touch((it.x - left) / (size.width - left)) }
            }
            .pointerInput(Unit) {
                val left = AXIS_WIDTH.toPx()
                detectHorizontalDragGestures { change, _ -> touch((change.position.x - left) / (size.width - left)) }
            },
    ) {
        // Margen para que los puntos extremos y las etiquetas no se corten.
        val edge = 8.dp.toPx()
        val area = PlotArea(AXIS_WIDTH.toPx(), edge, size.width - edge, size.height - edge)
        drawAxis(chart.ticks, area, grid, measurer, labels)
        val points = chart.heights.mapIndexed { index, height ->
            val position = if (chart.heights.size == 1) 1f else index / (chart.heights.size - 1f)
            Offset(area.left + position * area.width, area.bottom - height * area.height)
        }
        drawBalance(points, area, line)
        val current = points.getOrNull(selected) ?: return@Canvas
        drawLine(grid, Offset(current.x, area.top), Offset(current.x, area.bottom), 1.dp.toPx())
        drawCircle(surface, 7.dp.toPx(), current)
        drawCircle(line, 5.dp.toPx(), current)
    }
}

private class PlotArea(val left: Float, val top: Float, right: Float, val bottom: Float) {
    val width = right - left
    val height = bottom - top
}

private fun DrawScope.drawAxis(
    ticks: List<Long>, area: PlotArea, color: Color, measurer: TextMeasurer, style: TextStyle,
) {
    ticks.forEachIndexed { index, tick ->
        val y = area.bottom - area.height * index / (ticks.size - 1).coerceAtLeast(1)
        drawLine(color, Offset(area.left, y), Offset(area.left + area.width, y), 1.dp.toPx())
        val label = measurer.measure(compact(tick), style)
        drawText(label, topLeft = Offset(0f, y - label.size.height / 2f))
    }
}

private fun DrawScope.drawBalance(points: List<Offset>, area: PlotArea, color: Color) {
    if (points.isEmpty()) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    val fill = Path().apply {
        addPath(path)
        lineTo(points.last().x, area.bottom)
        lineTo(points.first().x, area.bottom)
        close()
    }
    drawPath(fill, color.copy(alpha = 0.1f))
    drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// --- Juegos top ---

@Composable
private fun TopGamesCard(top: TopGames) {
    StatCard("Juegos más jugados") {
        if (top.results.isEmpty()) {
            Text("Todavía no tienes juegos jugados.")
            return@StatCard
        }
        val most = top.results.maxOf { it.rounds }.coerceAtLeast(1)
        top.results.forEach { game ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(game.title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(if (game.rounds == 1L) "1 ronda" else "${game.rounds} rondas")
                }
                Box(
                    Modifier
                        .fillMaxWidth(game.rounds.toFloat() / most)
                        .height(8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                        ),
                )
                Note("Apostado: ${credits(game.wagered)}")
            }
        }
    }
}
