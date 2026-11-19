package com.droid.bootchart.presentation

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.droid.bootchart.resources.Res
import com.droid.bootchart.resources.inter_bold
import com.droid.bootchart.resources.inter_medium
import com.droid.bootchart.resources.inter_regular
import com.droid.bootchart.resources.inter_semibold
import com.droid.bootchart.resources.jetbrains_mono_medium
import com.droid.bootchart.resources.jetbrains_mono_regular
import com.droid.bootchart.resources.jetbrains_mono_semibold
import org.jetbrains.compose.resources.Font

/** Extra semantic colors for charts that Material roles do not cover. */
internal data class ChartColors(
    val cpu: Color,
    val diskRead: Color,
    val diskWrite: Color,
    val bar: Color,
    val grid: Color,
)

private val DarkChart = ChartColors(
    cpu = Color(0xFF93CCFF), diskRead = Color(0xFF4EDEA3), diskWrite = Color(0xFFFFB4AB),
    bar = Color(0xFF93CCFF), grid = Color(0xFF2D3449),
)
private val LightChart = ChartColors(
    cpu = Color(0xFF006494), diskRead = Color(0xFF00855A), diskWrite = Color(0xFFBA1A1A),
    bar = Color(0xFF006494), grid = Color(0xFFD5DAE5),
)

// Dark tokens come from requirements/DESIGN.md; the light palette is an assumption pending design input.
private val DarkColors = darkColorScheme(
    background = Color(0xFF0B1326), surface = Color(0xFF0B1326),
    surfaceContainer = Color(0xFF171F33), surfaceContainerHigh = Color(0xFF222A3D),
    surfaceContainerLow = Color(0xFF131B2E), surfaceContainerLowest = Color(0xFF060E20),
    surfaceContainerHighest = Color(0xFF2D3449),
    onSurface = Color(0xFFDAE2FD), onSurfaceVariant = Color(0xFFBFC7D2),
    primary = Color(0xFF93CCFF), onPrimary = Color(0xFF003351),
    primaryContainer = Color(0xFF3198DC), onPrimaryContainer = Color(0xFF002C47),
    secondary = Color(0xFF4EDEA3), onSecondary = Color(0xFF003824), secondaryContainer = Color(0xFF00A572),
    tertiary = Color(0xFFD0BCFF), onTertiary = Color(0xFF3C0091), tertiaryContainer = Color(0xFFA078FF),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF89929B), outlineVariant = Color(0xFF3F4850),
)
private val LightColors = lightColorScheme(
    background = Color(0xFFF7F9FF), surface = Color(0xFFF7F9FF),
    surfaceContainer = Color(0xFFEBEEF7), surfaceContainerHigh = Color(0xFFE1E5F0),
    surfaceContainerLow = Color(0xFFF0F3FA), surfaceContainerLowest = Color.White,
    surfaceContainerHighest = Color(0xFFD5DAE5),
    onSurface = Color(0xFF151B2B), onSurfaceVariant = Color(0xFF414750),
    primary = Color(0xFF006494), onPrimary = Color.White,
    secondary = Color(0xFF00855A), onSecondary = Color.White, tertiary = Color(0xFF6B4FBB), onTertiary = Color.White,
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outlineVariant = Color(0xFFC1C7D2),
)

private val LocalTelemetryFont = androidx.compose.runtime.staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

/** Locally bundled JetBrains Mono for timestamps, counters and process identities. */
internal val TelemetryStyle: TextStyle
    @Composable get() = TextStyle(
        fontFamily = LocalTelemetryFont.current, fontSize = 11.sp,
        lineHeight = 16.sp, fontWeight = FontWeight.Normal,
    )

/** Label style for section captions. */
internal val CaptionStyle: TextStyle
    @Composable get() = MaterialTheme.typography.labelSmall

internal val LocalChartColors = androidx.compose.runtime.staticCompositionLocalOf { DarkChart }

@Composable
internal fun DroidChartTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) DarkColors else LightColors
    val inter = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
    )
    val mono = FontFamily(
        Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(Res.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    )
    val typography = Typography(
        headlineSmall = TextStyle(fontFamily = inter, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium = TextStyle(fontFamily = inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = inter, fontSize = 13.sp, lineHeight = 20.sp),
        bodyMedium = TextStyle(fontFamily = inter, fontSize = 13.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = inter, fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelMedium = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
    )
    androidx.compose.runtime.CompositionLocalProvider(
        LocalChartColors provides if (dark) DarkChart else LightChart,
        LocalTelemetryFont provides mono,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
