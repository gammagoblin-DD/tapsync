package com.example.tapsyncwatch.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("tapsync_settings")

object SettingsKeys {
    val TARGET_IP = stringPreferencesKey("target_ip")
    val TARGET_PORT = intPreferencesKey("target_port")
    val SHOW_BPM = booleanPreferencesKey("show_bpm")
}

class SettingsStore(private val context: Context) {

    val settings: Flow<SettingsState> = context.dataStore.data.map { prefs ->
        SettingsState(
            ip = prefs[SettingsKeys.TARGET_IP] ?: "192.168.178.24",
            port = prefs[SettingsKeys.TARGET_PORT] ?: 7002,
            showBpm = prefs[SettingsKeys.SHOW_BPM] ?: true
        )
    }

    suspend fun updateIp(ip: String) {
        context.dataStore.edit { it[SettingsKeys.TARGET_IP] = ip }
    }

    suspend fun updatePort(port: Int) {
        context.dataStore.edit { it[SettingsKeys.TARGET_PORT] = port }
    }

    suspend fun setShowBpm(show: Boolean) {
        context.dataStore.edit { it[SettingsKeys.SHOW_BPM] = show }
    }
}

data class SettingsState(
    val ip: String,
    val port: Int,
    val showBpm: Boolean
)
