package net.heckerdev.cordium.settings.handlers

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.heckerdev.cordium.settings.SettingItem
import net.heckerdev.cordium.settings.SettingsAdapter
import net.heckerdev.cordium.settings.SettingsManager
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handler for checking and installing app updates from GitHub releases
 */
class SettingsUpdateApp : SettingHandler {
    
    override val settingId: String = "check_updates"
    
    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    private var latestApkUrl: String? = null
    private var latestVersion: String? = null
    private var pendingInstallFileName: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var downloadCheckRunnable: Runnable? = null
    private var installPermissionLauncher: ActivityResultLauncher<Intent>? = null
    
    /**
     * Initialize the update handler with the fragment's result launcher
     */
    fun initialize(fragment: Fragment) {
        installPermissionLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val context = fragment.context ?: return@registerForActivityResult
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.packageManager.canRequestPackageInstalls()) {
                    pendingInstallFileName?.let { installApk(context, fragment, it) }
                    pendingInstallFileName = null
                } else {
                    Toast.makeText(context, "Install permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun handle(
        context: Context,
        fragment: Fragment,
        item: SettingItem,
        adapter: SettingsAdapter,
        settingsManager: SettingsManager
    ) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionName = packageInfo.versionName ?: "Unknown"
            
            // If we already know there's an update available, download it
            if (latestApkUrl != null && latestVersion != null) {
                adapter.updateItemSubtitle(settingId, "Downloading update...")
                Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show()
                downloadAndInstallApk(context, fragment, adapter, latestApkUrl!!, latestVersion!!)
                latestApkUrl = null
                latestVersion = null
            } else {
                adapter.updateItemSubtitle(settingId, "Checking for updates...")
                checkForUpdates(context, adapter, currentVersionName)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Toast.makeText(context, "Error getting version", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun cleanup() {
        downloadReceiver?.let {
            // Will be unregistered by the fragment's context
        }
        downloadCheckRunnable?.let { handler.removeCallbacks(it) }
    }
    
    private fun checkForUpdates(context: Context, adapter: SettingsAdapter, currentVersion: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://api.github.com/repos/hecker-01/cordium/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val latestVersion = json.getString("tag_name").removePrefix("v")
                    
                    withContext(Dispatchers.Main) {
                        if (isNewerVersion(currentVersion, latestVersion)) {
                            // Find the APK asset
                            val assets = json.getJSONArray("assets")
                            var apkUrl: String? = null
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.getString("name")
                                if (name.endsWith(".apk")) {
                                    apkUrl = asset.getString("browser_download_url")
                                    break
                                }
                            }
                            
                            if (apkUrl != null) {
                                latestApkUrl = apkUrl
                                this@SettingsUpdateApp.latestVersion = latestVersion
                                adapter.updateItemSubtitle(settingId, "New version available: $latestVersion. Tap again to update")
                                Toast.makeText(context, "New version available: $latestVersion", Toast.LENGTH_SHORT).show()
                            } else {
                                adapter.updateItemSubtitle(settingId, "No APK found in release")
                                Toast.makeText(context, "No APK found in the release", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            adapter.updateItemSubtitle(settingId, "You're on the latest version")
                            Toast.makeText(context, "You're on the latest version!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        adapter.updateItemSubtitle(settingId, "Failed to check for updates (HTTP $responseCode)")
                        Toast.makeText(context, "Failed to check for updates. Server returned: HTTP $responseCode", Toast.LENGTH_LONG).show()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    adapter.updateItemSubtitle(settingId, "Error checking for updates")
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun downloadAndInstallApk(
        context: Context,
        fragment: Fragment,
        adapter: SettingsAdapter,
        apkUrl: String,
        version: String
    ) {
        try {
            val fileName = "Cordium-$version.apk"
            
            // Register receiver BEFORE starting download
            downloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        downloadCheckRunnable?.let { handler.removeCallbacks(it) }
                        handleDownloadComplete(context, fragment, adapter, fileName)
                    }
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(
                    downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
            
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Cordium Update")
                .setDescription("Downloading version $version")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)
            
            adapter.updateItemSubtitle(settingId, "Downloading update...")
            
            // Start polling as a fallback in case BroadcastReceiver doesn't fire
            startDownloadStatusPolling(context, adapter, downloadManager, fileName, fragment)
            
        } catch (e: Exception) {
            adapter.updateItemSubtitle(settingId, "Download failed")
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun startDownloadStatusPolling(
        context: Context,
        adapter: SettingsAdapter,
        downloadManager: DownloadManager,
        fileName: String,
        fragment: Fragment
    ) {
        downloadCheckRunnable = object : Runnable {
            override fun run() {
                try {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor: Cursor? = downloadManager.query(query)
                    
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIndex)
                        
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                cursor.close()
                                downloadCheckRunnable?.let { handler.removeCallbacks(it) }
                                handleDownloadComplete(context, fragment, adapter, fileName)
                                return
                            }
                            DownloadManager.STATUS_FAILED -> {
                                cursor.close()
                                downloadCheckRunnable?.let { handler.removeCallbacks(it) }
                                adapter.updateItemSubtitle(settingId, "Download failed")
                                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                return
                            }
                        }
                        cursor.close()
                    }
                    
                    // Check again in 500ms
                    handler.postDelayed(this, 500)
                } catch (e: Exception) {
                    // Fragment might be destroyed
                }
            }
        }
        handler.post(downloadCheckRunnable!!)
    }
    
    private fun handleDownloadComplete(
        context: Context,
        fragment: Fragment,
        adapter: SettingsAdapter,
        fileName: String
    ) {
        try {
            downloadReceiver?.let {
                try {
                    context.unregisterReceiver(it)
                } catch (e: Exception) {
                    // Already unregistered
                }
            }
            downloadReceiver = null
            
            adapter.updateItemSubtitle(settingId, "Download complete")
            Toast.makeText(context, "Download complete, installing...", Toast.LENGTH_SHORT).show()
            installApk(context, fragment, fileName)
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun installApk(context: Context, fragment: Fragment, fileName: String) {
        try {
            // Check if we need to request install permission (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    pendingInstallFileName = fileName
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    intent.data = Uri.parse("package:${context.packageName}")
                    installPermissionLauncher?.launch(intent)
                    return
                }
            }
            
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            
            if (!file.exists()) {
                Toast.makeText(context, "APK file not found", Toast.LENGTH_SHORT).show()
                return
            }
            
            val intent = Intent(Intent.ACTION_VIEW)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            fragment.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Installation failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun isNewerVersion(current: String, latest: String): Boolean {
        try {
            val currentParts = current.split("-")[0].split(".")
            val latestParts = latest.split("-")[0].split(".")
            
            for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
                val currentPart = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
                val latestPart = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
                
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Unregister broadcast receiver to prevent leaks
     */
    fun unregisterReceiver(context: Context) {
        downloadReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Already unregistered
            }
        }
        downloadReceiver = null
    }
}
