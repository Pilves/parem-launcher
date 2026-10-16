package com.parem.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
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
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.copyToClipboard
import com.parem.launcher.helper.CurrencyRates
import com.parem.launcher.helper.OmniboxHistory
import com.parem.launcher.helper.OmniboxMode
import com.parem.launcher.helper.OmniboxResolver
import com.parem.launcher.helper.QuickAction
import com.parem.launcher.helper.QuickActionParser
import com.parem.launcher.helper.SettingsSearch
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.fireQuickAction
import com.parem.launcher.helper.quickActionPreview
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.getShortcutRaws
import com.parem.launcher.helper.HiddenAppHint
import com.parem.launcher.helper.hideKeyboard
import com.parem.launcher.helper.isEinkDisplay
import com.parem.launcher.helper.skipAnimations
import com.parem.launcher.helper.openSearch
import com.parem.launcher.helper.privateProfile
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
    // Opening the drawer also reloads the list, and setAppList shows it whole,
    // so a seeded query is re-applied when the fresh list lands. Cleared on
    // hide/rename: their reload could narrow to one match and auto-launch it.
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

    // Labels of installed hidden apps, for the empty-result hint only; never shown
    private var hiddenLabels: List<String> = emptyList()
    private var hiddenHintShown = false

    // At most one ECB rates fetch per drawer session, started by the first
    // currency query; the in-flight flag picks the CurrencyNoRates tip.
    private var currencyFetchStarted = false
    private var currencyFetchInFlight = false

    private val viewModel: MainViewModel by activityViewModels()
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
            // The omnibox's only in-app mention: a different example each open
            val hints = resources.getStringArray(R.array.omnibox_hints)
            binding.search.queryHint = hints[hintIndex++ % hints.size]
        }
        try {
            searchTextView = binding.search.findViewById(R.id.search_src_text)
            searchTextView?.gravity = prefs.appLabelAlignment
            // The rotating omnibox hint is an example, not a label; TalkBack needs the field's purpose
            searchTextView?.contentDescription = getString(R.string.search_apps)
        } catch (e: Exception) {
            Log.e("AppDrawerFragment", "Failed to set search text alignment", e)
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
                        q.startsWith("!") ->
                            launchUrl(ctx, Constants.URL_DUCK_SEARCH + java.net.URLEncoder.encode(q, "UTF-8"))
                        adapter.itemCount == 0 -> BadHabitDialogs.gateWebOpen(
                            ctx, viewLifecycleOwner.lifecycleScope, { isAdded },
                            Intent(Intent.ACTION_WEB_SEARCH), open = { browser -> ctx.openSearch(q.trim(), browser) },
                        )
                        else -> adapter.launchFirstInList()
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                _binding?.appRename?.visibility = if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                updateOmniboxState(newText)
                refreshRecent()
                // No debounce: matching against precomputed keys is sub-millisecond
                // and Filter supersedes stale requests itself — instant beats smooth
                try {
                    adapter.filter.filter(newText) {
                        _binding?.let { b ->
                            if (omniboxMode != OmniboxMode.None) return@let
                            hiddenHintShown = flag == Constants.FLAG_LAUNCH_APP &&
                                HiddenAppHint.show(newText, adapter.itemCount, isPrivateSpaceLocked(), hiddenLabels)
                            if (adapter.itemCount == 0 && newText.isNotBlank()) {
                                b.appDrawerTip.text = getString(if (hiddenHintShown) R.string.hidden_apps_hint else R.string.no_apps_found)
                                b.appDrawerTip.visibility = View.VISIBLE
                            } else {
                                b.appDrawerTip.visibility = View.GONE
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AppDrawerFragment", "Error filtering app list", e)
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

    private fun onAppClick(app: AppModel) {
        if (!isAdded) return
        if (app.isPrivateHeader()) {
            viewModel.setPrivateLocked(!isQuietMode(app.user))
            return
        }
        if (app.appPackage.isEmpty())
            return
        if ((flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
            && checkBadHabitAndLaunch(app)
        ) {
            // Bad habit check is handling launch asynchronously
        } else {
            recordLaunch(app)
            viewModel.selectedApp(app, flag, gestureLetter)
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

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            appClickListener = { onAppClick(it) },
            appLongPressListener = { app ->
                if (isAdded && _binding != null) {
                    _binding?.search?.hideKeyboard()
                    AppMenu.show(this, app, prefs, viewModel, hidden = flag == Constants.FLAG_HIDDEN_APPS) {
                        if (isAdded && _binding != null) {
                            reapplyQueryOnReload = false
                            viewModel.getAppList()
                            viewModel.getHiddenApps()
                        }
                    }
                }
            }
        )
        adapter.showIcons = prefs.showIcons
        adapter.iconPackPackage = prefs.iconPackPackage
        adapter.openCounts = AppOpenCounter.getCounts(requireContext())
        // Read when the filter publishes, so fast typing only makes it stricter
        adapter.autoLaunchGuard = {
            val text = searchTextView?.text?.toString().orEmpty()
            !isSearchComposing() && !QuickActionParser.isActionPrefix(text) &&
                OmniboxResolver.autoLaunchAllowed(text, prefs.autoLaunchSingleMatch, isScreenReaderOn())
        }
        if (flag == Constants.FLAG_LAUNCH_APP) {
            // Looked up once per drawer; the lock state is re-read on every rebuild
            val profile = privateProfile(requireContext())
            if (profile != null) adapter.privateSpaceHeader = header@{
                if (!isAdded) return@header null
                val locked = isQuietMode(profile)
                AppModel(
                    getString(if (locked) R.string.private_space_unlock else R.string.private_space_lock),
                    null, "", null, false, profile, isPrivate = true
                )
            }
        }

        linearLayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun scrollVerticallyBy(
                dx: Int,
                recycler: Recycler,
                state: RecyclerView.State,
            ): Int {
                val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
                val overScroll = dx - scrollRange
                val rv = _binding?.recyclerView ?: return scrollRange
                if (overScroll < -10 && rv.scrollState == RecyclerView.SCROLL_STATE_DRAGGING)
                    if (isAdded) findNavController().popBackStack()
                return scrollRange
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
        // The glow isn't an animator, so no system scale removes it; on e-ink it forces a partial refresh
        if (requireContext().isEinkDisplay())
            binding.recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        if (requireContext().skipAnimations().not())
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
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
                }
            }
        } else {
            if (flag == Constants.FLAG_LAUNCH_APP) {
                viewModel.hiddenApps.observe(viewLifecycleOwner) { apps ->
                    hiddenLabels = apps?.map { it.appLabel }.orEmpty()
                }
                if (prefs.hiddenApps.isNotEmpty()) viewModel.getHiddenApps()
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
                    } else if (reapplyQueryOnReload) {
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

    private fun initClickListeners() {
        binding.appDrawerTip.setOnClickListener {
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
            ctx.showToast(getString(R.string.unable_to_open_app))
        }
    }

    /**
     * Omnibox web opens take the website-slot route: gated on the handling app,
     * then MainViewModel.selectedApp, which also pins a whitelisted browser under focus.
     */
    private fun launchUrl(ctx: Context, url: String) {
        BadHabitDialogs.gateWebOpen(
            ctx, viewLifecycleOwner.lifecycleScope, { isAdded },
            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            open = { browser ->
                if (isAdded) viewModel.selectedApp(
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
        true
    }

    /**
     * Returns true if the app has a limit and the launch gate (mindful pause /
     * limit warning) is handling it, false if we should proceed with normal launch.
     */
    private fun checkBadHabitAndLaunch(appModel: AppModel): Boolean {
        val ctx = context ?: return false
        if (!AppLimitManager.hasLimit(ctx, appModel.appPackage)) return false
        BadHabitDialogs.gateLaunch(
            ctx, viewLifecycleOwner.lifecycleScope, { isAdded },
            appModel.appLabel, appModel.appPackage,
            open = {
                if (isAdded) {
                    recordLaunch(appModel)
                    viewModel.selectedApp(appModel, flag)
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
        scrollListener?.let { binding.recyclerView.removeOnScrollListener(it) }
        scrollListener = null
        searchTextView = null
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        // Process lifetime is enough to rotate; not worth a pref
        var hintIndex = 0
    }
}
