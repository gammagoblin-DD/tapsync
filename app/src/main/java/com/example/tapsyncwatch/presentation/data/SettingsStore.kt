package com.example.tapsyncwatch.presentation.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.tapsyncwatch.domain.clock.ClockMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(
    name = "tapsync_settings"
)

/* =========================================================
 * KEYS
 * ========================================================= */

object SettingsKeys {

    val PRESET_A_NAME = stringPreferencesKey("preset_a_name")
    val PRESET_A_IP = stringPreferencesKey("preset_a_ip")
    val PRESET_A_PORT = intPreferencesKey("preset_a_port")

    val PRESET_B_NAME = stringPreferencesKey("preset_b_name")
    val PRESET_B_IP = stringPreferencesKey("preset_b_ip")
    val PRESET_B_PORT = intPreferencesKey("preset_b_port")

    val PRESET_C_NAME = stringPreferencesKey("preset_c_name")
    val PRESET_C_IP = stringPreferencesKey("preset_c_ip")
    val PRESET_C_PORT = intPreferencesKey("preset_c_port")

    val ACTIVE_PRESET = intPreferencesKey("active_preset")
    val SHOW_OSC_DOT = booleanPreferencesKey("show_osc_dot")

    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val DOWNBEAT_HAPTICS_ENABLED =
        booleanPreferencesKey("downbeat_haptics_enabled")

    val CLOCK_MODE = stringPreferencesKey("clock_mode")

    // 🆕 CLOCK ENABLE (ON / OFF)
    val CLOCK_ENABLED = booleanPreferencesKey("clock_enabled")
}

/* =========================================================
 * MODELS
 * ========================================================= */

data class OscTarget(
    val name: String,
    val ip: String,
    val port: Int
)

data class SettingsState(
    val presets: List<OscTarget>,
    val activePreset: Int,
    val showOscDot: Boolean,
    val hapticsEnabled: Boolean,
    val downbeatHapticsEnabled: Boolean,
    val clockMode: ClockMode,
    val clockEnabled: Boolean            // 🆕
) {
    val activeTarget: OscTarget
        get() = presets[activePreset.coerceIn(0, presets.lastIndex)]
}

/* =========================================================
 * STORE
 * ========================================================= */

class SettingsStore(
    private val context: Context
) {

    val settings: Flow<SettingsState> =
        context.dataStore.data.map { prefs ->

            val presets = listOf(
                OscTarget(
                    prefs[SettingsKeys.PRESET_A_NAME] ?: "Preset A",
                    prefs[SettingsKeys.PRESET_A_IP] ?: "192.168.178.24",
                    prefs[SettingsKeys.PRESET_A_PORT] ?: 7002
                ),
                OscTarget(
                    prefs[SettingsKeys.PRESET_B_NAME] ?: "Preset B",
                    prefs[SettingsKeys.PRESET_B_IP] ?: "192.168.178.25",
                    prefs[SettingsKeys.PRESET_B_PORT] ?: 7002
                ),
                OscTarget(
                    prefs[SettingsKeys.PRESET_C_NAME] ?: "Preset C",
                    prefs[SettingsKeys.PRESET_C_IP] ?: "192.168.178.26",
                    prefs[SettingsKeys.PRESET_C_PORT] ?: 7002
                )
            )

            SettingsState(
                presets = presets,
                activePreset = prefs[SettingsKeys.ACTIVE_PRESET] ?: 0,
                showOscDot = prefs[SettingsKeys.SHOW_OSC_DOT] ?: true,
                hapticsEnabled = prefs[SettingsKeys.HAPTICS_ENABLED] ?: true,
                downbeatHapticsEnabled =
                    prefs[SettingsKeys.DOWNBEAT_HAPTICS_ENABLED] ?: false,
                clockMode = ClockMode.valueOf(
                    prefs[SettingsKeys.CLOCK_MODE]
                        ?: ClockMode.EXTERNAL.name
                ),
                clockEnabled = prefs[SettingsKeys.CLOCK_ENABLED] ?: true
            )
        }

    val activeTarget: Flow<OscTarget> =
        settings.map { it.activeTarget }

    suspend fun setActivePreset(index: Int) {
        context.dataStore.edit {
            it[SettingsKeys.ACTIVE_PRESET] = index.coerceIn(0, 2)
        }
    }

    suspend fun updatePreset(index: Int, target: OscTarget) {
        context.dataStore.edit {
            when (index) {
                0 -> {
                    it[SettingsKeys.PRESET_A_NAME] = target.name
                    it[SettingsKeys.PRESET_A_IP] = target.ip
                    it[SettingsKeys.PRESET_A_PORT] = target.port
                }
                1 -> {
                    it[SettingsKeys.PRESET_B_NAME] = target.name
                    it[SettingsKeys.PRESET_B_IP] = target.ip
                    it[SettingsKeys.PRESET_B_PORT] = target.port
                }
                2 -> {
                    it[SettingsKeys.PRESET_C_NAME] = target.name
                    it[SettingsKeys.PRESET_C_IP] = target.ip
                    it[SettingsKeys.PRESET_C_PORT] = target.port
                }
            }
        }
    }

    suspend fun setShowOscDot(show: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.SHOW_OSC_DOT] = show
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setDownbeatHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.DOWNBEAT_HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setClockMode(mode: ClockMode) {
        context.dataStore.edit {
            it[SettingsKeys.CLOCK_MODE] = mode.name
        }
    }

    // 🆕 CLOCK ENABLE
    suspend fun setClockEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.CLOCK_ENABLED] = enabled
        }
    }
}
