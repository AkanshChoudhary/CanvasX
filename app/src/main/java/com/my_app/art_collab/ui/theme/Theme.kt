package com.my_app.art_collab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary                = Indigo60,
    onPrimary              = Color.White,
    primaryContainer       = Indigo20,
    onPrimaryContainer     = Indigo80,

    secondary              = Rose60,
    onSecondary            = Rose10,
    secondaryContainer     = Rose20,
    onSecondaryContainer   = Rose80,

    tertiary               = Teal60,
    onTertiary             = Teal10,
    tertiaryContainer      = Teal20,
    onTertiaryContainer    = Teal80,

    error                  = Color(0xFFFFB4AB),
    onError                = Color(0xFF690005),
    errorContainer         = Color(0xFF93000A),
    onErrorContainer       = Color(0xFFFFDAD6),

    background             = DarkBackground,
    onBackground           = OnDark,

    surface                = DarkSurface,
    onSurface              = OnDark,
    surfaceVariant         = DarkSurface2,
    onSurfaceVariant       = Color(0xFF9D96BB),

    surfaceContainer       = DarkSurface2,
    surfaceContainerLow    = DarkSurface,
    surfaceContainerHigh   = DarkSurface3,

    outline                = Outline,
    outlineVariant         = OutlineVariant,
    scrim                  = Color.Black,
    inverseSurface         = OnDark,
    inverseOnSurface       = DarkSurface,
    inversePrimary         = Indigo40,
)

private val LightColorScheme = lightColorScheme(
    primary                = Indigo40,
    onPrimary              = Color.White,
    primaryContainer       = Indigo90,
    onPrimaryContainer     = Indigo10,

    secondary              = Rose40,
    onSecondary            = Color.White,
    secondaryContainer     = Rose90,
    onSecondaryContainer   = Rose10,

    tertiary               = Teal40,
    onTertiary             = Color.White,
    tertiaryContainer      = Teal90,
    onTertiaryContainer    = Teal10,

    error                  = Color(0xFFBA1A1A),
    onError                = Color.White,
    errorContainer         = Color(0xFFFFDAD6),
    onErrorContainer       = Color(0xFF410002),

    background             = LightBackground,        // near-pure white
    onBackground           = OnLight,

    surface                = LightSurface,           // pure white
    onSurface              = OnLight,
    surfaceVariant         = LightSurfaceVariant,    // strong lavender — visible contrast
    onSurfaceVariant       = Color(0xFF4A4560),

    surfaceContainer       = LightSurfaceContainer,       // soft lavender cards
    surfaceContainerLow    = LightSurface,
    surfaceContainerHigh   = LightSurfaceContainerHigh,   // elevated items

    outline                = OutlineLight,
    outlineVariant         = OutlineVariantLight,
    scrim                  = Color.Black,
    inverseSurface         = OnLight,
    inverseOnSurface       = LightSurface,
    inversePrimary         = Indigo60,
)

@Composable
fun CanvasXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
