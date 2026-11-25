package com.tecsup.palacetec.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CasinoColors = darkColorScheme(
    primary = Dorado,
    onPrimary = Negro,
    primaryContainer = DoradoOscuro,
    onPrimaryContainer = Dorado,
    secondary = Naranja,
    onSecondary = Negro,
    secondaryContainer = DoradoOscuro,
    onSecondaryContainer = Dorado,
    tertiary = Naranja,
    onTertiary = Negro,
    background = Negro,
    onBackground = Marfil,
    surface = Negro,
    onSurface = Marfil,
    surfaceVariant = NegroElevado,
    onSurfaceVariant = MarfilTenue,
    surfaceContainerLowest = Negro,
    surfaceContainerLow = NegroSuperficie,
    surfaceContainer = NegroSuperficie,
    surfaceContainerHigh = NegroElevado,
    surfaceContainerHighest = NegroElevado,
    outline = MarfilTenue,
    error = RojoPerdida,
)

/** Siempre oscuro: es la identidad del casino. */
@Composable
fun PalaceTecTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CasinoColors, typography = Typography, content = content)
}
