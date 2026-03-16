package com.caskfive.pingcheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.caskfive.pingcheck.data.preferences.PreferencesManager
import com.caskfive.pingcheck.ui.PingCheckNavHost
import com.caskfive.pingcheck.ui.theme.PingCheckTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by preferencesManager.preferences.collectAsState(
                initial = com.caskfive.pingcheck.data.preferences.AppPreferences()
            )
            val darkTheme = when (prefs.themeMode) {
                "light" -> false
                "dark" -> true
                "high_contrast" -> true
                else -> isSystemInDarkTheme()
            }
            val highContrast = prefs.themeMode == "high_contrast"

            PingCheckTheme(darkTheme = darkTheme, highContrast = highContrast) {
                PingCheckNavHost()
            }
        }
    }
}
