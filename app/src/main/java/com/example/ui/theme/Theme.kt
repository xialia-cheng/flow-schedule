package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Immutable
data class FlowExtendedColors(
    val isDark: Boolean,
    val isDynamicColor: Boolean,
    val liveAccent: Color,
    val upcomingAccent: Color,
    val completedAccent: Color,
    val deleteAccent: Color,
    val deleteContainer: Color,
    val secondaryAccent: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
    val fluidCapsuleBg: Color,
    val fluidCapsuleContent: Color,
    val fluidCapsuleBorder: Color,
    val cardElevated: Color,
    val cardSubtle: Color,
    val timelineSpine: Color,
    val cardShadowColor: Color,
)

object FlowMotion {
    fun <T> standardSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> playfulSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> quickSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}

private val GeminiDarkColorScheme = darkColorScheme(
    primary = GeminiBlue,
    onPrimary = OnGeminiBlue,
    primaryContainer = GeminiBlueContainer,
    onPrimaryContainer = OnGeminiBlueContainer,
    secondary = GeminiViolet,
    onSecondary = OnGeminiViolet,
    secondaryContainer = GeminiVioletContainer,
    onSecondaryContainer = OnGeminiVioletContainer,
    tertiary = GeminiAmber,
    onTertiary = OnGeminiAmber,
    tertiaryContainer = GeminiAmberContainer,
    onTertiaryContainer = OnGeminiAmberContainer,
    error = CoralDelete,
    onError = OnGeminiBlue,
    errorContainer = CoralDeleteContainer,
    onErrorContainer = OnCoralDeleteContainer,
    background = GeminiObsidianBg,
    onBackground = DarkTextPrimary,
    surface = GeminiSurfaceLow,
    onSurface = DarkTextPrimary,
    surfaceVariant = GeminiSurface,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = GeminiSurfaceLowest,
    surfaceContainerLow = GeminiSurfaceLow,
    surfaceContainer = GeminiSurface,
    surfaceContainerHigh = GeminiSurfaceHigh,
    surfaceContainerHighest = GeminiSurfaceHighest,
    outline = GeminiOutline,
    outlineVariant = GeminiOutlineSubtle,
)

private val GeminiLightColorScheme = lightColorScheme(
    primary = GeminiLightPrimary,
    onPrimary = OnGeminiLightPrimary,
    primaryContainer = GeminiLightPrimaryContainer,
    onPrimaryContainer = OnGeminiLightPrimaryContainer,
    secondary = GeminiLightSecondary,
    onSecondary = OnGeminiLightSecondary,
    secondaryContainer = GeminiLightSecondaryContainer,
    onSecondaryContainer = OnGeminiLightSecondaryContainer,
    tertiary = LightAmber,
    onTertiary = OnLightAmber,
    tertiaryContainer = LightAmberContainer,
    onTertiaryContainer = OnLightAmberContainer,
    error = LightCoral,
    onError = OnGeminiLightPrimary,
    errorContainer = LightCoralContainer,
    onErrorContainer = OnLightCoralContainer,
    background = GeminiLightBg,
    onBackground = LightTextPrimary,
    surface = GeminiLightBg,
    onSurface = LightTextPrimary,
    surfaceVariant = GeminiLightSurface,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = GeminiLightSurfaceLowest,
    surfaceContainerLow = GeminiLightSurfaceLow,
    surfaceContainer = GeminiLightSurface,
    surfaceContainerHigh = GeminiLightSurfaceHigh,
    surfaceContainerHighest = GeminiLightSurfaceHighest,
    outline = GeminiLightOutline,
    outlineVariant = GeminiLightOutlineSubtle,
)

private fun buildExtendedColors(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
    isDynamic: Boolean,
): FlowExtendedColors {
    return if (darkTheme) {
        FlowExtendedColors(
            isDark = true,
            isDynamicColor = isDynamic,
            liveAccent = if (isDynamic) colorScheme.primary else GeminiGlowCyan,
            upcomingAccent = if (isDynamic) colorScheme.tertiary else GeminiAmber,
            completedAccent = EmeraldDone,
            deleteAccent = colorScheme.error,
            deleteContainer = colorScheme.errorContainer,
            secondaryAccent = colorScheme.secondary,
            glowPrimary = colorScheme.primary,
            glowSecondary = colorScheme.secondary,
            fluidCapsuleBg = colorScheme.surfaceContainerLowest,
            fluidCapsuleContent = colorScheme.onSurface,
            fluidCapsuleBorder = colorScheme.primary.copy(alpha = 0.42f),
            cardElevated = colorScheme.surfaceContainer,
            cardSubtle = colorScheme.surfaceContainerLow,
            timelineSpine = colorScheme.outlineVariant,
            cardShadowColor = Color.Transparent,
        )
    } else {
        FlowExtendedColors(
            isDark = false,
            isDynamicColor = isDynamic,
            liveAccent = if (isDynamic) colorScheme.primary else LightTeal,
            upcomingAccent = if (isDynamic) colorScheme.tertiary else LightAmber,
            completedAccent = LightEmerald,
            deleteAccent = colorScheme.error,
            deleteContainer = colorScheme.errorContainer,
            secondaryAccent = colorScheme.secondary,
            glowPrimary = colorScheme.primary,
            glowSecondary = colorScheme.secondary,
            fluidCapsuleBg = FluidCapsuleSurface,
            fluidCapsuleContent = FluidCapsuleText,
            fluidCapsuleBorder = colorScheme.primary.copy(alpha = 0.45f),
            cardElevated = colorScheme.surfaceContainerLowest,
            cardSubtle = colorScheme.surfaceContainerLow,
            timelineSpine = colorScheme.outlineVariant,
            cardShadowColor = Color(0x10142038),
        )
    }
}

private val DefaultDarkExtendedColors = buildExtendedColors(
    colorScheme = GeminiDarkColorScheme,
    darkTheme = true,
    isDynamic = false,
)

private val LocalFlowExtendedColors = staticCompositionLocalOf { DefaultDarkExtendedColors }

// Generous Material 3 Gemini-inspired high-corner-radius geometry
val FlowShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

object FlowTheme {
    val colors: FlowExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFlowExtendedColors.current
}

@Composable
fun FlowScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamicNow = dynamicColor && supportsDynamic

    val colorScheme = when {
        useDynamicNow && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicNow && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> GeminiDarkColorScheme
        else -> GeminiLightColorScheme
    }

    val extendedColors = buildExtendedColors(
        colorScheme = colorScheme,
        darkTheme = darkTheme,
        isDynamic = useDynamicNow,
    )

    CompositionLocalProvider(LocalFlowExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = FlowShapes,
            content = content,
        )
    }
}
