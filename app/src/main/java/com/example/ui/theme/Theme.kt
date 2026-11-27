package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

@Immutable
data class AegisSemanticColors(
    val emergency: Color,
    val emergencyContainer: Color,
    val onDevice: Color,
    val onDeviceContainer: Color,
    val cloud: Color,
    val cloudContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val cardBorder: Color,
    val elevatedSurface: Color
)

private val DarkSemanticColors = AegisSemanticColors(
    emergency = EmergencyCoral,
    emergencyContainer = EmergencyCoralDarkContainer,
    onDevice = OnDeviceEmerald,
    onDeviceContainer = OnDeviceEmeraldDarkContainer,
    cloud = CloudAzure,
    cloudContainer = CloudAzureDarkContainer,
    warning = OfflineAmber,
    warningContainer = OfflineAmberDarkContainer,
    cardBorder = DarkBorder,
    elevatedSurface = DarkSurfaceElevated
)

private val LightSemanticColors = AegisSemanticColors(
    emergency = EmergencyCoral,
    emergencyContainer = EmergencyCoralLightContainer,
    onDevice = OnDeviceEmerald,
    onDeviceContainer = OnDeviceEmeraldLightContainer,
    cloud = CloudAzure,
    cloudContainer = CloudAzureLightContainer,
    warning = OfflineAmber,
    warningContainer = OfflineAmberLightContainer,
    cardBorder = LightBorder,
    elevatedSurface = LightSurfaceElevated
)

val LocalAegisColors = staticCompositionLocalOf { DarkSemanticColors }

private val DarkColorScheme = darkColorScheme(
    primary = CloudAzure,
    onPrimary = Color.White,
    primaryContainer = CloudAzureDarkContainer,
    onPrimaryContainer = Color(0xFFBFDBFE),
    secondary = OnDeviceEmerald,
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = OnDeviceEmeraldDarkContainer,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = OfflineAmber,
    onTertiary = Color(0xFF271A00),
    tertiaryContainer = OfflineAmberDarkContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = EmergencyCoral,
    onError = Color.White,
    errorContainer = EmergencyCoralDarkContainer,
    onErrorContainer = Color(0xFFFECACA),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkBorder,
    outlineVariant = Color(0xFF21262D)
)

private val LightColorScheme = lightColorScheme(
    primary = CloudAzure,
    onPrimary = Color.White,
    primaryContainer = CloudAzureLightContainer,
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = OnDeviceEmerald,
    onSecondary = Color.White,
    secondaryContainer = OnDeviceEmeraldLightContainer,
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = OfflineAmber,
    onTertiary = Color.White,
    tertiaryContainer = OfflineAmberLightContainer,
    onTertiaryContainer = Color(0xFF78350F),
    error = EmergencyCoral,
    onError = Color.White,
    errorContainer = EmergencyCoralLightContainer,
    onErrorContainer = Color(0xFF991B1B),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightBorder,
    outlineVariant = Color(0xFFE5E7EB)
)

val AegisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AegisPulseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalAegisColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AegisShapes,
            content = content
        )
    }
}

object AegisTheme {
    val colors: AegisSemanticColors
        @Composable
        get() = LocalAegisColors.current
}
