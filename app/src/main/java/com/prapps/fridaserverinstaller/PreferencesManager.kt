package com.prapps.fridaserverinstaller

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    var isAutoStartEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_START, value) }
    
    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
        set(value) = prefs.edit { putInt(KEY_SERVER_PORT, value) }
    
    var isDarkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit { putBoolean(KEY_DARK_THEME, value) }
    
    // Saved versions
    fun getSavedVersions(): Set<String> {
        return prefs.getStringSet(KEY_SAVED_VERSIONS, emptySet())?.toSet() ?: emptySet()
    }
    
    fun addSavedVersion(version: String) {
        val current = getSavedVersions().toMutableSet()
        current.add(version)
        prefs.edit { putStringSet(KEY_SAVED_VERSIONS, current) }
    }
    
    fun removeSavedVersion(version: String) {
        val current = getSavedVersions().toMutableSet()
        current.remove(version)
        prefs.edit { putStringSet(KEY_SAVED_VERSIONS, current) }
    }
    
    companion object {
        private const val PREFS_NAME = "frida_server_prefs"
        private const val KEY_AUTO_START = "auto_start_enabled"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_SAVED_VERSIONS = "saved_versions"
        const val DEFAULT_PORT = 27042
    }
}
