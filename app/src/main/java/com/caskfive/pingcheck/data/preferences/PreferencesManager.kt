package com.caskfive.pingcheck.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppPreferences(
    val defaultCount: Int = 4,
    val defaultInterval: Float = 1.0f,
    val defaultPacketSize: Int = 56,
    val defaultTimeout: Int = 10,
    val themeMode: String = "system",
    val historyRetentionDays: Int = 30,
    val publicIpEnabled: Boolean = false,
)

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DEFAULT_COUNT = intPreferencesKey("default_count")
        val DEFAULT_INTERVAL = floatPreferencesKey("default_interval")
        val DEFAULT_PACKET_SIZE = intPreferencesKey("default_packet_size")
        val DEFAULT_TIMEOUT = intPreferencesKey("default_timeout")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HISTORY_RETENTION_DAYS = intPreferencesKey("history_retention_days")
        val PUBLIC_IP_ENABLED = booleanPreferencesKey("public_ip_enabled")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            defaultCount = prefs[Keys.DEFAULT_COUNT] ?: 4,
            defaultInterval = prefs[Keys.DEFAULT_INTERVAL] ?: 1.0f,
            defaultPacketSize = prefs[Keys.DEFAULT_PACKET_SIZE] ?: 56,
            defaultTimeout = prefs[Keys.DEFAULT_TIMEOUT] ?: 10,
            themeMode = prefs[Keys.THEME_MODE] ?: "system",
            historyRetentionDays = prefs[Keys.HISTORY_RETENTION_DAYS] ?: 30,
            publicIpEnabled = prefs[Keys.PUBLIC_IP_ENABLED] ?: false,
        )
    }

    suspend fun updateDefaultCount(count: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_COUNT] = count }
    }

    suspend fun updateDefaultInterval(interval: Float) {
        context.dataStore.edit { it[Keys.DEFAULT_INTERVAL] = interval }
    }

    suspend fun updateDefaultPacketSize(size: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_PACKET_SIZE] = size }
    }

    suspend fun updateDefaultTimeout(timeout: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_TIMEOUT] = timeout }
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun updateHistoryRetentionDays(days: Int) {
        context.dataStore.edit { it[Keys.HISTORY_RETENTION_DAYS] = days }
    }

    suspend fun updatePublicIpEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PUBLIC_IP_ENABLED] = enabled }
    }
}
