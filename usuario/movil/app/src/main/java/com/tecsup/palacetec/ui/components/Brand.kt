package com.tecsup.palacetec.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import com.tecsup.palacetec.ui.theme.Dorado
import com.tecsup.palacetec.ui.theme.Fondo
import com.tecsup.palacetec.ui.theme.NegroRuleta
import com.tecsup.palacetec.ui.theme.RojoRuleta
import com.tecsup.palacetec.ui.theme.Texto
import com.tecsup.palacetec.ui.theme.VerdeRuleta

/** Logo de PalaceTec: una rueda de ruleta con aro dorado. */
@Composable
fun PalaceTecLogo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).semantics { contentDescription = "PalaceTec" }) {
        val radius = this.size.minDimension / 2
        val rim = radius * 0.12f
        val wheel = radius - rim
        val sweep = 360f / SEGMENTS
        val topLeft = Offset(center.x - wheel, center.y - wheel)
        repeat(SEGMENTS) { index ->
            val color = when {
                index == 0 -> VerdeRuleta
                index % 2 == 1 -> RojoRuleta
                else -> NegroRuleta
            }
            drawArc(color, -90f - sweep / 2 + index * sweep, sweep, useCenter = true, topLeft, Size(wheel * 2, wheel * 2))
        }
        drawCircle(Dorado, radius - rim / 2, style = Stroke(rim))
        drawCircle(Fondo, wheel * 0.55f)
        drawCircle(Dorado, wheel * 0.55f, style = Stroke(rim * 0.5f))
        drawCircle(Dorado, wheel * 0.16f)
        drawCircle(Texto, rim * 0.55f, Offset(center.x, center.y - wheel * 0.78f))
    }
}

/** "Palace" en blanco y "Tec" en dorado. */
@Composable
fun PalaceTecWordmark(modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.headlineMedium) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Texto)) { append("Palace") }
            withStyle(SpanStyle(color = Dorado)) { append("Tec") }
        },
        style = style,
        modifier = modifier,
    )
}

private const val SEGMENTS = 19
