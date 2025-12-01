package net.heckerdev.cordium

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.heckerdev.cordium.settings.*
import net.heckerdev.cordium.settings.handlers.*

class SettingsFragment : Fragment() {
    
    private lateinit var settingsConfig: SettingsConfig
    private lateinit var settingsManager: SettingsManager
    private lateinit var adapter: SettingsAdapter
    private var currentPageId: String? = null
    
    // Setting handlers
    private val handlers = mutableMapOf<String, SettingHandler>()
    private val updateAppHandler = SettingsUpdateApp()
    private val buildNumberHandler = SettingsBuildNumber()
    
    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // If on a subpage, go back to main settings
            if (currentPageId != null) {
                loadSettingsPage(null)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        
        // Register back pressed callback
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
        
        // Initialize settings manager
        settingsManager = SettingsManager(requireContext())
        
        // Initialize handlers
        initializeHandlers()
        
        // Load settings configuration from JSON
        try {
            val jsonString = requireContext().assets.open("settings.json").bufferedReader().use { it.readText() }
            settingsConfig = SettingsParser.parse(jsonString)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error loading settings: ${e.message}", Toast.LENGTH_LONG).show()
            return view
        }
        
        // Setup RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.settingsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        adapter = SettingsAdapter(settingsManager) { item ->
            handleSettingItemClick(item)
        }
        recyclerView.adapter = adapter
        
        // Load main settings page
        loadSettingsPage(null)
        
        // Update dynamic items
        buildNumberHandler.updateBuildNumber(requireContext(), adapter)
        
        return view
    }
    
    private fun initializeHandlers() {
        // Initialize update app handler
        updateAppHandler.initialize(this)
        handlers[updateAppHandler.settingId] = updateAppHandler
        
        // Initialize build number handler
        handlers[buildNumberHandler.settingId] = buildNumberHandler
    }
    
    private fun loadSettingsPage(pageId: String?) {
        currentPageId = pageId
        
        // Update back button callback - enabled when on subpage
        backPressedCallback.isEnabled = (pageId != null)
        
        val categories = if (pageId == null) {
            // Load main page
            view?.findViewById<TextView>(R.id.settingsTitle)?.text = "Settings"
            settingsConfig.categories
        } else {
            // Load subpage
            val subpage = settingsConfig.subpages[pageId]
            if (subpage != null) {
                view?.findViewById<TextView>(R.id.settingsTitle)?.text = subpage.title
                subpage.categories
            } else {
                Toast.makeText(requireContext(), "Page not found: $pageId", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        adapter.setCategories(categories)
    }
    
    private fun handleSettingItemClick(item: SettingItem) {
        // Check if there's a registered handler for this setting
        val handler = handlers[item.id]
        if (handler != null) {
            handler.handle(requireContext(), this, item, adapter, settingsManager)
            return
        }
        
        // Default handling for common setting types
        when (item) {
            is SubpageItem -> {
                loadSettingsPage(item.pageId)
            }
            is SelectionItem -> {
                showSelectionDialog(item)
            }
            is ToggleItem -> {
                handleToggleChange(item)
            }
            is ActionItem -> {
                Toast.makeText(requireContext(), "Action: ${item.title}", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // Info items and other non-interactive items
            }
        }
    }
    
    private fun handleToggleChange(item: ToggleItem) {
        val value = settingsManager.getBoolean(item.key, item.default)
        Toast.makeText(requireContext(), "${item.title}: ${if (value) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
    }
    
    private fun showSelectionDialog(item: SelectionItem) {
        val currentValue = settingsManager.getString(item.key, item.default)
        val currentIndex = item.options.indexOfFirst { it.value == currentValue }
        
        val labels = item.options.map { it.label }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle(item.title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selectedOption = item.options[which]
                settingsManager.setString(item.key, selectedOption.value)
                adapter.notifyDataSetChanged()
                Toast.makeText(requireContext(), "Selected: ${selectedOption.label}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up all handlers
        handlers.values.forEach { it.cleanup() }
        // Specifically unregister update app receiver
        updateAppHandler.unregisterReceiver(requireContext())
    }
}
