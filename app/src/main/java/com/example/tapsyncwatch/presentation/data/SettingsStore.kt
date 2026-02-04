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

    // 🆕 UI-only monitors / debug
    val SHOW_EXTERNAL_BPM = booleanPreferencesKey("show_external_bpm")
    val SHOW_OSC_DEBUG = booleanPreferencesKey("show_osc_debug")

    // 🆕 Phase visualizer
    val PHASE_VISUALIZER_ENABLED = booleanPreferencesKey("phase_visualizer_enabled")
    val PHASE_SPIRAL_ENABLED = booleanPreferencesKey("phase_spiral_enabled")

    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val DOWNBEAT_HAPTICS_ENABLED =
        booleanPreferencesKey("downbeat_haptics_enabled")

    // 🆕 Transport-Haptics (OSC Send)
    val TRANSPORT_HAPTICS_ENABLED =
        booleanPreferencesKey("transport_haptics_enabled")

    val CLOCK_MODE = stringPreferencesKey("clock_mode")
    val CLOCK_ENABLED = booleanPreferencesKey("clock_enabled")

    // 🆕 Animations
    val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
    val REMOTE_ANIMATIONS_ENABLED = booleanPreferencesKey("remote_animations_enabled")
    val REMOTE_GHOST_MODE_ENABLED = booleanPreferencesKey("remote_ghost_mode_enabled")
    val GOBLIN_FLASH_ENABLED = booleanPreferencesKey("goblin_flash_enabled")
    val RIPPLE_ENABLED = booleanPreferencesKey("ripple_enabled")
    val OSC_PULSE_ENABLED = booleanPreferencesKey("osc_pulse_enabled")

    // 🆕 Connection heartbeat (Ping/Pong via Resolume Wire)
    val HEARTBEAT_ENABLED = booleanPreferencesKey("heartbeat_enabled")
    val HEARTBEAT_INTERVAL_MS = longPreferencesKey("heartbeat_interval_ms")
    val SIGNAL_GRACE_MS = longPreferencesKey("signal_grace_ms")
    val HEARTBEAT_ADAPTIVE_ENABLED = booleanPreferencesKey("heartbeat_adaptive_enabled")
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

    // UI monitors
    val showExternalBpm: Boolean,
    val showOscDebug: Boolean,
    val phaseVisualizerEnabled: Boolean,
    val phaseSpiralEnabled: Boolean,

    // UI motion
    val animationsEnabled: Boolean,
    val remoteAnimationsEnabled: Boolean,
    val remoteGhostModeEnabled: Boolean,
    val goblinFlashEnabled: Boolean,
    val rippleEnabled: Boolean,
    val oscPulseEnabled: Boolean,

    // Connection (Ping/Pong)
    val heartbeatEnabled: Boolean,
    val heartbeatAdaptiveEnabled: Boolean,
    val heartbeatIntervalMs: Long,
    val signalGraceMs: Long,

    val hapticsEnabled: Boolean,
    val downbeatHapticsEnabled: Boolean,
    val transportHapticsEnabled: Boolean, // 🆕
    val clockMode: ClockMode,
    val clockEnabled: Boolean
) {
    val activeTarget: OscTarget
        get() = presets[activePreset.coerceIn(0, presets.lastIndex)]
}

/* =========================================================
 * DEFAULT STATE
 * ========================================================= */

val DEFAULT_SETTINGS_STATE = SettingsState(
    presets = listOf(
        OscTarget("Preset A", "192.168.178.24", 7002),
        OscTarget("Preset B", "192.168.178.25", 7002),
        OscTarget("Preset C", "192.168.178.26", 7002)
    ),
    activePreset = 0,
    showOscDot = true,

    showExternalBpm = true,
    showOscDebug = false,
    phaseVisualizerEnabled = true,
    phaseSpiralEnabled = false,

    animationsEnabled = true,
    remoteAnimationsEnabled = true,
    remoteGhostModeEnabled = true,
    goblinFlashEnabled = true,
    rippleEnabled = true,
    oscPulseEnabled = true,

    heartbeatEnabled = true,
    heartbeatAdaptiveEnabled = true,
    heartbeatIntervalMs = 1200L,
    signalGraceMs = 5000L,

    hapticsEnabled = true,
    downbeatHapticsEnabled = false,
    transportHapticsEnabled = false, // 🆕 default OFF
    clockMode = ClockMode.EXTERNAL,
    clockEnabled = true
)

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

                showExternalBpm = prefs[SettingsKeys.SHOW_EXTERNAL_BPM] ?: true,
                showOscDebug = prefs[SettingsKeys.SHOW_OSC_DEBUG] ?: false,
                phaseVisualizerEnabled = prefs[SettingsKeys.PHASE_VISUALIZER_ENABLED] ?: true,
                phaseSpiralEnabled = prefs[SettingsKeys.PHASE_SPIRAL_ENABLED] ?: false,

                animationsEnabled = prefs[SettingsKeys.ANIMATIONS_ENABLED] ?: true,
                remoteAnimationsEnabled = prefs[SettingsKeys.REMOTE_ANIMATIONS_ENABLED] ?: true,
                remoteGhostModeEnabled = prefs[SettingsKeys.REMOTE_GHOST_MODE_ENABLED] ?: true,
                goblinFlashEnabled = prefs[SettingsKeys.GOBLIN_FLASH_ENABLED] ?: true,
                rippleEnabled = prefs[SettingsKeys.RIPPLE_ENABLED] ?: true,
                oscPulseEnabled = prefs[SettingsKeys.OSC_PULSE_ENABLED] ?: true,
                heartbeatEnabled = prefs[SettingsKeys.HEARTBEAT_ENABLED] ?: DEFAULT_SETTINGS_STATE.heartbeatEnabled,
                heartbeatAdaptiveEnabled = prefs[SettingsKeys.HEARTBEAT_ADAPTIVE_ENABLED] ?: DEFAULT_SETTINGS_STATE.heartbeatAdaptiveEnabled,
                heartbeatIntervalMs = prefs[SettingsKeys.HEARTBEAT_INTERVAL_MS] ?: DEFAULT_SETTINGS_STATE.heartbeatIntervalMs,
                signalGraceMs = prefs[SettingsKeys.SIGNAL_GRACE_MS] ?: DEFAULT_SETTINGS_STATE.signalGraceMs,

                hapticsEnabled = prefs[SettingsKeys.HAPTICS_ENABLED] ?: true,
                downbeatHapticsEnabled =
                    prefs[SettingsKeys.DOWNBEAT_HAPTICS_ENABLED] ?: false,
                transportHapticsEnabled =
                    prefs[SettingsKeys.TRANSPORT_HAPTICS_ENABLED] ?: false,
                clockMode = ClockMode.valueOf(
                    prefs[SettingsKeys.CLOCK_MODE]
                        ?: ClockMode.EXTERNAL.name
                ),
                clockEnabled = prefs[SettingsKeys.CLOCK_ENABLED] ?: true
            )
        }

    val activeTarget: Flow<OscTarget> =
        settings.map { it.activeTarget }

    suspend fun setShowOscDot(show: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.SHOW_OSC_DOT] = show
        }
    }

    suspend fun setShowExternalBpm(show: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.SHOW_EXTERNAL_BPM] = show
        }
    }

    suspend fun setShowOscDebug(show: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.SHOW_OSC_DEBUG] = show
        }
    }

    suspend fun setPhaseVisualizerEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.PHASE_VISUALIZER_ENABLED] = enabled
        }
    }

    suspend fun setPhaseSpiralEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.PHASE_SPIRAL_ENABLED] = enabled
        }
    }

    // 🆕 Animations
    suspend fun setAnimationsEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.ANIMATIONS_ENABLED] = enabled
        }
    }

    suspend fun setRemoteAnimationsEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.REMOTE_ANIMATIONS_ENABLED] = enabled
        }
    }

    suspend fun setRemoteGhostModeEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.REMOTE_GHOST_MODE_ENABLED] = enabled
        }
    }

    suspend fun setGoblinFlashEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.GOBLIN_FLASH_ENABLED] = enabled
        }
    }

    suspend fun setRippleEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.RIPPLE_ENABLED] = enabled
        }
    }

    suspend fun setOscPulseEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.OSC_PULSE_ENABLED] = enabled
        }
    }

    // 🆕 Heartbeat
    suspend fun setHeartbeatEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.HEARTBEAT_ENABLED] = enabled
        }
    }


suspend fun setHeartbeatAdaptiveEnabled(enabled: Boolean) {
    context.dataStore.edit {
        it[SettingsKeys.HEARTBEAT_ADAPTIVE_ENABLED] = enabled
    }
}

    suspend fun setHeartbeatIntervalMs(value: Long) {
        context.dataStore.edit {
            it[SettingsKeys.HEARTBEAT_INTERVAL_MS] = value.coerceIn(250L, 5000L)
        }
    }

    suspend fun setSignalGraceMs(value: Long) {
        context.dataStore.edit {
            it[SettingsKeys.SIGNAL_GRACE_MS] = value.coerceIn(750L, 20000L)
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

    // 🆕 Transport-Haptics
    suspend fun setTransportHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.TRANSPORT_HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setClockMode(mode: ClockMode) {
        context.dataStore.edit {
            it[SettingsKeys.CLOCK_MODE] = mode.name
        }
    }

    suspend fun setClockEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.CLOCK_ENABLED] = enabled
        }
    }

    suspend fun setActivePreset(index: Int) {
        context.dataStore.edit {
            it[SettingsKeys.ACTIVE_PRESET] = index
        }
    }

    suspend fun updatePreset(
        index: Int,
        name: String,
        ip: String,
        port: Int
    ) {
        context.dataStore.edit { prefs ->
            when (index) {
                0 -> {
                    prefs[SettingsKeys.PRESET_A_NAME] = name
                    prefs[SettingsKeys.PRESET_A_IP] = ip
                    prefs[SettingsKeys.PRESET_A_PORT] = port
                }
                1 -> {
                    prefs[SettingsKeys.PRESET_B_NAME] = name
                    prefs[SettingsKeys.PRESET_B_IP] = ip
                    prefs[SettingsKeys.PRESET_B_PORT] = port
                }
                2 -> {
                    prefs[SettingsKeys.PRESET_C_NAME] = name
                    prefs[SettingsKeys.PRESET_C_IP] = ip
                    prefs[SettingsKeys.PRESET_C_PORT] = port
                }
            }
        }
    }
}
