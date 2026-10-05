package com.kirolos.todoapp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Immutable
class IosColors(
    val bg: Color, val card: Color, val label: Color, val secondary: Color,
    val separator: Color, val fill: Color, val segSelected: Color,
    val blue: Color, val red: Color, val orange: Color, val green: Color
)

val LightIos = IosColors(
    bg = Color(0xFFF2F2F7), card = Color.White, label = Color.Black, secondary = Color(0xFF8E8E93),
    separator = Color(0xFFD1D1D6), fill = Color(0xFFE3E3E8), segSelected = Color.White,
    blue = Color(0xFF007AFF), red = Color(0xFFFF3B30), orange = Color(0xFFFF9500), green = Color(0xFF34C759)
)

val DarkIos = IosColors(
    bg = Color.Black, card = Color(0xFF1C1C1E), label = Color.White, secondary = Color(0xFF98989F),
    separator = Color(0xFF38383A), fill = Color(0xFF2C2C2E), segSelected = Color(0xFF636366),
    blue = Color(0xFF0A84FF), red = Color(0xFFFF453A), orange = Color(0xFFFF9F0A), green = Color(0xFF30D158)
)

val LocalIos = staticCompositionLocalOf { LightIos }

@Composable
fun IosTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) DarkIos else LightIos
    val scheme = if (dark) darkColorScheme(primary = c.blue, background = c.bg, surface = c.card)
    else lightColorScheme(primary = c.blue, background = c.bg, surface = c.card)
    CompositionLocalProvider(
        LocalIos provides c,
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(colorScheme = scheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = c.bg, contentColor = c.label) {
                content()
            }
        }
    }
}
