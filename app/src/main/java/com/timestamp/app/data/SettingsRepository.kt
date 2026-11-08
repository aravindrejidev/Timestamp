package com.timestamp.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("timestamp_settings", Context.MODE_PRIVATE)

    private val _amoled = MutableStateFlow(prefs.getBoolean("amoled", false))
    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()

    private val _glow = MutableStateFlow(prefs.getBoolean("glow", true))
    val glow: StateFlow<Boolean> = _glow.asStateFlow()

    private val _tapCompMs = MutableStateFlow(prefs.getInt("tap_comp_ms", 0))
    val tapCompMs: StateFlow<Int> = _tapCompMs.asStateFlow()

    private val _exportFolder = MutableStateFlow(prefs.getString("export_folder", null))
    val exportFolder: StateFlow<String?> = _exportFolder.asStateFlow()

    fun setAmoled(value: Boolean) {
        prefs.edit().putBoolean("amoled", value).apply()
        _amoled.value = value
    }

    fun setGlow(value: Boolean) {
        prefs.edit().putBoolean("glow", value).apply()
        _glow.value = value
    }

    fun setTapCompMs(value: Int) {
        prefs.edit().putInt("tap_comp_ms", value).apply()
        _tapCompMs.value = value
    }

    fun setExportFolder(uri: String?) {
        prefs.edit().putString("export_folder", uri).apply()
        _exportFolder.value = uri
    }
}
