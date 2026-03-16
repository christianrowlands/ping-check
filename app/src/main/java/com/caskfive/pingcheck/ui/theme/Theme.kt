package com.caskfive.pingcheck.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    secondary = Blue80,
    tertiary = Orange80,
    error = Red80,
    background = Grey10,
    surface = Grey20,
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    secondary = Blue40,
    tertiary = Orange40,
    error = Red40,
    background = Grey95,
    surface = Grey90,
)

private val HighContrastDarkColorScheme = darkColorScheme(
    primary = HcGreen,
    secondary = HcBlue,
    tertiary = HcOrange,
    error = HcRed,
    background = PureBlack,
    surface = HcSurface,
    onBackground = PureWhite,
    onSurface = PureWhite,
    onPrimary = PureBlack,
    onSecondary = PureBlack,
    onError = PureBlack,
)

@Composable
fun PingCheckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        highContrast -> HighContrastDarkColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
