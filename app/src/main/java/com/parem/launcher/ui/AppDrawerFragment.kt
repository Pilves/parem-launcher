package com.parem.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.graphics.Canvas
import android.widget.EdgeEffect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parem.launcher.helper.CrashReporter
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentAppDrawerBinding
import com.parem.launcher.data.AppModel
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.helper.AppOpenCounter
import com.parem.launcher.helper.ContactMatcher
import com.parem.launcher.helper.ContactSearchManager
import com.parem.launcher.helper.SearchHints
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.copyToClipboard
import com.parem.launcher.helper.CurrencyRates
import com.parem.launcher.helper.OmniboxHistory
import com.parem.launcher.helper.OmniboxMode
import com.parem.launcher.helper.OmniboxResolver
import com.parem.launcher.helper.QuickAction
import com.parem.launcher.helper.QuickActionParser
import com.parem.launcher.helper.SettingsSearch
import com.parem.launcher.helper.fireQuickAction
import com.parem.launcher.helper.quickActionPreview
import com.parem.launcher.helper.getShortcutRaws
import com.parem.launcher.helper.HiddenAppHint
import com.parem.launcher.helper.HiddenLabelLoad
import com.parem.launcher.helper.PackageChangeTracker
import com.parem.launcher.helper.getHiddenAppsForSearch
import com.parem.launcher.helper.hideKeyboard
import com.parem.launcher.helper.skipAnimations
import com.parem.launcher.helper.openSearch
import com.parem.launcher.helper.privateProfile
import com.parem.launcher.helper.privateSpaceEntryHidden
import com.parem.launcher.helper.DrawerPull
import com.parem.launcher.helper.DrawerRows
import com.parem.launcher.helper.padForSystemBars
import com.parem.launcher.helper.showKeyboard
import com.parem.launcher.helper.showToast
import com.parem.launcher.ui.settings.SettingsSearchIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.core.os.bundleOf


class AppDrawerFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var recentAdapter: RecentHistoryAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    private var flag = Constants.FLAG_LAUNCH_APP
    private var gestureLetter: Char? = null
    private var canRename = false
    // Key typed on a hardware keyboard at home; seeds the search once the app
    // list is in (filtering an empty list would flash "no apps found")
    private var typedQuery = ""
    private var revealPrivateSpace = false
    // Opening the drawer also reloads the list, and setAppList shows it whole,
    // so a seeded query is re-applied when the fresh list lands. Cleared on
    // hide/rename/uninstall: their reload could narrow to one match and auto-launch it.
    private var reapplyQueryOnReload = false
    private var scrollListener: RecyclerView.OnScrollListener? = null
    private var searchTextView: TextView? = null
    private var cachedIsCjkKeyboard: Boolean? = null

    // Omnibox state: the search field doubles as an omnibox. Exactly one mode is
    // active at a time — arithmetic, unit conversion, dialable number, or web
    // search (leading space) — or None for ordinary app search.
    private var omniboxMode: OmniboxMode = OmniboxMode.None

    // Loaded once per drawer session, only when contact search is enabled and
    // permitted (see loadContactsIfEnabled). Empty otherwise → no matching runs.
    private var contacts: List<ContactMatcher.Contact> = emptyList()

    // Built on the first query; only the launch drawer resolves omnibox modes.
    private var settingsRows: List<SettingsSearch.Row>? = null

    // Labels protect web searches and drive the empty-result hint; never shown.
    private val hiddenLabels = HiddenLabelLoad()
    private var hiddenLabelsLoading = false
    private var hiddenHintShown = false
    private var pendingSubmit: String? = null

    // At most one ECB rates fetch per drawer session, started by the first
    // currency query; the in-flight flag picks the CurrencyNoRates tip.
    private var currencyFetchStarted = false
    private var currencyFetchInFlight = false

    private val viewModel: MainViewModel by activityViewModels()

    // The installer sets RESULT_OK only after PackageManager has removed the app,
    // so this live reload can't re-cache the old list the way a reload on
    // regaining focus could, and it doesn't wait for the LauncherApps callback,
    // which one phone missed until a reboot (M6-WP16).
    private val uninstallLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            PackageChangeTracker.invalidate()
            reloadAfterMenuChange()
        }
    }

    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        arguments?.let {
            flag = it.getInt(Constants.Key.FLAG, Constants.FLAG_LAUNCH_APP)
            canRename = it.getBoolean(Constants.Key.RENAME, false)
            gestureLetter = if (it.containsKey(Constants.Key.LETTER)) it.getChar(Constants.Key.LETTER) else null
            if (savedInstanceState == null)
                typedQuery = it.getString(Constants.Key.QUERY).orEmpty()
        }
        binding.root.padForSystemBars(ime = true)
        initViews()
        initSearch()
        initAdapter()
        initObservers()
        initClickListeners()
        loadContactsIfEnabled()
        loadShortcuts()
    }

    /** App shortcuts (M4-WP14): empty unless Parem is the default home. */
    private fun loadShortcuts() {
        if (flag != Constants.FLAG_LAUNCH_APP) return
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val raws = getShortcutRaws(appContext)
            val b = _binding ?: return@launch
            if (!isAdded || raws.isEmpty()) return@launch
            adapter.shortcutRaws = raws
            // Shortcuts can't change the app-row count, so a one-app result
            // has already auto-launched; re-filtering it would fire it again
            val query = b.search.query
            if (!query.isNullOrBlank() && adapter.currentList.count { it.shortcutId == null } != 1)
                adapter.filter.filter(query)
        }
    }

    /**
     * Loads contacts into memory once, but only when the drawer is a launcher and
     * the opt-in feature is active (enabled + permission granted). This is the
     * only entry point to contact loading, so a disabled toggle means the
     * provider is never queried.
     */
    private fun loadContactsIfEnabled() {
        if (flag != Constants.FLAG_LAUNCH_APP) return
        if (!ContactSearchManager.isActive(requireContext())) return
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { ContactSearchManager.loadContacts(appContext) }
            if (!isAdded || _binding == null) return@launch
            contacts = loaded
        }
    }

    private fun initViews() {
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP)
            binding.search.queryHint = getString(R.string.select_an_app)
        else if (flag == Constants.FLAG_LAUNCH_APP) {
            // The omnibox's only in-app mention: a different example each open, until it has sunk in
            val opens = prefs.searchHintOpens
            binding.search.queryHint = SearchHints.forOpen(opens, resources.getStringArray(R.array.omnibox_hints))
            if (opens < SearchHints.MAX_OPENS) prefs.searchHintOpens = opens + 1
        }
        try {
            searchTextView = binding.search.findViewById(R.id.search_src_text)
            searchTextView?.gravity = prefs.appLabelAlignment
            // The rotating omnibox hint is an example, not a label; TalkBack needs the field's purpose
            searchTextView?.contentDescription = getString(R.string.search_apps)
        } catch (e: Exception) {
            CrashReporter.reportNonFatal("AppDrawerFragment: Failed to set search text alignment", e)
        }
    }

    /**
     * While a CJK IME is composing (e.g. typing pinyin before selecting a character),
     * the search field holds a composing region and the letters are not a final query —
     * don't auto-launch then. Gated to CJK keyboards because some Latin keyboards
     * (SwiftKey) keep a composing region for ordinary typing. (Upstream fixes #629/#694.)
     */
    private fun isSearchComposing(): Boolean {
        val text = searchTextView?.text
        if (text !is android.text.Spannable) return false
        val start = android.view.inputmethod.BaseInputConnection.getComposingSpanStart(text)
        val end = android.view.inputmethod.BaseInputConnection.getComposingSpanEnd(text)
        if (start !in 0 until end) return false
        return isCjkKeyboard()
    }

    /** TalkBack-like state: touch exploration, or any spoken-feedback service, is on. */
    private fun isScreenReaderOn(): Boolean {
        val am = context?.getSystemService(android.view.accessibility.AccessibilityManager::class.java) ?: return false
        return am.isTouchExplorationEnabled ||
            am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN).isNotEmpty()
    }

    private fun isCjkKeyboard(): Boolean {
        cachedIsCjkKeyboard?.let { return it }
        val result = try {
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val subtype = imm.currentInputMethodSubtype
            val language = when {
                subtype == null -> ""
                subtype.languageTag.isNotEmpty() -> subtype.languageTag
                else -> @Suppress("DEPRECATION") subtype.locale
            }
            language.startsWith("zh") || language.startsWith("ja") || language.startsWith("ko")
        } catch (e: Exception) {
            CrashReporter.reportNonFatal("AppDrawerFragment", e)
            false
        }
        cachedIsCjkKeyboard = result
        return result
    }

    private fun initSearch() {
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val ctx = context ?: return true
                val q = query ?: ""
                // Explicit Enter owns this query, including any auto-launch already posted.
                adapter.suppressAutoLaunchForQuery(q)
                // Filtering and list diffs finish asynchronously; old rows must never handle Enter.
                if ((omniboxMode == OmniboxMode.None || omniboxMode is OmniboxMode.Setting) &&
                    !adapter.isQueryReady(q)) {
                    pendingSubmit = q
                    return true
                }
                if (flag == Constants.FLAG_LAUNCH_APP && omniboxMode == OmniboxMode.None &&
                    (q.startsWith("!") || adapter.itemCount == 0) && currentHiddenLabels() == null) {
                    // A failed, incomplete or stale lookup stays closed; Enter retries it.
                    pendingSubmit = q
                    if (!hiddenLabelsLoading || hiddenLabels.isStale(prefs.hiddenApps, PackageChangeTracker.stamp())) loadHiddenLabels()
                    if (pendingSubmit != null) ctx.showToast(getString(R.string.checking_hidden_apps))
                    return true
                }
                // Calls stay out of history (a number or contact name is not a
                // query); a plain search that launches an app records the launch
                val isQuery = when (omniboxMode) {
                    is OmniboxMode.Calc, is OmniboxMode.Conversion, is OmniboxMode.Currency,
                    OmniboxMode.WebSearch -> true
                    OmniboxMode.None -> q.startsWith("!") || adapter.itemCount == 0
                    else -> false
                }
                if (isQuery) recordHistory(OmniboxHistory.Entry.Query(q))
                when (val mode = omniboxMode) {
                    is OmniboxMode.Calc -> copyResult(mode.result)
                    is OmniboxMode.Conversion -> copyResult(mode.result)
                    is OmniboxMode.Currency -> copyResult(mode.result)
                    OmniboxMode.CurrencyNoRates -> {}
                    is OmniboxMode.Dial -> dial(ctx, mode.number)
                    is OmniboxMode.QuickAction -> runQuickAction(mode.action)
                    is OmniboxMode.Contact -> dial(ctx, mode.number)
                    OmniboxMode.WebSearch ->
                        launchUrl(ctx, Constants.URL_GOOGLE_SEARCH + java.net.URLEncoder.encode(q.trim(), "UTF-8"))
                    is OmniboxMode.Setting ->
                        if (adapter.itemCount == 0) openSetting(mode.anchor) else adapter.launchFirstInList()
                    OmniboxMode.None -> when {
                        flag == Constants.FLAG_LAUNCH_APP && !hiddenHintShown && HiddenAppHint.canSearchWeb(q, currentHiddenLabels()) && q.startsWith("!") ->
                            launchUrl(ctx, Constants.URL_DUCK_SEARCH + java.net.URLEncoder.encode(q, "UTF-8"), q)
                        flag == Constants.FLAG_LAUNCH_APP && !hiddenHintShown && HiddenAppHint.canSearchWeb(q, currentHiddenLabels()) && adapter.itemCount == 0 -> BadHabitDialogs.gateWebOpen(
                            ctx, viewLifecycleOwner.lifecycleScope, { isAdded },
                            Intent(Intent.ACTION_WEB_SEARCH), open = { browser ->
                                if (canOpenImplicitSearch(q)) ctx.openSearch(q.trim(), browser)
                            },
                        )
                        else -> adapter.launchFirstInList()
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (newText.isNotBlank()) revealPrivateSpace = false
                if (pendingSubmit != newText) pendingSubmit = null
                _binding?.appRename?.visibility = if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                updateOmniboxState(newText)
                refreshRecent()
                // No debounce: matching against precomputed keys is sub-millisecond
                // and Filter supersedes stale requests itself — instant beats smooth
                try {
                    adapter.filter.filter(newText)
                } catch (e: Exception) {
                    CrashReporter.reportNonFatal("AppDrawerFragment: Error filtering app list", e)
                }
                return true
            }
        })
    }

    /**
     * The search field doubles as an omnibox: arithmetic and unit conversions
     * show a live result (tap or submit to copy), and a leading space turns
     * the query into a Google search on submit.
     */
    private fun updateOmniboxState(newText: String) {
        val b = _binding ?: return
        omniboxMode = OmniboxMode.None
        // Omnibox modes only make sense when the drawer is a launcher, not when
        // it is open as an app picker (set home app / swipe app / etc.)
        if (flag != Constants.FLAG_LAUNCH_APP) return
        val rows = settingsRows ?: SettingsSearchIndex.rows(b.root.context).also { settingsRows = it }
        omniboxMode = OmniboxResolver.resolve(newText, contacts, CurrencyRates.cached(b.root.context), rows)
        if (omniboxMode is OmniboxMode.Currency || omniboxMode == OmniboxMode.CurrencyNoRates)
            fetchCurrencyRatesOnce()
        val tip = when (val mode = omniboxMode) {
            is OmniboxMode.Calc -> "= ${mode.result}"
            is OmniboxMode.Conversion -> "= ${mode.result}"
            is OmniboxMode.Currency -> getString(R.string.currency_hint, mode.result, mode.date)
            OmniboxMode.CurrencyNoRates -> getString(
                if (currencyFetchInFlight) R.string.currency_downloading else R.string.currency_unavailable
            )
            is OmniboxMode.Dial -> getString(R.string.call_hint, mode.number)
            OmniboxMode.WebSearch -> getString(R.string.google_search_hint, newText.trim())
            is OmniboxMode.QuickAction -> quickActionPreview(b.root.context, mode.action)
            is OmniboxMode.Contact -> getString(R.string.contact_hint, mode.name, mode.number)
            is OmniboxMode.Setting -> getString(R.string.setting_hint, mode.title)
            OmniboxMode.None -> null
        }
        if (tip != null) b.appDrawerTip.text = tip
        b.appDrawerTip.visibility = if (tip != null) View.VISIBLE else View.GONE
    }

    private fun refreshEmptyResultTip() {
        val b = _binding ?: return
        if (omniboxMode != OmniboxMode.None) return
        val query = b.search.query.toString()
        val emptyTip = if (flag == Constants.FLAG_LAUNCH_APP)
            HiddenAppHint.emptyTip(query, isPrivateSpaceLocked(), currentHiddenLabels())
        else HiddenAppHint.EmptyTip.NO_APPS
        hiddenHintShown = flag == Constants.FLAG_LAUNCH_APP && adapter.itemCount == 0 &&
            emptyTip == HiddenAppHint.EmptyTip.HIDDEN_APPS
        // Picker drawers only pick an app, so a tip there must not promise a search
        val searches = flag == Constants.FLAG_LAUNCH_APP
        val bang = searches && query.startsWith("!") && query.length > 1
        if (bang || (adapter.itemCount == 0 && query.isNotBlank())) {
            b.appDrawerTip.text = when {
                hiddenHintShown -> getString(R.string.hidden_apps_hint)
                searches && !HiddenAppHint.canSearchWeb(query, currentHiddenLabels()) -> getString(R.string.no_apps_found)
                bang -> getString(R.string.duck_search_hint, query)
                searches -> getString(R.string.web_search_hint, query.trim())
                else -> getString(R.string.no_apps_found)
            }
            b.appDrawerTip.visibility = View.VISIBLE
        } else {
            b.appDrawerTip.visibility = View.GONE
        }
    }

    private fun currentHiddenLabels(): List<String>? =
        hiddenLabels.labelsFor(prefs.hiddenApps, PackageChangeTracker.stamp())

    private fun canOpenImplicitSearch(query: String): Boolean {
        if (!isAdded || _binding == null) return false
        val labels = currentHiddenLabels()
        if (labels == null) {
            if (!hiddenLabelsLoading || hiddenLabels.isStale(prefs.hiddenApps, PackageChangeTracker.stamp()))
                loadHiddenLabels()
            requireContext().showToast(getString(R.string.checking_hidden_apps))
        }
        return HiddenAppHint.canSearchWeb(query, labels)
    }

    private fun loadHiddenLabels() {
        val hiddenKeys = prefs.hiddenApps
        val packageStamp = PackageChangeTracker.stamp()
        val generation = hiddenLabels.begin(hiddenKeys, packageStamp)
        hiddenLabelsLoading = hiddenKeys.isNotEmpty()
        if (hiddenKeys.isEmpty()) {
            resumePendingSubmit()
            return
        }
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val apps = getHiddenAppsForSearch(appContext, prefs)
            if (!isAdded || _binding == null || !hiddenLabels.isCurrent(generation)) return@launch
            if (hiddenLabels.isStale(prefs.hiddenApps, PackageChangeTracker.stamp())) {
                loadHiddenLabels()
                return@launch
            }
            hiddenLabels.complete(generation, prefs.hiddenApps, apps?.map { it.appLabel }, PackageChangeTracker.stamp())
            hiddenLabelsLoading = false
            if (binding.search.query.isNotBlank()) refreshEmptyResultTip()
            if (pendingSubmit != null && apps == null) {
                pendingSubmit = null
                requireContext().showToast(getString(R.string.hidden_apps_check_failed))
            } else resumePendingSubmit()
        }
    }

    private fun resumePendingSubmit() {
        val b = _binding ?: return
        val query = pendingSubmit ?: return
        if (!isAdded || b.search.query.toString() != query || !adapter.isQueryReady(query)) return
        pendingSubmit = null
        b.search.setQuery(query, true)
    }

    /** Locked means a private profile exists and is in quiet mode; no profile is not locked. */
    private fun isPrivateSpaceLocked(): Boolean {
        val profile = context?.let { privateProfile(it) } ?: return false
        return isQuietMode(profile)
    }

    private fun openHiddenApps() {
        if (!isAdded) return
        viewModel.getHiddenApps()
        findNavController().navigate(R.id.appListFragment, bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS))
    }

    /** fetchIfDue is a no-op when the cache is fresh; the re-resolve shows new rates without retyping. */
    private fun fetchCurrencyRatesOnce() {
        if (currencyFetchStarted) return
        currencyFetchStarted = true
        currencyFetchInFlight = true
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            CurrencyRates.fetchIfDue(appContext)
            currencyFetchInFlight = false
            val b = _binding ?: return@launch
            if (!isAdded) return@launch
            // Only a still-showing currency tip is refreshed; other tips (e.g.
            // "no apps found") are owned by the filter callback.
            if (omniboxMode is OmniboxMode.Currency || omniboxMode == OmniboxMode.CurrencyNoRates)
                updateOmniboxState(b.search.query.toString())
        }
    }

    private fun onAppClick(app: AppModel, sourceView: View? = null) {
        val sourceBounds = sourceView?.let { android.graphics.Rect().also { bounds -> it.getGlobalVisibleRect(bounds) } }
        if (!isAdded) return
        if (app.isPrivateHeader()) {
            val locked = isQuietMode(app.user)
            if (_binding?.search?.query?.isNotBlank() == true) {
                if (locked) viewModel.setPrivateLocked(false)
                else {
                    revealPrivateSpace = true
                    binding.search.setQuery("", false)
                }
            } else {
                viewModel.setPrivateLocked(!locked)
            }
            return
        }
        if (app.appPackage.isEmpty())
            return
        if ((flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
            && checkBadHabitAndLaunch(app, sourceBounds)
        ) {
            // Bad habit check is handling launch asynchronously
        } else {
            recordLaunch(app)
            viewModel.selectedApp(app, flag, gestureLetter, sourceBounds)
            if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                findNavController().popBackStack(R.id.mainFragment, false)
            else
                findNavController().popBackStack()
        }
    }

    // Only the launch drawer feeds history: pickers aren't launches, and the
    // hidden-apps drawer and private space would leak what they keep out of sight.
    private fun recordLaunch(app: AppModel) {
        if (flag == Constants.FLAG_LAUNCH_APP && !app.isPrivate)
            recordHistory(OmniboxHistory.Entry.App(historyKey(app)))
    }

    private fun recordHistory(entry: OmniboxHistory.Entry) {
        if (flag != Constants.FLAG_LAUNCH_APP || !prefs.omniboxHistoryEnabled) return
        prefs.omniboxHistory = OmniboxHistory.encode(
            OmniboxHistory.push(OmniboxHistory.decode(prefs.omniboxHistory), entry)
        )
    }

    private fun historyKey(app: AppModel) = app.appPackage + "|" + app.user.toString()

    /**
     * Recent launches and queries while the search is empty. Launches resolve
     * against the drawer's current list, so uninstalled, hidden and locked
     * private apps drop out and renamed apps show their current label.
     */
    private fun refreshRecent() {
        val b = _binding ?: return
        if (!::recentAdapter.isInitialized) return
        val show = flag == Constants.FLAG_LAUNCH_APP && prefs.omniboxHistoryEnabled && b.search.query.isNullOrBlank()
        val entries = if (show) OmniboxHistory.decode(prefs.omniboxHistory) else emptyList()
        val apps = if (entries.isEmpty()) emptyMap()
            else adapter.appsList.filter { !it.isPrivate && it.appPackage.isNotEmpty() }.associateBy(::historyKey)
        recentAdapter.submit(entries.mapNotNull {
            when (it) {
                is OmniboxHistory.Entry.App -> apps[it.key]?.let(RecentHistoryAdapter.Row::App)
                is OmniboxHistory.Entry.Query -> RecentHistoryAdapter.Row.Query(it.text)
            }
        })
    }

    private fun reloadAfterMenuChange() {
        if (!isAdded || _binding == null) return
        reapplyQueryOnReload = false
        viewModel.getAppList()
        if (flag == Constants.FLAG_LAUNCH_APP) loadHiddenLabels()
        viewModel.getHiddenApps()
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            appClickListener = { app, view -> onAppClick(app, view) },
            appLongPressListener = { app ->
                if (isAdded && _binding != null) {
                    _binding?.search?.hideKeyboard()
                    AppMenu.show(this, app, prefs, viewModel, hidden = flag == Constants.FLAG_HIDDEN_APPS,
                        uninstall = uninstallLauncher) { reloadAfterMenuChange() }
                }
            }
        )
        adapter.onFilterCommitted = onFilterCommitted@{ query ->
            if (_binding?.search?.query?.toString() == query) {
                if (revealPrivateSpace) {
                    val profile = context?.let { privateProfile(it) }
                    if (profile != null && !isQuietMode(profile)) {
                        if (query.isNotBlank()) {
                            binding.search.setQuery("", false)
                            return@onFilterCommitted
                        }
                        val index = adapter.currentList.indexOfFirst { it.isPrivateHeader() }
                        if (index >= 0) {
                            revealPrivateSpace = false
                            binding.search.hideKeyboard()
                            linearLayoutManager.scrollToPositionWithOffset(recentAdapter.itemCount + index, 0)
                        }
                    }
                }
                refreshEmptyResultTip()
                resumePendingSubmit()
            }
        }
        adapter.showIcons = prefs.showIcons
        adapter.iconPackPackage = prefs.iconPackPackage
        adapter.openCounts = AppOpenCounter.getCounts(requireContext())
        // Read when the filter publishes, so fast typing only makes it stricter
        adapter.autoLaunchGuard = { query ->
            val text = searchTextView?.text?.toString().orEmpty()
            text == query && !isSearchComposing() && !QuickActionParser.isActionPrefix(text) &&
                OmniboxResolver.autoLaunchAllowed(text, prefs.autoLaunchSingleMatch, isScreenReaderOn())
        }
        if (flag == Constants.FLAG_LAUNCH_APP) {
            // Re-read profile and system visibility on every rebuild, including search.
            adapter.privateSpaceHeader = header@{ query ->
                if (!isAdded || _binding == null) return@header null
                val profile = privateProfile(requireContext()) ?: return@header null
                val locked = isQuietMode(profile)
                if (!DrawerRows.showPrivateEntry(query, getString(R.string.private_space), locked,
                        privateSpaceEntryHidden(requireContext(), profile))) return@header null
                AppModel(
                    getString(when {
                        locked -> R.string.private_space_unlock
                        query.isNotBlank() -> R.string.private_space_open
                        else -> R.string.private_space_lock
                    }),
                    null, "", null, false, profile, isPrivate = true
                )
            }
        }

        linearLayoutManager = LinearLayoutManager(requireContext())
        val recycler = binding.recyclerView
        var released = false
        recycler.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL -> released = false
                    MotionEvent.ACTION_UP -> released = true
                }
                return false
            }
        })
        recycler.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> released = true
                MotionEvent.ACTION_CANCEL -> released = false
            }
            false
        }
        recycler.overScrollMode = View.OVER_SCROLL_ALWAYS
        recycler.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                if (direction != DIRECTION_TOP) {
                    if (!view.context.skipAnimations()) return super.createEdgeEffect(view, direction)
                    return object : EdgeEffect(view.context) {
                        override fun draw(canvas: Canvas): Boolean = false
                    }
                }
                return object : EdgeEffect(view.context) {
                    private val threshold = 64f * resources.displayMetrics.density
                    private val pullState = DrawerPull(threshold)
                    private fun pull(delta: Float) {
                        if (view.scrollState != RecyclerView.SCROLL_STATE_DRAGGING) return
                        if (pullState.pull(delta * view.height)) {
                            view.performHapticFeedback(
                                if (android.os.Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                                else HapticFeedbackConstants.CLOCK_TICK
                            )
                        }
                        if (!view.context.skipAnimations()) {
                            view.animate().cancel()
                            view.translationY = (pullState.distance * 0.35f).coerceAtMost(threshold)
                        }
                    }
                    override fun onPull(deltaDistance: Float) = pull(deltaDistance)
                    override fun onPull(deltaDistance: Float, displacement: Float) = pull(deltaDistance)
                    override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
                        val before = pullState.distance
                        pull(deltaDistance)
                        return if (view.height > 0) (pullState.distance - before) / view.height else 0f
                    }
                    override fun isFinished(): Boolean = pullState.distance <= 0f
                    override fun getDistance(): Float = if (view.height > 0) pullState.distance / view.height else 0f
                    override fun onAbsorb(velocity: Int) { pullState.release(false) }
                    override fun onRelease() {
                        val close = pullState.release(released)
                        released = false
                        if (view.context.skipAnimations()) view.translationY = 0f
                        else view.animate().translationY(0f).setDuration(150).start()
                        if (close && isAdded && _binding != null) findNavController().popBackStack()
                    }
                    override fun draw(canvas: Canvas): Boolean = false
                }
            }
        }

        recentAdapter = RecentHistoryAdapter(
            prefs.appLabelAlignment,
            onApp = { onAppClick(it) },
            onQuery = { _binding?.search?.setQuery(it, false) },
        )

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = ConcatAdapter(recentAdapter, adapter)
        scrollListener = getRecyclerViewOnScrollListener()
        binding.recyclerView.addOnScrollListener(scrollListener!!)
        binding.recyclerView.itemAnimator = null
    }

    private fun initObservers() {
        viewModel.firstOpen.observe(viewLifecycleOwner) {
            if (it && flag == Constants.FLAG_LAUNCH_APP) {
                binding.appDrawerTip.visibility = View.VISIBLE
                binding.appDrawerTip.isSelected = true
            }
        }
        val wantSortByUsage = requireContext().appUsagePermissionGranted()
                && prefs.appDrawerSortByUsage
        adapter.sortByUsage = wantSortByUsage

        // Load cached usage stats immediately so sort works on first render
        if (wantSortByUsage) {
            val cached = prefs.getCachedUsageStats()
            if (cached.isNotEmpty()) adapter.usageStats = cached
        }

        if (flag == Constants.FLAG_HIDDEN_APPS) {
            // A recreated ViewModel (process death, "Don't keep activities") has
            // never loaded this list; only Settings fills it before navigating
            if (viewModel.hiddenApps.value == null) viewModel.getHiddenApps()
            viewModel.hiddenApps.observe(viewLifecycleOwner) {
                it?.let {
                    adapter.setAppList(it.toMutableList())
                    if (binding.search.query.isNotBlank()) adapter.filter.filter(binding.search.query)
                }
            }
        } else {
            if (flag == Constants.FLAG_LAUNCH_APP) {
                loadHiddenLabels()
            }
            viewModel.appList.observe(viewLifecycleOwner) {
                it?.let { appModels ->
                    adapter.setAppList(appModels.toMutableList())
                    refreshRecent()
                    if (typedQuery.isNotEmpty()) {
                        // Keys typed after the drawer took focus follow the first one
                        binding.search.setQuery(typedQuery + binding.search.query, false)
                        typedQuery = ""
                        reapplyQueryOnReload = true
                    } else if (binding.search.query.isNotBlank()) {
                        if (!reapplyQueryOnReload)
                            adapter.suppressAutoLaunchForQuery(binding.search.query.toString())
                        adapter.filter.filter(binding.search.query)
                    }
                }
            }
        }
        if (requireContext().appUsagePermissionGranted()) {
            viewModel.perAppScreenTime.observe(viewLifecycleOwner) { stats ->
                adapter.usageStats = stats
                prefs.setCachedUsageStats(stats)
                adapter.filter.filter(binding.search.query)
            }
            viewModel.getPerAppScreenTime()
        }
    }

    private fun tipRepeatsEnter(): Boolean = when (omniboxMode) {
        is OmniboxMode.Dial, OmniboxMode.WebSearch -> true
        OmniboxMode.None -> !hiddenHintShown && flag == Constants.FLAG_LAUNCH_APP &&
            binding.search.query.isNotBlank() && HiddenAppHint.canSearchWeb(binding.search.query.toString(), currentHiddenLabels())
        else -> false
    }

    private fun initClickListeners() {
        binding.appDrawerTip.setOnClickListener {
            if (tipRepeatsEnter()) {
                binding.search.setQuery(binding.search.query, true)
                return@setOnClickListener
            }
            when (val mode = omniboxMode) {
                is OmniboxMode.Calc -> {
                    copyResult(mode.result)
                    return@setOnClickListener
                }
                is OmniboxMode.Conversion -> {
                    copyResult(mode.result)
                    return@setOnClickListener
                }
                is OmniboxMode.Currency -> {
                    copyResult(mode.result)
                    return@setOnClickListener
                }
                is OmniboxMode.Contact -> {
                    dial(requireContext(), mode.number)
                    return@setOnClickListener
                }
                is OmniboxMode.QuickAction -> {
                    runQuickAction(mode.action)
                    return@setOnClickListener
                }
                is OmniboxMode.Setting -> {
                    openSetting(mode.anchor)
                    return@setOnClickListener
                }
                OmniboxMode.None -> if (hiddenHintShown) {
                    openHiddenApps()
                    return@setOnClickListener
                }
                else -> {}
            }
            binding.appDrawerTip.isSelected = false
            binding.appDrawerTip.isSelected = true
        }
        binding.appRename.setOnClickListener {
            val name = binding.search.query.toString().trim()
            if (name.isEmpty()) {
                requireContext().showToast(getString(R.string.type_a_new_app_name_first))
                binding.search.showKeyboard()
                return@setOnClickListener
            }

            if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8) {
                prefs.setHomeAppName(flag, name)
            }
            findNavController().popBackStack()
        }
    }

    private fun getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {

            var onTop = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {

                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        onTop = !recyclerView.canScrollVertically(-1)
                        if (onTop)
                            _binding?.search?.hideKeyboard()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1))
                            _binding?.search?.hideKeyboard()
                        else if (!recyclerView.canScrollVertically(-1))
                            // A d-pad focused row reaching the top keeps its focus
                            if (!onTop && isRemoving.not() && recyclerView.focusedChild == null)
                                _binding?.search?.showKeyboard(prefs.autoShowKeyboard)
                    }
                }
            }
        }
    }

    private fun copyResult(result: String) {
        requireContext().copyToClipboard(result)
        _binding?.appDrawerTip?.let(Motion::copied)
        requireContext().showToast(getString(R.string.copied))
    }

    // The action pops the drawer, so Back from Settings returns home
    private fun openSetting(anchor: Int) {
        if (!isAdded) return
        findNavController().navigate(
            R.id.action_appListFragment_to_settingsFragment2,
            bundleOf(Constants.Key.SETTING to anchor),
        )
    }

    /**
     * Not an app launch: Focus and the mindful pause don't apply, like Dial.
     * Alarm and timer skip the clock's UI, so the drawer closes itself; the
     * calendar editor opens on top and Back from it lands home.
     */
    private fun runQuickAction(action: QuickAction) {
        val ctx = context ?: return
        if (!fireQuickAction(ctx, action)) {
            ctx.showToast(getString(R.string.quick_action_no_app))
            return
        }
        if (action !is QuickAction.Event) ctx.showToast(getString(R.string.quick_action_sent))
        findNavController().popBackStack(R.id.mainFragment, false)
    }

    private fun dial(ctx: android.content.Context, number: String) {
        try {
            startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_DIAL,
                    android.net.Uri.parse("tel:" + number.replace(" ", ""))
                )
            )
        } catch (e: Exception) {
            CrashReporter.reportNonFatal("AppDrawerFragment", e)
            ctx.showToast(getString(R.string.unable_to_open_app))
        }
    }

    /**
     * Omnibox web opens take the website-slot route: gated on the handling app,
     * then MainViewModel.selectedApp, which also pins a whitelisted browser under focus.
     */
    private fun launchUrl(ctx: Context, url: String, implicitSearch: String? = null) {
        BadHabitDialogs.gateWebOpen(
            ctx, viewLifecycleOwner.lifecycleScope, { isAdded },
            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            open = { browser ->
                if (isAdded && (implicitSearch == null || canOpenImplicitSearch(implicitSearch))) viewModel.selectedApp(
                    AppModel(url, null, browser, null, false, android.os.Process.myUserHandle(), url),
                    Constants.FLAG_LAUNCH_APP
                )
            },
        )
    }

    private fun isQuietMode(profile: android.os.UserHandle): Boolean = try {
        (requireContext().getSystemService(android.content.Context.USER_SERVICE) as android.os.UserManager)
            .isQuietModeEnabled(profile)
    } catch (e: Exception) {
        CrashReporter.reportNonFatal("AppDrawerFragment", e)
        true
    }

    /**
     * Returns true if the app has a limit or pause and the launch gate (mindful pause /
     * limit warning) is handling it, false if we should proceed with normal launch.
     */
    private fun checkBadHabitAndLaunch(appModel: AppModel, sourceBounds: android.graphics.Rect?): Boolean {
        val ctx = context ?: return false
        if (!AppLimitManager.isGated(ctx, appModel.appPackage)) return false
        BadHabitDialogs.gateLaunch(
            ctx, viewLifecycleOwner.lifecycleScope, { isAdded },
            appModel.appLabel, appModel.appPackage,
            open = {
                if (isAdded) {
                    recordLaunch(appModel)
                    viewModel.selectedApp(appModel, flag, sourceBounds = sourceBounds)
                    findNavController().popBackStack(R.id.mainFragment, false)
                }
            },
            // Backing out of the pause returns home rather than leaving the drawer open
            onCancel = { if (isAdded) findNavController().popBackStack(R.id.mainFragment, false) },
        )
        return true
    }

    override fun onStart() {
        super.onStart()
        cachedIsCjkKeyboard = null
        binding.search.showKeyboard(prefs.autoShowKeyboard)
        // Further typing must land in the field even with keyboard auto-show off
        if (typedQuery.isNotEmpty()) binding.search.requestFocus()
    }

    override fun onStop() {
        _binding?.search?.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        revealPrivateSpace = false
        scrollListener?.let { binding.recyclerView.removeOnScrollListener(it) }
        scrollListener = null
        pendingSubmit = null
        searchTextView = null
        super.onDestroyView()
        _binding = null
    }
}
