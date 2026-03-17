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
    primary = Cyan80,
    onPrimary = CyanDark,
    secondary = Blue80,
    onSecondary = BlueDark,
    tertiary = Amber80,
    onTertiary = AmberDark,
    error = Red80,
    onError = RedDark,
    background = Grey10,
    onBackground = GreyE0,
    surface = Grey20,
    onSurface = GreyE0,
)

private val LightColorScheme = lightColorScheme(
    primary = Cyan40,
    onPrimary = PureWhite,
    secondary = Blue40,
    onSecondary = PureWhite,
    tertiary = Amber40,
    onTertiary = PureWhite,
    error = Red40,
    onError = PureWhite,
    background = Grey95,
    onBackground = Grey10,
    surface = Grey90,
    onSurface = Grey10,
)

private val HighContrastDarkColorScheme = darkColorScheme(
    primary = HcCyan,
    onPrimary = PureBlack,
    secondary = HcBlue,
    onSecondary = PureBlack,
    tertiary = HcAmber,
    onTertiary = PureBlack,
    error = HcRed,
    onError = PureBlack,
    background = PureBlack,
    onBackground = PureWhite,
    surface = HcSurface,
    onSurface = PureWhite,
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
