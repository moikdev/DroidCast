package dev.tinycast.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Adapted from Tinycast Theme.swift; provenance is in third_party/tinycast-palette.
object PaletteTheme {
    val background = Color(0xFF111111)
    val surface = Color(0xFF202020)
    val ink = Color.White
    val secondary = Color.White.copy(alpha = .60f)
    val tertiary = Color.White.copy(alpha = .40f)
    val selection = Color.White.copy(alpha = .10f)
    val hover = Color.White.copy(alpha = .05f)
    val control = Color.White.copy(alpha = .10f)
    val border = Color.White.copy(alpha = .20f)
    val separator = Color.White.copy(alpha = .10f)
    val accent = Color(0xFF7EA9FF)
    val width = 750.dp
    val height = 475.dp
    val radius = 26.dp
    val rowRadius = 10.dp
    val icon = 26.dp
    val header = 56.dp
    val footer = 52.dp
    val row = 44.dp
}
