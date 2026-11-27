package com.tecsup.palacetec.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CasinoColors = darkColorScheme(
    primary = Verde,
    onPrimary = Fondo,
    primaryContainer = VerdeOscuro,
    onPrimaryContainer = VerdeClaro,
    secondary = Dorado,
    onSecondary = Fondo,
    secondaryContainer = DoradoOscuro,
    onSecondaryContainer = DoradoClaro,
    tertiary = Rojo,
    onTertiary = Texto,
    tertiaryContainer = RojoOscuro,
    onTertiaryContainer = RojoPerdida,
    background = Fondo,
    onBackground = Texto,
    surface = Fondo,
    onSurface = Texto,
    surfaceVariant = SuperficieAlta,
    onSurfaceVariant = TextoTenue,
    surfaceContainerLowest = Fondo,
    surfaceContainerLow = SuperficieBaja,
    surfaceContainer = Superficie,
    surfaceContainerHigh = SuperficieAlta,
    surfaceContainerHighest = SuperficieMaxima,
    surfaceBright = SuperficieMaxima,
    surfaceDim = Fondo,
    inverseSurface = Texto,
    inverseOnSurface = Fondo,
    outline = Borde,
    outlineVariant = Borde,
    error = RojoPerdida,
    onError = Fondo,
    errorContainer = RojoOscuro,
    onErrorContainer = RojoPerdida,
)

private val CasinoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Siempre oscuro: es la identidad del casino. */
@Composable
fun PalaceTecTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CasinoColors, typography = Typography, shapes = CasinoShapes, content = content)
}
