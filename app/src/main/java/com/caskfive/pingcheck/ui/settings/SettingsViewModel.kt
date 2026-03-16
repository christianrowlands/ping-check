package com.caskfive.pingcheck.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caskfive.pingcheck.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsScreenState(
    val themeMode: String = "system",
    val defaultCount: Int = 4,
    val defaultInterval: Float = 1.0f,
    val defaultPacketSize: Int = 56,
    val defaultTimeout: Int = 10,
    val historyRetentionDays: Int = 30,
    val publicIpEnabled: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsScreenState())
    val state: StateFlow<SettingsScreenState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.preferences.collectLatest { prefs ->
                _state.update {
                    it.copy(
                        themeMode = prefs.themeMode,
                        defaultCount = prefs.defaultCount,
                        defaultInterval = prefs.defaultInterval,
                        defaultPacketSize = prefs.defaultPacketSize,
                        defaultTimeout = prefs.defaultTimeout,
                        historyRetentionDays = prefs.historyRetentionDays,
                        publicIpEnabled = prefs.publicIpEnabled,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesManager.updateThemeMode(mode)
        }
    }

    fun updateDefaultCount(count: Int) {
        viewModelScope.launch {
            preferencesManager.updateDefaultCount(count)
        }
    }

    fun updateDefaultInterval(interval: Float) {
        viewModelScope.launch {
            preferencesManager.updateDefaultInterval(interval)
        }
    }

    fun updateDefaultPacketSize(size: Int) {
        viewModelScope.launch {
            preferencesManager.updateDefaultPacketSize(size)
        }
    }

    fun updateDefaultTimeout(timeout: Int) {
        viewModelScope.launch {
            preferencesManager.updateDefaultTimeout(timeout)
        }
    }

    fun updateHistoryRetentionDays(days: Int) {
        viewModelScope.launch {
            preferencesManager.updateHistoryRetentionDays(days)
        }
    }

    fun updatePublicIpEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.updatePublicIpEnabled(enabled)
        }
    }
}
