package net.heckerdev.cordium.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * Data models for the dynamic settings system
 */

// Root configuration
data class SettingsConfig(
    val categories: List<SettingsCategory>,
    val subpages: Map<String, SettingsSubpage>
)

// Category section
data class SettingsCategory(
    val id: String,
    val title: String,
    val items: List<SettingItem>
)

// Subpage definition
data class SettingsSubpage(
    val title: String,
    val categories: List<SettingsCategory>
)

// Base setting item
sealed class SettingItem {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String?
    abstract val icon: String?
}

// Info item (non-interactive display)
data class InfoItem(
    override val id: String,
    override val title: String,
    override val subtitle: String?,
    override val icon: String?,
    val isDynamic: Boolean = false
) : SettingItem()

// Action item (clickable)
data class ActionItem(
    override val id: String,
    override val title: String,
    override val subtitle: String?,
    override val icon: String?,
    val isDynamic: Boolean = false
) : SettingItem()

// Toggle switch
data class ToggleItem(
    override val id: String,
    override val title: String,
    override val subtitle: String?,
    override val icon: String?,
    val key: String,
    val default: Boolean
) : SettingItem()

// Selection (single choice)
data class SelectionItem(
    override val id: String,
    override val title: String,
    override val subtitle: String?,
    override val icon: String?,
    val key: String,
    val options: List<SelectionOption>,
    val default: String
) : SettingItem()

data class SelectionOption(
    val value: String,
    val label: String
)

// Subpage navigation item
data class SubpageItem(
    override val id: String,
    override val title: String,
    override val subtitle: String?,
    override val icon: String?,
    val pageId: String
) : SettingItem()

/**
 * Parser to convert JSON to data models
 */
object SettingsParser {
    
    fun parse(jsonString: String): SettingsConfig {
        val root = JSONObject(jsonString)
        val categories = parseCategories(root.getJSONArray("categories"))
        val subpages = if (root.has("subpages")) {
            parseSubpages(root.getJSONObject("subpages"))
        } else {
            emptyMap()
        }
        return SettingsConfig(categories, subpages)
    }
    
    private fun parseCategories(array: JSONArray): List<SettingsCategory> {
        return (0 until array.length()).map { i ->
            parseCategory(array.getJSONObject(i))
        }
    }
    
    private fun parseCategory(obj: JSONObject): SettingsCategory {
        return SettingsCategory(
            id = obj.getString("id"),
            title = obj.getString("title"),
            items = parseItems(obj.getJSONArray("items"))
        )
    }
    
    private fun parseItems(array: JSONArray): List<SettingItem> {
        return (0 until array.length()).map { i ->
            parseItem(array.getJSONObject(i))
        }
    }
    
    private fun parseItem(obj: JSONObject): SettingItem {
        val type = obj.getString("type")
        val id = obj.getString("id")
        val title = obj.getString("title")
        val subtitle = obj.optString("subtitle", null)
        val icon = obj.optString("icon", null)
        
        return when (type) {
            "info" -> InfoItem(
                id = id,
                title = title,
                subtitle = subtitle,
                icon = icon,
                isDynamic = obj.optBoolean("dynamic", false)
            )
            "action" -> ActionItem(
                id = id,
                title = title,
                subtitle = subtitle,
                icon = icon,
                isDynamic = obj.optBoolean("dynamic", false)
            )
            "toggle" -> ToggleItem(
                id = id,
                title = title,
                subtitle = subtitle,
                icon = icon,
                key = obj.getString("key"),
                default = obj.getBoolean("default")
            )
            "selection" -> SelectionItem(
                id = id,
                title = title,
                subtitle = subtitle,
                icon = icon,
                key = obj.getString("key"),
                options = parseOptions(obj.getJSONArray("options")),
                default = obj.getString("default")
            )
            "subpage" -> SubpageItem(
                id = id,
                title = title,
                subtitle = subtitle,
                icon = icon,
                pageId = obj.getString("page")
            )
            else -> throw IllegalArgumentException("Unknown setting type: $type")
        }
    }
    
    private fun parseOptions(array: JSONArray): List<SelectionOption> {
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            SelectionOption(
                value = obj.getString("value"),
                label = obj.getString("label")
            )
        }
    }
    
    private fun parseSubpages(obj: JSONObject): Map<String, SettingsSubpage> {
        val map = mutableMapOf<String, SettingsSubpage>()
        obj.keys().forEach { key ->
            val subpageObj = obj.getJSONObject(key)
            map[key] = SettingsSubpage(
                title = subpageObj.getString("title"),
                categories = parseCategories(subpageObj.getJSONArray("categories"))
            )
        }
        return map
    }
}
