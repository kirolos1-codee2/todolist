package com.kirolos.todoapp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** ألوان وأسطح تصميم الزجاج (Liquid Glass) */
@Immutable
class GlassColors(
    val dark: Boolean,
    val bgTop: Color, val bgBottom: Color, val blobs: List<Color>,
    val label: Color, val secondary: Color, val separator: Color,
    val fill: Brush,        // سطح زجاجي عادي
    val strongFill: Brush,  // زجاج أكتر كثافة (للعناصر العايمة)
    val sheetFill: Brush,   // خلفية الشيت
    val edge: Brush,        // حافة لامعة (specular)
    val control: Color, val controlSelected: Color,
    val blue: Color, val blueLight: Color, val red: Color, val orange: Color, val green: Color
)

val LightGlass = GlassColors(
    dark = false,
    bgTop = Color(0xFFE8EEFF), bgBottom = Color(0xFFFCEEF6),
    blobs = listOf(Color(0xFF5AA2FF), Color(0xFFB07CFF), Color(0xFFFF8AB3), Color(0xFF5EE0C6)),
    label = Color(0xFF14141A), secondary = Color(0x993C3C43), separator = Color(0x263C3C43),
    fill = Brush.verticalGradient(listOf(Color(0xB8FFFFFF), Color(0x8CFFFFFF))),
    strongFill = Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0xCCFFFFFF))),
    sheetFill = Brush.verticalGradient(listOf(Color(0xF7FFFFFF), Color(0xF4F1F1FA))),
    edge = Brush.linearGradient(listOf(Color(0xF2FFFFFF), Color(0x26FFFFFF), Color(0x1A5A5A78), Color(0xB3FFFFFF))),
    control = Color(0x143C3C43), controlSelected = Color(0xF2FFFFFF),
    blue = Color(0xFF007AFF), blueLight = Color(0xFF5AC8FA),
    red = Color(0xFFFF3B30), orange = Color(0xFFFF9500), green = Color(0xFF34C759)
)

val DarkGlass = GlassColors(
    dark = true,
    bgTop = Color(0xFF0B0B1E), bgBottom = Color(0xFF150A24),
    blobs = listOf(Color(0xFF3B6BFF), Color(0xFF8B3DFF), Color(0xFFFF3D8B), Color(0xFF12C2A9)),
    label = Color.White, secondary = Color(0x99EBEBF5), separator = Color(0x26FFFFFF),
    fill = Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x14FFFFFF))),
    strongFill = Brush.verticalGradient(listOf(Color(0xE61E1E32), Color(0xD9181826))),
    sheetFill = Brush.verticalGradient(listOf(Color(0xF2262640), Color(0xF51A1A2C))),
    edge = Brush.linearGradient(listOf(Color(0x8CFFFFFF), Color(0x14FFFFFF), Color(0x0DFFFFFF), Color(0x4DFFFFFF))),
    control = Color(0x1FFFFFFF), controlSelected = Color(0x40FFFFFF),
    blue = Color(0xFF0A84FF), blueLight = Color(0xFF64D2FF),
    red = Color(0xFFFF453A), orange = Color(0xFFFF9F0A), green = Color(0xFF30D158)
)

val LocalGlass = staticCompositionLocalOf { LightGlass }

fun Color.lighten(fraction: Float): Color = lerp(this, Color.White, fraction)

/** سطح زجاجي: تعبئة شفافة + حافة لامعة */
@Composable
fun Modifier.glass(shape: Shape, strong: Boolean = false): Modifier {
    val g = LocalGlass.current
    return this
        .clip(shape)
        .background(if (strong) g.strongFill else g.fill)
        .border(1.dp, g.edge, shape)
}

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val g = if (dark) DarkGlass else LightGlass
    val scheme = if (dark) darkColorScheme(primary = g.blue, background = g.bgTop, surface = g.bgTop)
    else lightColorScheme(primary = g.blue, background = g.bgTop, surface = g.bgTop)
    CompositionLocalProvider(
        LocalGlass provides g,
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(colorScheme = scheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = g.bgTop, contentColor = g.label) {
                Box(Modifier.fillMaxSize()) {
                    AuroraBackground(g)
                    content()
                }
            }
        }
    }
}

/** خلفية ملوّنة بتتحرك ببطء — الزجاج بيبان حلو فوقها */
@Composable
private fun AuroraBackground(g: GlassColors) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(36000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(g.bgTop, g.bgBottom)))
        val a = phase * 2f * PI.toFloat()
        val w = size.width
        val h = size.height
        val alpha = if (g.dark) 0.55f else 0.50f

        fun blob(color: Color, cx: Float, cy: Float, r: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
        }
        blob(g.blobs[0], w * (0.10f + 0.10f * sin(a)), h * (0.10f + 0.05f * cos(a)), w * 0.90f)
        blob(g.blobs[1], w * (0.95f + 0.08f * cos(a)), h * (0.35f + 0.06f * sin(a)), w * 0.80f)
        blob(g.blobs[2], w * (0.20f + 0.10f * cos(a + 1f)), h * (0.70f + 0.05f * sin(a + 1f)), w * 0.85f)
        blob(g.blobs[3], w * (0.85f + 0.08f * sin(a + 2f)), h * (0.95f + 0.04f * cos(a)), w * 0.75f)
    }
}
