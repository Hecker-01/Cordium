package net.heckerdev.cordium

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SettingsFragment : Fragment() {

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
            updateStatusText.text = "Checking for updates..."
            checkForUpdates(currentVersionName, updateStatusText)
        }
        
        return view
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
                    val htmlUrl = json.getString("html_url")
                    
                    withContext(Dispatchers.Main) {
                        if (isNewerVersion(currentVersion, latestVersion)) {
                            statusText.text = "Update available: $latestVersion"
                            Toast.makeText(requireContext(), "New version available: $latestVersion", Toast.LENGTH_LONG).show()
                            
                            // Open the release page
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl))
                            startActivity(intent)
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
