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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val NiraxDarkColorScheme = darkColorScheme(
    primary = IndigoDarkPrimary,
    onPrimary = Color(0xFF0A154A),
    primaryContainer = IndigoDarkPrimaryContainer,
    onPrimaryContainer = IndigoDarkOnPrimaryContainer,
    secondary = CoralDarkAccent,
    onSecondary = Color(0xFF4A0614),
    secondaryContainer = CoralDarkContainer,
    onSecondaryContainer = CoralDarkOnContainer,
    tertiary = TealDarkTertiary,
    onTertiary = Color(0xFF042D42),
    tertiaryContainer = TealDarkContainer,
    onTertiaryContainer = TealDarkOnContainer,
    background = ObsidianBg,
    onBackground = InkLight,
    surface = ObsidianSurface,
    onSurface = InkLight,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = InkMutedDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF1F2840)
)

private val NiraxLightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoPrimaryContainer,
    onPrimaryContainer = IndigoOnPrimaryContainer,
    secondary = CoralAccent,
    onSecondary = Color.White,
    secondaryContainer = CoralContainer,
    onSecondaryContainer = CoralOnContainer,
    tertiary = TealTertiary,
    onTertiary = Color.White,
    tertiaryContainer = TealContainer,
    onTertiaryContainer = TealOnContainer,
    background = AlabasterBg,
    onBackground = InkPrimary,
    surface = SurfaceWhite,
    onSurface = InkPrimary,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = InkSecondary,
    outline = OutlineLight,
    outlineVariant = Color(0xFFE7ECF6)
)

val NiraxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NiraxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> NiraxDarkColorScheme
        else -> NiraxLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NiraxTypography,
        shapes = NiraxShapes,
        content = content
    )
}
