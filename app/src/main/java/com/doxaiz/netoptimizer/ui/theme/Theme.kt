package com.doxaiz.netoptimizer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class DoxaizThemeKind { AURORA, CYBER, LIGHT }

// Кибер-тема: цвета объявлены ДО схем (порядок инициализации важен!)
private val CyberBg = Color(0xFF0A0F1E)
private val CyberCard = Color(0xFF0A0F1E)
private val CyberCard2 = Color(0xFF060B16)
private val CyberTxt = Color(0xFFF2F7FF)
private val CyberMut = Color(0xFFA9C0E4)
private val CyberLine = Color(0xFF16264A)

private val AuroraScheme = darkColorScheme(
    primary = AccentCyan,
    secondary = AccentViolet,
    tertiary = AccentGreen,
    background = DoxaizBg,
    surface = DoxaizCard,
    surfaceVariant = DoxaizCard,
    onBackground = DoxaizTxt,
    onSurface = DoxaizTxt,
    onSurfaceVariant = DoxaizMut2,
    outline = DoxaizLine
)

private val CyberScheme = darkColorScheme(
    primary = AccentCyan,
    secondary = AccentViolet,
    tertiary = AccentGreen,
    background = CyberBg,
    surface = CyberCard,
    surfaceVariant = CyberCard,
    onBackground = CyberTxt,
    onSurface = CyberTxt,
    onSurfaceVariant = CyberMut,
    outline = CyberLine
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0077CC),
    secondary = AccentViolet,
    tertiary = Color(0xFF00A35C),
    background = LightBg,
    surface = LightCard,
    surfaceVariant = LightCard2,
    onBackground = LightTxt,
    onSurface = LightTxt,
    onSurfaceVariant = LightMut,
    outline = LightLine
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Типографика: плотные заголовки с отрицательным трекингом, цифры — крупные и читаемые
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp)
)

@Composable
fun DoxaizTheme(kind: DoxaizThemeKind, content: @Composable () -> Unit) {
    val scheme = when (kind) {
        DoxaizThemeKind.AURORA -> AuroraScheme
        DoxaizThemeKind.CYBER -> CyberScheme
        DoxaizThemeKind.LIGHT -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, typography = AppTypography, content = content)
}
