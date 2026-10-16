package com.parem.launcher.ui

import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.parem.launcher.helper.CrashReporter
import com.parem.launcher.R
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.Constants
import com.parem.launcher.databinding.AdapterAppDrawerBinding
import com.parem.launcher.helper.AppIconCache
import com.parem.launcher.helper.DrawerRows
import com.parem.launcher.helper.IconPackManager
import com.parem.launcher.helper.OmniboxResolver
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.formattedTimeSpent
import com.parem.launcher.helper.SearchMatcher
import com.parem.launcher.helper.ShortcutMatcher

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val appClickListener: (AppModel) -> Unit,
    private val appLongPressListener: (AppModel) -> Unit,
) : ListAdapter<AppModel, AppDrawerAdapter.ViewHolder>(DIFF_CALLBACK), Filterable {

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem.appPackage == newItem.appPackage && oldItem.user == newItem.user &&
                    oldItem.shortcutId == newItem.shortcutId

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem == newItem
        }
    }

    @Volatile private var autoLaunch = true
    @Volatile private var isBangSearch = false
    // Main thread only. The same query can publish twice (a re-filter for
    // late usage stats or shortcuts); only the first may auto-launch.
    private var autoLaunchedQuery: String? = null

    /**
     * Builds the private-space header row (empty package, isPrivate, label =
     * its Lock/Unlock action), or null when there is none. Only the launch
     * drawer sets it; it is read on every rebuild so the label tracks the
     * current lock state.
     */
    var privateSpaceHeader: () -> AppModel? = { null }

    /** Extra veto for auto-launch, checked on the main thread right before firing. */
    var autoLaunchGuard: () -> Boolean = { true }
    private val appFilter = createAppFilter()
    private val myUserHandle = android.os.Process.myUserHandle()

    @Volatile var usageStats: Map<String, Long> = emptyMap()
    @Volatile var openCounts: Map<String, Int> = emptyMap()
    var sortByUsage: Boolean = false
    var showIcons: Boolean = false
    var iconPackPackage: String = ""

    @Volatile var appsList: MutableList<AppModel> = mutableListOf()
    @Volatile var appFilteredList: MutableList<AppModel> = mutableListOf()
    @Volatile private var labelKeys: Map<String, SearchMatcher.LabelKey> = emptyMap()

    /** App shortcuts (M4-WP14); only the launch drawer sets them. */
    @Volatile var shortcutRaws: List<ShortcutMatcher.Raw> = emptyList()
        set(value) {
            field = value
            rebuildShortcutEntries()
        }
    @Volatile private var shortcutEntries: List<ShortcutMatcher.Entry> = emptyList()
    @Volatile private var shortcutParents: Map<String, AppModel> = emptyMap()

    // The header shares the row layout; its own view type keeps recycled
    // header and app rows apart.
    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isPrivateHeader()) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            if (itemCount == 0 || position == RecyclerView.NO_POSITION) return
            val appModel = getItem(position)
            holder.bind(
                appLabelGravity,
                myUserHandle,
                appModel,
                appClickListener,
                appLongPressListener,
                usageStats,
                openCounts,
                showIcons,
                iconPackPackage
            )
        } catch (e: Exception) {
            CrashReporter.reportNonFatal("AppDrawerAdapter: Error binding view holder", e)
        }
    }

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                isBangSearch = charSearch?.startsWith("!") ?: false
                autoLaunch = charSearch?.startsWith(" ")?.not() ?: true

                val snapshot = appsList.toList()
                val statsSnapshot = usageStats

                val searchText = charSearch?.toString()?.trim() ?: ""
                var appFilteredList = (if (searchText.isBlank()) snapshot
                else snapshot.filter { app ->
                    appLabelMatches(app.appLabel, searchText)
                }).toMutableList()

                if (sortByUsage) {
                    // Usage stats need the usage-access permission; open counts are
                    // the launcher's own data and always available as a fallback
                    val counts = openCounts
                    if (statsSnapshot.isNotEmpty()) {
                        appFilteredList = appFilteredList.sortedByDescending { statsSnapshot[it.appPackage] ?: 0L }.toMutableList()
                    } else if (counts.isNotEmpty()) {
                        appFilteredList = appFilteredList.sortedByDescending { counts[it.appPackage] ?: 0 }.toMutableList()
                    }
                }

                // After the usage sort: shortcuts keep matcher order, under every app row
                if (flag == Constants.FLAG_LAUNCH_APP && searchText.isNotBlank()) {
                    val parents = shortcutParents
                    ShortcutMatcher.filter(shortcutEntries, searchText).mapNotNullTo(appFilteredList) { e ->
                        parents[e.raw.appKey]?.copy(
                            key = null, activityClassName = null, isNew = false,
                            shortcutId = e.raw.id, shortcutLabel = e.raw.shortLabel,
                        )
                    }
                }

                val filterResults = FilterResults()
                filterResults.values = appFilteredList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                results?.values?.let {
                    val items = (it as? MutableList<AppModel>) ?: (it as? List<AppModel>)?.toMutableList() ?: return
                    appFilteredList = decorate(items, constraint.isNullOrBlank())
                    val currentFiltered = appFilteredList.toList()
                    val query = constraint?.toString().orEmpty()
                    if (query != autoLaunchedQuery) autoLaunchedQuery = null
                    submitList(currentFiltered) {
                        autoLaunch(currentFiltered, query)
                    }
                }
            }
        }
    }

    private fun autoLaunch(filteredSnapshot: List<AppModel>, query: String) {
        try {
            // App rows only: a shortcut row never starts or stops auto-launch
            if (filteredSnapshot.count { it.shortcutId == null } == 1
                && query != autoLaunchedQuery
                && autoLaunch
                && isBangSearch.not()
                && flag == Constants.FLAG_LAUNCH_APP
                && filteredSnapshot.isNotEmpty()
                && !OmniboxResolver.blocksAutoLaunch(query)
                && autoLaunchGuard()
            ) {
                autoLaunchedQuery = query
                Handler(Looper.getMainLooper()).post {
                    try {
                        firstApp(filteredSnapshot, includeShortcuts = false)?.let(appClickListener)
                    } catch (e: Exception) {
                        CrashReporter.reportNonFatal("AppDrawerAdapter.autoLaunch", e)
                    }
                }
            }
        } catch (e: Exception) {
            CrashReporter.reportNonFatal("AppDrawerAdapter: Error during auto launch", e)
        }
    }

    private fun appLabelMatches(appLabel: String, charSearch: String): Boolean {
        val key = labelKeys[appLabel] ?: SearchMatcher.key(appLabel)
        return SearchMatcher.matches(appLabel, key, charSearch)
    }

    fun setAppList(appsList: MutableList<AppModel>) {
        // Must stay first: every picker observes the same appList as the launch drawer
        val list = DrawerRows.gate(appsList, flag == Constants.FLAG_LAUNCH_APP) { it.isPrivate }.toMutableList()
        this.appsList = list
        val newKeys = mutableMapOf<String, SearchMatcher.LabelKey>()
        for (app in list) {
            newKeys[app.appLabel] = SearchMatcher.key(app.appLabel)
        }
        labelKeys = newKeys
        rebuildShortcutEntries()
        if (sortByUsage && (usageStats.isNotEmpty() || openCounts.isNotEmpty())) {
            filter.filter("")
        } else {
            this.appFilteredList = decorate(list, searchBlank = true)
            submitList(appFilteredList.toList())
        }
    }

    /** Joins the raws with the gated list: hidden, locked and other users' apps drop out, renames apply. */
    private fun rebuildShortcutEntries() {
        val raws = shortcutRaws
        if (raws.isEmpty() && shortcutEntries.isEmpty()) return
        val parents = LinkedHashMap<String, AppModel>()
        for (app in appsList) {
            // A package with two launcher activities: the first row is the parent
            if (app.url == null && app.appPackage.isNotEmpty())
                parents.putIfAbsent(ShortcutMatcher.appKey(app.appPackage, app.user.toString()), app)
        }
        shortcutParents = parents
        shortcutEntries = ShortcutMatcher.entries(raws, parents.mapValues { it.value.appLabel })
    }

    /**
     * Rows as submitted: regular apps, the private-space header, private apps,
     * then the bottom-padding row. Header and padding only with a blank
     * search, as the padding row never matched a search before.
     */
    private fun decorate(apps: List<AppModel>, searchBlank: Boolean): MutableList<AppModel> {
        val header = if (searchBlank && flag == Constants.FLAG_LAUNCH_APP) privateSpaceHeader() else null
        val padding = if (searchBlank) AppModel("", null, "", "", false, myUserHandle) else null
        return DrawerRows.decorate(apps, { it.isPrivate }, header, padding) { it.shortcutId != null }.toMutableList()
    }

    // Header and padding rows have no package and are never launched
    private fun firstApp(rows: List<AppModel>, includeShortcuts: Boolean = true): AppModel? =
        rows.firstOrNull { it.appPackage.isNotEmpty() && (includeShortcuts || it.shortcutId == null) }

    fun launchFirstInList() {
        firstApp(appFilteredList)?.let(appClickListener)
    }

    class ViewHolder(private val binding: AdapterAppDrawerBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            appLabelGravity: Int,
            myUserHandle: UserHandle,
            appModel: AppModel,
            clickListener: (AppModel) -> Unit,
            longPressListener: (AppModel) -> Unit,
            usageStats: Map<String, Long>,
            openCounts: Map<String, Int>,
            showIcons: Boolean,
            iconPackPackage: String,
        ) =
            with(binding) {
                if (appModel.isPrivateHeader()) {
                    appTitle.visibility = View.VISIBLE
                    appTitle.text = root.context.getString(R.string.private_space)
                    appTitle.gravity = appLabelGravity
                    appTitle.setCompoundDrawablesRelative(null, null, null, null)
                    appTitle.setOnClickListener { clickListener(appModel) }
                    appTitle.setOnLongClickListener(null)
                    otherProfileIndicator.isVisible = false
                    appUsageTime.text = appModel.appLabel
                    appUsageTime.visibility = View.VISIBLE
                    return
                }
                if (appModel.appPackage.isEmpty()) {
                    appTitle.text = ""
                    appTitle.setOnClickListener(null)
                    appTitle.setOnLongClickListener(null)
                    appUsageTime.visibility = View.GONE
                    appTitle.setCompoundDrawablesRelative(null, null, null, null)
                    otherProfileIndicator.isVisible = false
                    return
                }
                appTitle.visibility = View.VISIBLE
                appTitle.text = appModel.appLabel + if (appModel.isNew == true) " ✦" else ""
                appTitle.gravity = appLabelGravity
                otherProfileIndicator.isVisible = appModel.user != myUserHandle

                // Show app icon if enabled
                if (showIcons && appModel.appPackage.isNotEmpty()) {
                    val iconSize = 20.dpToPx()
                    val icon = if (iconPackPackage.isNotEmpty()) {
                        IconPackManager.getIconForApp(root.context, iconPackPackage, appModel.appPackage, appModel.activityClassName)
                    } else null
                    val drawable = icon ?: AppIconCache.get(root.context, appModel.appPackage)
                    drawable?.setBounds(0, 0, iconSize, iconSize)
                    appTitle.setCompoundDrawablesRelative(drawable, null, null, null)
                    appTitle.compoundDrawablePadding = 8.dpToPx()
                } else {
                    appTitle.setCompoundDrawablesRelative(null, null, null, null)
                }

                // Hide, rename and uninstall would act on the parent app, so no menu
                if (appModel.shortcutId != null) {
                    appTitle.text = appModel.shortcutLabel
                    appUsageTime.text = appModel.appLabel
                    appUsageTime.visibility = View.VISIBLE
                    appTitle.setOnClickListener { clickListener(appModel) }
                    appTitle.setOnLongClickListener(null)
                    return
                }

                val timeMs = usageStats[appModel.appPackage] ?: 0L
                val opens = openCounts[appModel.appPackage] ?: 0
                if ((timeMs > 0 || opens > 0) && appModel.appPackage.isNotEmpty()) {
                    val timeText = if (timeMs > 0) root.context.formattedTimeSpent(timeMs) else ""
                    appUsageTime.text = when {
                        timeMs > 0 && opens > 0 -> "$timeText · ${opens}×"
                        opens > 0 -> "${opens}×"
                        else -> timeText
                    }
                    appUsageTime.visibility = View.VISIBLE
                } else {
                    appUsageTime.visibility = View.GONE
                }

                appTitle.setOnClickListener { clickListener(appModel) }
                appTitle.setOnLongClickListener {
                    longPressListener(appModel)
                    true
                }
            }
    }
}

/** The drawer's private-space header row (see [AppDrawerAdapter.privateSpaceHeader]). */
fun AppModel.isPrivateHeader(): Boolean = isPrivate && appPackage.isEmpty()
