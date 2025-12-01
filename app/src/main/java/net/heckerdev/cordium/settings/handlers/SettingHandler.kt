package net.heckerdev.cordium.settings.handlers

import android.content.Context
import androidx.fragment.app.Fragment
import net.heckerdev.cordium.settings.SettingItem
import net.heckerdev.cordium.settings.SettingsAdapter
import net.heckerdev.cordium.settings.SettingsManager

/**
 * Base interface for handling setting actions
 */
interface SettingHandler {
    /**
     * The setting ID this handler is responsible for
     */
    val settingId: String
    
    /**
     * Handle the setting action
     * @param context Android context
     * @param fragment The fragment (for showing dialogs, etc.)
     * @param item The setting item that was clicked
     * @param adapter The settings adapter (for updating UI)
     * @param settingsManager The settings manager (for reading/writing preferences)
     */
    fun handle(
        context: Context,
        fragment: Fragment,
        item: SettingItem,
        adapter: SettingsAdapter,
        settingsManager: SettingsManager
    )
    
    /**
     * Called when the handler should clean up resources
     */
    fun cleanup() {}
}
