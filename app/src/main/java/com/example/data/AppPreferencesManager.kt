package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent Configuration & State Manager.
 * Preserves user settings across app process death and system restarts.
 */
class AppPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("game_turbo_verified_prefs", Context.MODE_PRIVATE)

    // 1. User Preferences
    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME, "dark") ?: "dark")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(prefs.getString(KEY_LANGUAGE, "ar") ?: "ar")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _selectedGamePkg = MutableStateFlow(prefs.getString(KEY_SELECTED_GAME, "com.tencent.ig") ?: "com.tencent.ig")
    val selectedGamePkg: StateFlow<String> = _selectedGamePkg.asStateFlow()

    // Unified Profiles: "balanced", "performance", "battery", "competitive"
    private val _selectedProfile = MutableStateFlow(prefs.getString(KEY_PROFILE, "balanced") ?: "balanced")
    val selectedProfile: StateFlow<String> = _selectedProfile.asStateFlow()

    private val _selectedDns = MutableStateFlow(prefs.getString(KEY_SELECTED_DNS, "one.one.one.one") ?: "one.one.one.one")
    val selectedDns: StateFlow<String> = _selectedDns.asStateFlow()

    private val _safeModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_SAFE_MODE, true))
    val safeModeEnabled: StateFlow<Boolean> = _safeModeEnabled.asStateFlow()

    private val _autoDetectEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DETECT, false))
    val autoDetectEnabled: StateFlow<Boolean> = _autoDetectEnabled.asStateFlow()

    private val _dndEnabled = MutableStateFlow(prefs.getBoolean(KEY_DND_ENABLED, false))
    val dndEnabled: StateFlow<Boolean> = _dndEnabled.asStateFlow()

    // Floating Monitor Overlay Preferences
    private val _overlayEnabledPref = MutableStateFlow(prefs.getBoolean(KEY_OVERLAY_ENABLED, false))
    val overlayEnabledPref: StateFlow<Boolean> = _overlayEnabledPref.asStateFlow()

    private val _overlayPosX = MutableStateFlow(prefs.getInt(KEY_OVERLAY_X, 40))
    val overlayPosX: StateFlow<Int> = _overlayPosX.asStateFlow()

    private val _overlayPosY = MutableStateFlow(prefs.getInt(KEY_OVERLAY_Y, 180))
    val overlayPosY: StateFlow<Int> = _overlayPosY.asStateFlow()

    // 2. Interrupted Session State (Persisted across process death)
    private val _isSessionActive = MutableStateFlow(prefs.getBoolean(KEY_SESSION_ACTIVE, false))
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _activeSessionId = MutableStateFlow(prefs.getLong(KEY_ACTIVE_SESSION_ID, 0L))
    val activeSessionId: StateFlow<Long> = _activeSessionId.asStateFlow()

    private val _previousDndFilter = MutableStateFlow(prefs.getInt(KEY_PREV_DND_FILTER, -1))
    val previousDndFilter: StateFlow<Int> = _previousDndFilter.asStateFlow()

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME, mode).apply()
        _themeMode.value = mode
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
        _language.value = lang
    }

    fun setSelectedGame(pkg: String) {
        prefs.edit().putString(KEY_SELECTED_GAME, pkg).apply()
        _selectedGamePkg.value = pkg
    }

    fun setSelectedProfile(prof: String) {
        val normalized = when (prof.lowercase()) {
            "performance", "high_performance" -> "performance"
            "battery", "eco" -> "battery"
            "competitive", "pro" -> "competitive"
            else -> "balanced"
        }
        prefs.edit().putString(KEY_PROFILE, normalized).apply()
        _selectedProfile.value = normalized
    }

    fun setSelectedDns(dnsHost: String) {
        prefs.edit().putString(KEY_SELECTED_DNS, dnsHost).apply()
        _selectedDns.value = dnsHost
    }

    fun setSafeMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAFE_MODE, enabled).apply()
        _safeModeEnabled.value = enabled
    }

    fun setAutoDetect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DETECT, enabled).apply()
        _autoDetectEnabled.value = enabled
    }

    fun setDndEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DND_ENABLED, enabled).apply()
        _dndEnabled.value = enabled
    }

    fun setOverlayEnabledPref(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
        _overlayEnabledPref.value = enabled
    }

    fun setOverlayPosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_OVERLAY_X, x).putInt(KEY_OVERLAY_Y, y).apply()
        _overlayPosX.value = x
        _overlayPosY.value = y
    }

    fun setSessionActiveState(isActive: Boolean, sessionId: Long = 0L) {
        prefs.edit()
            .putBoolean(KEY_SESSION_ACTIVE, isActive)
            .putLong(KEY_ACTIVE_SESSION_ID, sessionId)
            .apply()
        _isSessionActive.value = isActive
        _activeSessionId.value = sessionId
    }

    fun setPreviousDndFilter(filter: Int) {
        prefs.edit().putInt(KEY_PREV_DND_FILTER, filter).apply()
        _previousDndFilter.value = filter
    }

    companion object {
        private const val KEY_THEME = "pref_theme_mode"
        private const val KEY_LANGUAGE = "pref_language"
        private const val KEY_SELECTED_GAME = "pref_selected_game"
        private const val KEY_PROFILE = "pref_profile"
        private const val KEY_SELECTED_DNS = "pref_selected_dns"
        private const val KEY_SAFE_MODE = "pref_safe_mode"
        private const val KEY_AUTO_DETECT = "pref_auto_detect"
        private const val KEY_DND_ENABLED = "pref_dnd_enabled"
        private const val KEY_OVERLAY_ENABLED = "pref_overlay_enabled"
        private const val KEY_OVERLAY_X = "pref_overlay_x"
        private const val KEY_OVERLAY_Y = "pref_overlay_y"
        private const val KEY_SESSION_ACTIVE = "state_session_active"
        private const val KEY_ACTIVE_SESSION_ID = "state_active_session_id"
        private const val KEY_PREV_DND_FILTER = "state_prev_dnd_filter"
    }
}
