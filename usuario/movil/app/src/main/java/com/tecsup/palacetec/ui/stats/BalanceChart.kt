package com.tecsup.palacetec.ui.stats

import com.tecsup.palacetec.data.remote.BalancePoint
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Gráfico ya calculado: la pantalla solo lo dibuja. Las alturas van de 0 (abajo) a 1 (arriba). */
data class BalanceChart(
    val heights: List<Float>,
    /** Valores redondos del eje, de abajo hacia arriba. */
    val ticks: List<Long>,
)

/** El eje no parte de cero: lo que importa es cómo cambió el saldo. null si no hay puntos. */
fun balanceChart(points: List<BalancePoint>): BalanceChart? {
    val values = points.map { it.balance.toDoubleOrNull() ?: 0.0 }
    val min = values.minOrNull() ?: return null
    val max = values.max()
    val step = niceStep(max - min)
    var low = floor(min / step) * step
    var high = ceil(max / step) * step
    if (low == high) {
        // Saldo sin cambios: la línea queda al medio, salvo que sea cero.
        low = (low - step).coerceAtLeast(0.0)
        high += step
    }
    val count = ((high - low) / step).toInt()
    return BalanceChart(
        heights = values.map { ((it - low) / (high - low)).toFloat() },
        ticks = (0..count).map { (low + it * step).toLong() },
    )
}

/** 1, 2, 5, 10, 20, 50… para que el eje tenga entre dos y cuatro divisiones. */
private fun niceStep(range: Double): Double {
    val raw = range / MAX_STEPS
    if (raw <= 1) return 1.0
    val power = 10.0.pow(floor(log10(raw)))
    return listOf(1, 2, 5, 10).first { it * power >= raw } * power
}

private const val MAX_STEPS = 3
