package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class VortexThemeMode {
    DARK,
    LIGHT,
    DYNAMIC
}

@Immutable
data class VortexExtraColors(
    val isDark: Boolean,
    val glassSurface: Color,
    val glassBorder: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
    val success: Color,
    val warning: Color,
    val gradientStart: Color,
    val gradientMid: Color,
    val gradientEnd: Color
)

val LocalVortexColors = staticCompositionLocalOf {
    VortexExtraColors(
        isDark = true,
        glassSurface = Color(0xCC13172E),
        glassBorder = MidnightCardBorder,
        glowPrimary = ElectricPurple,
        glowSecondary = VortexCyan,
        success = SuccessEmerald,
        warning = WarningAmber,
        gradientStart = ElectricPurple,
        gradientMid = NeonBlue,
        gradientEnd = VortexCyan
    )
}

private val VortexDarkColorScheme = darkColorScheme(
    primary = ElectricPurple,
    onPrimary = Color.White,
    primaryContainer = ElectricPurpleDeep,
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = NeonBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = VortexCyan,
    onTertiary = Color(0xFF001F24),
    tertiaryContainer = VortexCyanDeep,
    onTertiaryContainer = Color(0xFFCFFAFE),
    background = MidnightBlack,
    onBackground = TextPrimaryDark,
    surface = MidnightSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = MidnightSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF2E3556),
    error = ErrorCoral,
    onError = Color.White
)

private val VortexLightColorScheme = lightColorScheme(
    primary = ElectricPurpleDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = NeonBlueDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = VortexCyanDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFFAFE),
    onTertiaryContainer = Color(0xFF083344),
    background = PremiumWhite,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFE11D48),
    onError = Color.White
)

val VortexShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun VortexTheme(
    themeMode: VortexThemeMode = VortexThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        VortexThemeMode.DARK -> true
        VortexThemeMode.LIGHT -> false
        VortexThemeMode.DYNAMIC -> systemDark
    }

    val colorScheme = when {
        themeMode == VortexThemeMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> VortexDarkColorScheme
        else -> VortexLightColorScheme
    }

    val extraColors = if (useDark) {
        VortexExtraColors(
            isDark = true,
            glassSurface = Color(0xB8141830),
            glassBorder = MidnightCardBorder,
            glowPrimary = colorScheme.primary,
            glowSecondary = colorScheme.tertiary,
            success = SuccessEmerald,
            warning = WarningAmber,
            gradientStart = colorScheme.primary,
            gradientMid = colorScheme.secondary,
            gradientEnd = colorScheme.tertiary
        )
    } else {
        VortexExtraColors(
            isDark = false,
            glassSurface = Color(0xE6FFFFFF),
            glassBorder = LightCardBorder,
            glowPrimary = colorScheme.primary,
            glowSecondary = colorScheme.tertiary,
            success = SuccessEmerald,
            warning = WarningAmber,
            gradientStart = colorScheme.primary,
            gradientMid = colorScheme.secondary,
            gradientEnd = colorScheme.tertiary
        )
    }

    CompositionLocalProvider(LocalVortexColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VortexTypography,
            shapes = VortexShapes,
            content = content
        )
    }
}
