package com.moataz.edge.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val EdgeMint = Color(0xFF2AD4B7)
val EdgeGold = Color(0xFFDDBA68)
val EdgeInk = Color(0xFF071219)
val EdgeSurface = Color(0xFF0E1C26)
val EdgeSurfaceHigh = Color(0xFF142733)
val EdgeText = Color(0xFFEAF3F5)
val EdgeMuted = Color(0xFF9CB0B8)
val EdgeDanger = Color(0xFFFF7D7D)
val EdgeWarning = Color(0xFFFFC76B)

private val EdgeScheme = darkColorScheme(
    primary = EdgeMint,
    onPrimary = Color(0xFF00201B),
    secondary = EdgeGold,
    onSecondary = Color(0xFF261A00),
    background = EdgeInk,
    onBackground = EdgeText,
    surface = EdgeSurface,
    onSurface = EdgeText,
    surfaceVariant = EdgeSurfaceHigh,
    onSurfaceVariant = EdgeMuted,
    error = EdgeDanger,
    onError = Color(0xFF3B0000),
    outline = Color(0xFF36515D)
)

private val ArabicFirstTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 25.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
)

private val EdgeShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp)
)

@Composable
fun EdgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EdgeScheme, typography = ArabicFirstTypography, shapes = EdgeShapes, content = content)
}
