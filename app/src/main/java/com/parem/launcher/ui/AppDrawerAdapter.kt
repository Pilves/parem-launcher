package com.parem.launcher.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.parem.launcher.R
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.Constants
import com.parem.launcher.databinding.AdapterAppDrawerBinding
import com.parem.launcher.helper.AppIconCache
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.helper.DrawerRows
import com.parem.launcher.helper.GrayscaleController
import com.parem.launcher.helper.IconPackManager
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.formattedTimeSpent
import com.parem.launcher.helper.hideKeyboard
import com.parem.launcher.helper.isSystemApp
import com.parem.launcher.helper.SearchMatcher
import com.parem.launcher.helper.ShortcutMatcher
import com.parem.launcher.helper.showKeyboard

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appHideListener: (AppModel, Int) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
    private val appGrayscaleListener: (AppModel) -> Unit,
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
                flag,
                appLabelGravity,
                myUserHandle,
                appModel,
                appClickListener,
                appDeleteListener,
                appInfoListener,
                appHideListener,
                appRenameListener,
                appGrayscaleListener,
                usageStats,
                openCounts,
                showIcons,
                iconPackPackage
            )
        } catch (e: Exception) {
            Log.e("AppDrawerAdapter", "Error binding view holder", e)
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
                && autoLaunchGuard()
            ) {
                autoLaunchedQuery = query
                Handler(Looper.getMainLooper()).post {
                    try { firstApp(filteredSnapshot, includeShortcuts = false)?.let(appClickListener) } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e("AppDrawerAdapter", "Error during auto launch", e)
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

    fun removeApp(position: Int) {
        if (position < 0 || position >= appFilteredList.size) return
        val app = appFilteredList[position]
        appFilteredList = appFilteredList.toMutableList().also { it.removeAt(position) }
        appsList = appsList.toMutableList().also { it.remove(app) }
        submitList(appFilteredList.toList())
    }

    class ViewHolder(private val binding: AdapterAppDrawerBinding) : RecyclerView.ViewHolder(binding.root) {

        private var currentTextWatcher: TextWatcher? = null

        fun bind(
            flag: Int,
            appLabelGravity: Int,
            myUserHandle: UserHandle,
            appModel: AppModel,
            clickListener: (AppModel) -> Unit,
            appDeleteListener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
            appHideListener: (AppModel, Int) -> Unit,
            appRenameListener: (AppModel, String) -> Unit,
            appGrayscaleListener: (AppModel) -> Unit,
            usageStats: Map<String, Long> = emptyMap(),
            openCounts: Map<String, Int> = emptyMap(),
            showIcons: Boolean = false,
            iconPackPackage: String = "",
        ) =
            with(binding) {
                if (appModel.isPrivateHeader()) {
                    appHideLayout.visibility = View.GONE
                    renameLayout.visibility = View.GONE
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
                    appHideLayout.visibility = View.GONE
                    renameLayout.visibility = View.GONE
                    return
                }
                appHideLayout.visibility = View.GONE
                renameLayout.visibility = View.GONE
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
                    if (appModel.appPackage.isNotEmpty()) {
                        appDelete.alpha = if (root.context.isSystemApp(appModel.appPackage)) 0.5f else 1.0f
                        appHide.text = if (flag == Constants.FLAG_HIDDEN_APPS)
                            root.context.getString(R.string.adapter_show)
                        else
                            root.context.getString(R.string.adapter_hide)
                        appTitle.visibility = View.INVISIBLE
                        appHideLayout.visibility = View.VISIBLE
                        // Hide, rename and time limit key on package (rename, limit) or
                        // write exported prefs; private rows offer none of them
                        appHide.isVisible = !appModel.isPrivate
                        appRename.isVisible = flag != Constants.FLAG_HIDDEN_APPS && !appModel.isPrivate
                        appBadHabit.isVisible = flag != Constants.FLAG_HIDDEN_APPS && !appModel.isPrivate
                        if (appBadHabit.isVisible) {
                            appBadHabit.text = if (AppLimitManager.hasLimit(root.context, appModel.appPackage))
                                root.context.getString(R.string.remove_time_limit)
                            else
                                root.context.getString(R.string.set_time_limit)
                        }
                        appGrayscale.isVisible = appBadHabit.isVisible
                        if (appGrayscale.isVisible) {
                            appGrayscale.text = root.context.getString(
                                if (GrayscaleController.isAppMarked(root.context, appModel.appPackage)) R.string.grayscale_app_off
                                else R.string.grayscale_app_on
                            )
                        }
                    }
                    true
                }
                appRename.setOnClickListener {
                    if (appModel.appPackage.isNotEmpty()) {
                        val appNameHint = getAppName(etAppRename.context, appModel.appPackage)
                        etAppRename.hint = appNameHint
                        etAppRename.setText(appModel.appLabel)
                        etAppRename.setSelectAllOnFocus(true)
                        renameLayout.visibility = View.VISIBLE
                        appHideLayout.visibility = View.GONE
                        etAppRename.showKeyboard()
                        etAppRename.imeOptions = EditorInfo.IME_ACTION_DONE;

                        currentTextWatcher?.let { etAppRename.removeTextChangedListener(it) }
                        val watcher = object : TextWatcher {
                            override fun afterTextChanged(s: Editable?) {
                                etAppRename.hint = appNameHint
                            }

                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int,
                            ) {
                            }

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int,
                            ) {
                                etAppRename.hint = ""
                            }
                        }
                        currentTextWatcher = watcher
                        etAppRename.addTextChangedListener(watcher)
                    }
                }
                etAppRename.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus)
                        appTitle.visibility = View.INVISIBLE
                    else
                        appTitle.visibility = View.VISIBLE
                }
                etAppRename.setOnEditorActionListener { _, actionCode, _ ->
                    if (actionCode == EditorInfo.IME_ACTION_DONE) {
                        val renameLabel = etAppRename.text.toString().trim()
                        if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                            appRenameListener(appModel, renameLabel)
                            renameLayout.visibility = View.GONE
                        }
                        true
                    } else {
                        false
                    }
                }
                tvSaveRename.setOnClickListener {
                    etAppRename.hideKeyboard()
                    val renameLabel = etAppRename.text.toString().trim()
                    if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                        appRenameListener(appModel, renameLabel)
                        renameLayout.visibility = View.GONE
                    } else {
                        val fallbackLabel = try {
                            val packageManager = etAppRename.context.packageManager
                            packageManager.getApplicationLabel(
                                packageManager.getApplicationInfo(appModel.appPackage, 0)
                            ).toString()
                        } catch (e: Exception) {
                            appModel.appPackage
                        }
                        appRenameListener(appModel, fallbackLabel)
                        renameLayout.visibility = View.GONE
                    }
                }
                appInfo.setOnClickListener { appInfoListener(appModel) }
                appBadHabit.setOnClickListener {
                    if (appModel.appPackage.isNotEmpty()) {
                        if (AppLimitManager.hasLimit(root.context, appModel.appPackage)) {
                            AppLimitManager.removeLimit(root.context, appModel.appPackage)
                            appHideLayout.visibility = View.GONE
                            appTitle.visibility = View.VISIBLE
                        } else {
                            BadHabitDialogs.showTimeLimitPicker(root.context, appModel.appPackage) {
                                appHideLayout.visibility = View.GONE
                                appTitle.visibility = View.VISIBLE
                            }
                        }
                    }
                }
                appGrayscale.setOnClickListener {
                    if (appModel.appPackage.isNotEmpty()) {
                        appGrayscaleListener(appModel)
                        appHideLayout.visibility = View.GONE
                        appTitle.visibility = View.VISIBLE
                    }
                }
                appDelete.setOnClickListener { appDeleteListener(appModel) }
                appMenuClose.setOnClickListener {
                    appHideLayout.visibility = View.GONE
                    appTitle.visibility = View.VISIBLE
                }
                appRenameClose.setOnClickListener {
                    renameLayout.visibility = View.GONE
                    appTitle.visibility = View.VISIBLE
                }
                appHide.setOnClickListener { appHideListener(appModel, bindingAdapterPosition) }
            }

        private fun getAppName(context: Context, appPackage: String): String {
            return try {
                val packageManager = context.packageManager
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(appPackage, 0)
                ).toString()
            } catch (e: Exception) {
                appPackage
            }
        }

    }
}

/** The drawer's private-space header row (see [AppDrawerAdapter.privateSpaceHeader]). */
fun AppModel.isPrivateHeader(): Boolean = isPrivate && appPackage.isEmpty()
