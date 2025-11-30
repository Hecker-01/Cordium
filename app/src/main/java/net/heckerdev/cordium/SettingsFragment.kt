package net.heckerdev.cordium

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SettingsFragment : Fragment() {

    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    private var latestApkUrl: String? = null
    private var latestVersion: String? = null
    private var pendingInstallFileName: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var downloadCheckRunnable: Runnable? = null
    
    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (requireContext().packageManager.canRequestPackageInstalls()) {
                pendingInstallFileName?.let { installApk(it) }
                pendingInstallFileName = null
            } else {
                Toast.makeText(requireContext(), "Install permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        
        // Set build number
        val buildNumberText = view.findViewById<TextView>(R.id.buildNumberText)
        val currentVersionName: String
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(
                requireContext().packageName, 
                0
            )
            currentVersionName = packageInfo.versionName ?: "Unknown"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
            buildNumberText.text = "$currentVersionName ($versionCode)"
        } catch (e: PackageManager.NameNotFoundException) {
            buildNumberText.text = "Unknown"
            return view
        }
        
        // Check for updates
        val updateStatusText = view.findViewById<TextView>(R.id.updateStatusText)
        view.findViewById<View>(R.id.checkUpdatesItem).setOnClickListener {
            // If we already know there's an update available, download it
            if (latestApkUrl != null && latestVersion != null) {
                updateStatusText.text = "Downloading update..."
                Toast.makeText(requireContext(), "Downloading update...", Toast.LENGTH_SHORT).show()
                downloadAndInstallApk(latestApkUrl!!, latestVersion!!, updateStatusText)
                latestApkUrl = null
                latestVersion = null
            } else {
                updateStatusText.text = "Checking for updates..."
                checkForUpdates(currentVersionName, updateStatusText)
            }
        }
        
        return view
    }
    
    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (e: Exception) {
                // Already unregistered
            }
        }
        downloadCheckRunnable?.let { handler.removeCallbacks(it) }
    }
    
    private fun checkForUpdates(currentVersion: String, statusText: TextView) {
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
                                this@SettingsFragment.latestVersion = latestVersion
                                statusText.text = "New version available: $latestVersion. Tap again to update"
                                Toast.makeText(requireContext(), "New version available: $latestVersion", Toast.LENGTH_SHORT).show()
                            } else {
                                statusText.text = "No APK found in release"
                                Toast.makeText(requireContext(), "No APK found in the release", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            statusText.text = "You're on the latest version"
                            Toast.makeText(requireContext(), "You're on the latest version!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        statusText.text = "Failed to check for updates (HTTP $responseCode)"
                        Toast.makeText(requireContext(), "Failed to check for updates. Server returned: HTTP $responseCode", Toast.LENGTH_LONG).show()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Error checking for updates"
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun downloadAndInstallApk(apkUrl: String, version: String, statusText: TextView) {
        try {
            val fileName = "Cordium-$version.apk"
            
            // Register receiver BEFORE starting download
            downloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        downloadCheckRunnable?.let { handler.removeCallbacks(it) }
                        handleDownloadComplete(fileName, statusText)
                    }
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(
                    downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                requireContext().registerReceiver(
                    downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
            
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Cordium Update")
                .setDescription("Downloading version $version")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(requireContext(), Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)
            
            statusText.text = "Downloading update..."
            
            // Start polling as a fallback in case BroadcastReceiver doesn't fire
            startDownloadStatusPolling(downloadManager, fileName, statusText)
            
        } catch (e: Exception) {
            statusText.text = "Download failed"
            Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun startDownloadStatusPolling(downloadManager: DownloadManager, fileName: String, statusText: TextView) {
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
                                handleDownloadComplete(fileName, statusText)
                                return
                            }
                            DownloadManager.STATUS_FAILED -> {
                                cursor.close()
                                downloadCheckRunnable?.let { handler.removeCallbacks(it) }
                                statusText.text = "Download failed"
                                Toast.makeText(requireContext(), "Download failed", Toast.LENGTH_SHORT).show()
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
    
    private fun handleDownloadComplete(fileName: String, statusText: TextView) {
        try {
            downloadReceiver?.let {
                try {
                    requireContext().unregisterReceiver(it)
                } catch (e: Exception) {
                    // Already unregistered
                }
            }
            downloadReceiver = null
            
            statusText.text = "Download complete"
            Toast.makeText(requireContext(), "Download complete, installing...", Toast.LENGTH_SHORT).show()
            installApk(fileName)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun installApk(fileName: String) {
        try {
            // Check if we need to request install permission (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!requireContext().packageManager.canRequestPackageInstalls()) {
                    pendingInstallFileName = fileName
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    intent.data = Uri.parse("package:${requireContext().packageName}")
                    installPermissionLauncher.launch(intent)
                    return
                }
            }
            
            val file = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            
            if (!file.exists()) {
                Toast.makeText(requireContext(), "APK file not found", Toast.LENGTH_SHORT).show()
                return
            }
            
            val intent = Intent(Intent.ACTION_VIEW)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val apkUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Installation failed: ${e.message}", Toast.LENGTH_LONG).show()
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
}
