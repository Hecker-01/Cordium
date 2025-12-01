package net.heckerdev.cordium.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import net.heckerdev.cordium.R

/**
 * Adapter for displaying settings items
 */
class SettingsAdapter(
    private val settingsManager: SettingsManager,
    private val onItemClick: (SettingItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<SettingsAdapterItem>()
    
    companion object {
        private const val VIEW_TYPE_CATEGORY = 0
        private const val VIEW_TYPE_INFO = 1
        private const val VIEW_TYPE_ACTION = 2
        private const val VIEW_TYPE_TOGGLE = 3
        private const val VIEW_TYPE_SELECTION = 4
        private const val VIEW_TYPE_SUBPAGE = 5
    }
    
    sealed class SettingsAdapterItem {
        data class Category(val title: String) : SettingsAdapterItem()
        data class Item(val setting: SettingItem) : SettingsAdapterItem()
    }
    
    fun setCategories(categories: List<SettingsCategory>) {
        items.clear()
        categories.forEach { category ->
            items.add(SettingsAdapterItem.Category(category.title))
            category.items.forEach { item ->
                items.add(SettingsAdapterItem.Item(item))
            }
        }
        notifyDataSetChanged()
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is SettingsAdapterItem.Category -> VIEW_TYPE_CATEGORY
            is SettingsAdapterItem.Item -> when (item.setting) {
                is InfoItem -> VIEW_TYPE_INFO
                is ActionItem -> VIEW_TYPE_ACTION
                is ToggleItem -> VIEW_TYPE_TOGGLE
                is SelectionItem -> VIEW_TYPE_SELECTION
                is SubpageItem -> VIEW_TYPE_SUBPAGE
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_CATEGORY -> CategoryViewHolder(
                inflater.inflate(R.layout.item_settings_category, parent, false)
            )
            VIEW_TYPE_INFO -> InfoViewHolder(
                inflater.inflate(R.layout.item_settings_info, parent, false)
            )
            VIEW_TYPE_ACTION -> ActionViewHolder(
                inflater.inflate(R.layout.item_settings_action, parent, false)
            )
            VIEW_TYPE_TOGGLE -> ToggleViewHolder(
                inflater.inflate(R.layout.item_settings_toggle, parent, false)
            )
            VIEW_TYPE_SELECTION -> SelectionViewHolder(
                inflater.inflate(R.layout.item_settings_selection, parent, false)
            )
            VIEW_TYPE_SUBPAGE -> SubpageViewHolder(
                inflater.inflate(R.layout.item_settings_subpage, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SettingsAdapterItem.Category -> {
                (holder as CategoryViewHolder).bind(item.title)
            }
            is SettingsAdapterItem.Item -> {
                when (val setting = item.setting) {
                    is InfoItem -> (holder as InfoViewHolder).bind(setting)
                    is ActionItem -> (holder as ActionViewHolder).bind(setting, onItemClick)
                    is ToggleItem -> (holder as ToggleViewHolder).bind(setting, settingsManager, onItemClick)
                    is SelectionItem -> (holder as SelectionViewHolder).bind(setting, settingsManager, onItemClick)
                    is SubpageItem -> (holder as SubpageViewHolder).bind(setting, onItemClick)
                }
            }
        }
    }
    
    override fun getItemCount(): Int = items.size
    
    // Update dynamic content
    fun updateItemSubtitle(id: String, subtitle: String) {
        items.forEachIndexed { index, item ->
            if (item is SettingsAdapterItem.Item && item.setting.id == id) {
                when (val setting = item.setting) {
                    is InfoItem -> items[index] = SettingsAdapterItem.Item(
                        setting.copy(subtitle = subtitle)
                    )
                    is ActionItem -> items[index] = SettingsAdapterItem.Item(
                        setting.copy(subtitle = subtitle)
                    )
                    else -> {}
                }
                notifyItemChanged(index)
            }
        }
    }
    
    // ViewHolders
    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.categoryTitle)
        
        fun bind(title: String) {
            titleText.text = title
        }
    }
    
    class InfoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.settingIcon)
        private val title: TextView = view.findViewById(R.id.settingTitle)
        private val subtitle: TextView = view.findViewById(R.id.settingSubtitle)
        
        fun bind(item: InfoItem) {
            title.text = item.title
            subtitle.text = item.subtitle ?: ""
            subtitle.visibility = if (item.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
            
            item.icon?.let { iconName ->
                val iconRes = itemView.context.resources.getIdentifier(
                    iconName, "drawable", itemView.context.packageName
                )
                if (iconRes != 0) {
                    icon.setImageResource(iconRes)
                    icon.visibility = View.VISIBLE
                } else {
                    icon.visibility = View.GONE
                }
            } ?: run {
                icon.visibility = View.GONE
            }
        }
    }
    
    class ActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container: LinearLayout = view.findViewById(R.id.settingContainer)
        private val icon: ImageView = view.findViewById(R.id.settingIcon)
        private val title: TextView = view.findViewById(R.id.settingTitle)
        private val subtitle: TextView = view.findViewById(R.id.settingSubtitle)
        
        fun bind(item: ActionItem, onClick: (SettingItem) -> Unit) {
            title.text = item.title
            subtitle.text = item.subtitle ?: ""
            subtitle.visibility = if (item.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
            
            item.icon?.let { iconName ->
                val iconRes = itemView.context.resources.getIdentifier(
                    iconName, "drawable", itemView.context.packageName
                )
                if (iconRes != 0) {
                    icon.setImageResource(iconRes)
                    icon.visibility = View.VISIBLE
                } else {
                    icon.visibility = View.GONE
                }
            } ?: run {
                icon.visibility = View.GONE
            }
            
            container.setOnClickListener { onClick(item) }
        }
    }
    
    class ToggleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.settingIcon)
        private val title: TextView = view.findViewById(R.id.settingTitle)
        private val subtitle: TextView = view.findViewById(R.id.settingSubtitle)
        private val toggle: SwitchCompat = view.findViewById(R.id.settingToggle)
        
        fun bind(item: ToggleItem, manager: SettingsManager, onClick: (SettingItem) -> Unit) {
            title.text = item.title
            subtitle.text = item.subtitle ?: ""
            subtitle.visibility = if (item.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
            
            item.icon?.let { iconName ->
                val iconRes = itemView.context.resources.getIdentifier(
                    iconName, "drawable", itemView.context.packageName
                )
                if (iconRes != 0) {
                    icon.setImageResource(iconRes)
                    icon.visibility = View.VISIBLE
                } else {
                    icon.visibility = View.GONE
                }
            } ?: run {
                icon.visibility = View.GONE
            }
            
            val currentValue = manager.getBoolean(item.key, item.default)
            toggle.isChecked = currentValue
            
            toggle.setOnCheckedChangeListener { _, isChecked ->
                manager.setBoolean(item.key, isChecked)
                onClick(item)
            }
        }
    }
    
    class SelectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container: LinearLayout = view.findViewById(R.id.settingContainer)
        private val icon: ImageView = view.findViewById(R.id.settingIcon)
        private val title: TextView = view.findViewById(R.id.settingTitle)
        private val subtitle: TextView = view.findViewById(R.id.settingSubtitle)
        private val value: TextView = view.findViewById(R.id.settingValue)
        
        fun bind(item: SelectionItem, manager: SettingsManager, onClick: (SettingItem) -> Unit) {
            title.text = item.title
            subtitle.text = item.subtitle ?: ""
            subtitle.visibility = if (item.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
            
            item.icon?.let { iconName ->
                val iconRes = itemView.context.resources.getIdentifier(
                    iconName, "drawable", itemView.context.packageName
                )
                if (iconRes != 0) {
                    icon.setImageResource(iconRes)
                    icon.visibility = View.VISIBLE
                } else {
                    icon.visibility = View.GONE
                }
            } ?: run {
                icon.visibility = View.GONE
            }
            
            val currentValue = manager.getString(item.key, item.default)
            val selectedOption = item.options.find { it.value == currentValue }
            value.text = selectedOption?.label ?: currentValue
            
            container.setOnClickListener { onClick(item) }
        }
    }
    
    class SubpageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container: LinearLayout = view.findViewById(R.id.settingContainer)
        private val icon: ImageView = view.findViewById(R.id.settingIcon)
        private val title: TextView = view.findViewById(R.id.settingTitle)
        private val subtitle: TextView = view.findViewById(R.id.settingSubtitle)
        
        fun bind(item: SubpageItem, onClick: (SettingItem) -> Unit) {
            title.text = item.title
            subtitle.text = item.subtitle ?: ""
            subtitle.visibility = if (item.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
            
            item.icon?.let { iconName ->
                val iconRes = itemView.context.resources.getIdentifier(
                    iconName, "drawable", itemView.context.packageName
                )
                if (iconRes != 0) {
                    icon.setImageResource(iconRes)
                    icon.visibility = View.VISIBLE
                } else {
                    icon.visibility = View.GONE
                }
            } ?: run {
                icon.visibility = View.GONE
            }
            
            container.setOnClickListener { onClick(item) }
        }
    }
}
