package net.heckerdev.cordium.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Manager for storing and retrieving setting values
 */
class SettingsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "cordium_settings",
        Context.MODE_PRIVATE
    )
    
    // Toggle settings
    fun getBoolean(key: String, default: Boolean): Boolean {
        return prefs.getBoolean(key, default)
    }
    
    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
    
    // Selection settings
    fun getString(key: String, default: String): String {
        return prefs.getString(key, default) ?: default
    }
    
    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    
    // Clear all settings
    fun clear() {
        prefs.edit().clear().apply()
    }
    
    // Get all settings as map
    fun getAll(): Map<String, *> {
        return prefs.all
    }
    
    // Register change listener
    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }
    
    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
