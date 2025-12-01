package net.heckerdev.cordium.settings.handlers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.fragment.app.Fragment
import net.heckerdev.cordium.settings.SettingItem
import net.heckerdev.cordium.settings.SettingsAdapter
import net.heckerdev.cordium.settings.SettingsManager

/**
 * Handler for displaying build number information
 */
class SettingsBuildNumber : SettingHandler {
    
    override val settingId: String = "build_number"
    
    override fun handle(
        context: Context,
        fragment: Fragment,
        item: SettingItem,
        adapter: SettingsAdapter,
        settingsManager: SettingsManager
    ) {
        // This is an info item, just update the display
        updateBuildNumber(context, adapter)
    }
    
    /**
     * Update the build number display
     */
    fun updateBuildNumber(context: Context, adapter: SettingsAdapter) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName, 
                0
            )
            val versionName = packageInfo.versionName ?: "Unknown"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
            adapter.updateItemSubtitle(settingId, "$versionName ($versionCode)")
        } catch (e: PackageManager.NameNotFoundException) {
            adapter.updateItemSubtitle(settingId, "Unknown")
        }
    }
}
