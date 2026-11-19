package com.example.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun VortexDynamicBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val extra = LocalVortexColors.current
    val baseBg = MaterialTheme.colorScheme.background
    val transition = rememberInfiniteTransition(label = "vortex_backdrop")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backdrop_shift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
            .drawBehind {
                val alpha1 = if (extra.isDark) 0.20f else 0.10f
                val alpha2 = if (extra.isDark) 0.16f else 0.08f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(extra.gradientStart.copy(alpha = alpha1), Color.Transparent),
                        center = Offset(
                            x = size.width * (0.15f + 0.25f * shift),
                            y = size.height * 0.12f
                        ),
                        radius = size.maxDimension * 0.65f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(extra.gradientEnd.copy(alpha = alpha2), Color.Transparent),
                        center = Offset(
                            x = size.width * (0.88f - 0.20f * shift),
                            y = size.height * 0.65f
                        ),
                        radius = size.maxDimension * 0.60f
                    )
                )
            },
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    glowBorder: Boolean = false,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val extra = LocalVortexColors.current
    val borderBrush = if (glowBorder) {
        Brush.linearGradient(
            colors = listOf(
                extra.gradientStart.copy(alpha = 0.75f),
                extra.gradientMid.copy(alpha = 0.55f),
                extra.gradientEnd.copy(alpha = 0.75f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(extra.glassBorder, extra.glassBorder.copy(alpha = 0.05f))
        )
    }

    Box(
        modifier = modifier
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = shape,
                        ambientColor = extra.glowPrimary.copy(alpha = 0.25f),
                        spotColor = extra.glowSecondary.copy(alpha = 0.25f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(extra.glassSurface)
            .border(BorderStroke(1.dp, borderBrush), shape)
            .padding(contentPadding),
        content = content
    )
}

fun Modifier.shimmerSkeleton(cornerRadius: Dp = 12.dp): Modifier = composed {
    val extra = LocalVortexColors.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )
    val base = if (extra.isDark) Color(0xFF1B203D) else Color(0xFFE2E8F0)
    val highlight = if (extra.isDark) Color(0xFF2B325E) else Color(0xFFF8FAFC)
    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(
            brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(translate, 0f),
                end = Offset(translate + 260f, 200f)
            )
        )
}

@Composable
fun vortexPrimaryGradient(): Brush {
    val extra = LocalVortexColors.current
    return Brush.horizontalGradient(
        colors = listOf(extra.gradientStart, extra.gradientMid, extra.gradientEnd)
    )
}
