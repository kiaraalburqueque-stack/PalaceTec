package com.tecsup.palacetec.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.tecsup.palacetec.R

/** Los seis avatares de la web: la misma silueta sobre un color. */
val PresetAvatars: Map<String, Color> = listOf(
    0xFF7950B0, 0xFF176984, 0xFFB03D52, 0xFF956600, 0xFF196B42, 0xFF3952A4,
).mapIndexed { index, color -> "/avatars/avatar-${index + 1}.svg" to Color(color) }.toMap()

/** Sin foto, o mientras se descarga, se ve la silueta. */
@Composable
fun Avatar(avatarUrl: String, photoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(PresetAvatars[avatarUrl] ?: MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(
            painterResource(R.drawable.ic_person), contentDescription = null, tint = Color.White,
            modifier = Modifier.fillMaxSize().padding(size / 8),
        )
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
