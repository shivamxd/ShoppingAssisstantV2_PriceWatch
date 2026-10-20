package com.example.shoppingassisstantv2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE0E7FF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF1E1B4B),
    secondary = BrandMint,
    onSecondary = Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFCCFBF1),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF042F2E),
    tertiary = BrandCoral,
    onTertiary = Color.White,
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFEDD5),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF431407),
    error = androidx.compose.ui.graphics.Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF410002),
    background = androidx.compose.ui.graphics.Color(0xFFFAF9FF),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1B1B22),
    surface = androidx.compose.ui.graphics.Color(0xFFFAF9FF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1B1B22),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE4E1EC),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF47464F),
    outline = androidx.compose.ui.graphics.Color(0xFF787680),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFC8C5D0),
    inverseSurface = androidx.compose.ui.graphics.Color(0xFF303038),
    inverseOnSurface = androidx.compose.ui.graphics.Color(0xFFF3F0F7),
    inversePrimary = androidx.compose.ui.graphics.Color(0xFFC3C0FF),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF4F2FA),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFEEECF5),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFE8E6EF),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFE2E0E9),
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFC3C0FF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF2A1A87),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF4233B8),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE4E0FF),
    secondary = androidx.compose.ui.graphics.Color(0xFF5EEAD4),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF00382F),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF005145),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF9CF5E6),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFFB59B),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF5C1900),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF7F2C0A),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDBCF),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    onError = androidx.compose.ui.graphics.Color(0xFF690005),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF93000A),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    background = androidx.compose.ui.graphics.Color(0xFF111016),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE7E3ED),
    surface = androidx.compose.ui.graphics.Color(0xFF111016),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE7E3ED),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF47464F),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC9C5D0),
    outline = androidx.compose.ui.graphics.Color(0xFF928F99),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF47464F),
    inverseSurface = androidx.compose.ui.graphics.Color(0xFFE7E3ED),
    inverseOnSurface = androidx.compose.ui.graphics.Color(0xFF303038),
    inversePrimary = BrandIndigo,
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF0C0B10),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF19171F),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF1D1B23),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF27252D),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF323039),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PriceWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
