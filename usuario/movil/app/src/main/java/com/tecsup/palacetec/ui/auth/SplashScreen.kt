package com.tecsup.palacetec.ui.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tecsup.palacetec.ui.components.PalaceTecLogo
import com.tecsup.palacetec.ui.components.PalaceTecWordmark
import com.tecsup.palacetec.ui.theme.Fondo
import com.tecsup.palacetec.ui.theme.VerdeOscuro

/** Se ve mientras se lee la sesión guardada. */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "logo")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart),
        label = "giro",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(VerdeOscuro, Fondo), radius = 1_400f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PalaceTecLogo(120.dp, Modifier.rotate(angle))
            PalaceTecWordmark(style = MaterialTheme.typography.displaySmall)
            Text(
                "Casino en línea",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CircularProgressIndicator(Modifier.padding(top = 16.dp).size(28.dp), strokeWidth = 3.dp)
        }
        Text(
            "Solo mayores de 18 años · Créditos virtuales, sin dinero real",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
        )
    }
}
