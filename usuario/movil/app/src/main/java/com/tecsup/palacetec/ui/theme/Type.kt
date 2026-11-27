package com.tecsup.palacetec.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val Base = Typography()

/** Títulos con más peso, como en los lobbies de casino; el cuerpo queda igual para que se lea bien. */
val Typography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.ExtraBold),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
)
