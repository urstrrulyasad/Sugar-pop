package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

private val DarkColorScheme = darkColorScheme(
    primary = ChampagneGold,
    onPrimary = ObsidianBg,
    primaryContainer = ChampagneContainer,
    onPrimaryContainer = OnChampagneContainer,
    secondary = DeepSteelBlue,
    onSecondary = ObsidianBg,
    secondaryContainer = SteelContainer,
    onSecondaryContainer = OnSteelContainer,
    tertiary = EmeraldSecure,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = SilverText,
    surface = ObsidianSurface,
    onSurface = SilverText,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    surfaceContainerLowest = Color(0xFF07090D),
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceVariant,
    surfaceContainerHigh = ObsidianCard,
    surfaceContainerHighest = Color(0xFF252D40),
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF22293A),
    error = CoralAlert,
    onError = ObsidianBg,
    errorContainer = CoralContainer,
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = LightGoldPrimary,
    onPrimary = Color.White,
    primaryContainer = LightGoldContainer,
    onPrimaryContainer = LightOnGoldContainer,
    secondary = Color(0xFF33558C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E5FF),
    onSecondaryContainer = Color(0xFF0A244D),
    tertiary = Color(0xFF15803D),
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMutedInk,
    surfaceContainerLow = Color(0xFFFDFCF9),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE5E1D8),
    outline = LightOutline,
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val VaultShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KeepsakeVaultTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VaultShapes,
        content = content
    )
}
