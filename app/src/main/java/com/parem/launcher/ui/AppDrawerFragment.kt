package com.parem.launcher.ui

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
import com.parem.launcher.helper.hideKeyboard
import com.parem.launcher.helper.isEinkDisplay
import com.parem.launcher.helper.skipAnimations
import com.parem.launcher.helper.isSystemApp
import com.parem.launcher.helper.openAppInfo
import com.parem.launcher.helper.openSearch
import com.parem.launcher.helper.openUrl
import com.parem.launcher.helper.privateProfile
import com.parem.launcher.helper.showKeyboard
import com.parem.launcher.helper.showToast
import com.parem.launcher.helper.uninstall
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
            if (savedInstanceState == null)
                typedQuery = it.getString(Constants.Key.QUERY).orEmpty()
        }
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
                    is OmniboxMode.Calc -> {
                        ctx.copyToClipboard(mode.result)
                        ctx.showToast(getString(R.string.copied))
                    }
                    is OmniboxMode.Conversion -> {
                        ctx.copyToClipboard(mode.result)
                        ctx.showToast(getString(R.string.copied))
                    }
                    is OmniboxMode.Currency -> {
                        ctx.copyToClipboard(mode.result)
                        ctx.showToast(getString(R.string.copied))
                    }
                    OmniboxMode.CurrencyNoRates -> {}
                    is OmniboxMode.Dial -> dial(ctx, mode.number)
                    is OmniboxMode.QuickAction -> runQuickAction(mode.action)
                    is OmniboxMode.Contact -> dial(ctx, mode.number)
                    OmniboxMode.WebSearch ->
                        ctx.openUrl(Constants.URL_GOOGLE_SEARCH + java.net.URLEncoder.encode(q.trim(), "UTF-8"))
                    is OmniboxMode.Setting ->
                        if (adapter.itemCount == 0) openSetting(mode.anchor) else adapter.launchFirstInList()
                    OmniboxMode.None -> when {
                        q.startsWith("!") ->
                            ctx.openUrl(Constants.URL_DUCK_SEARCH + java.net.URLEncoder.encode(q, "UTF-8"))
                        adapter.itemCount == 0 -> ctx.openSearch(q.trim())
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
                            if (adapter.itemCount == 0 && newText.isNotBlank()) {
                                b.appDrawerTip.text = getString(R.string.no_apps_found)
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
            viewModel.selectedApp(app, flag)
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
            appInfoListener = {
                if (!isAdded) return@AppDrawerAdapter
                val ctx = context ?: return@AppDrawerAdapter
                openAppInfo(
                    ctx,
                    it.user,
                    it.appPackage
                )
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            appDeleteListener = {
                val ctx = context ?: return@AppDrawerAdapter
                when {
                    // ACTION_DELETE can't target another profile; app info can
                    // uninstall from there (upstream fix #446)
                    it.user != android.os.Process.myUserHandle() ->
                        openAppInfo(ctx, it.user, it.appPackage)
                    ctx.isSystemApp(it.appPackage) ->
                        ctx.showToast(getString(R.string.system_app_cannot_delete))
                    else -> ctx.uninstall(it.appPackage)
                }
            },
            appHideListener = { appModel, position ->
                if (!isAdded) return@AppDrawerAdapter
                reapplyQueryOnReload = false
                adapter.removeApp(position)

                val newSet = mutableSetOf<String>()
                newSet.addAll(prefs.hiddenApps)
                if (flag == Constants.FLAG_HIDDEN_APPS) {
                    newSet.remove(appModel.appPackage) // for backward compatibility
                    newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
                } else
                    newSet.add(appModel.appPackage + "|" + appModel.user.toString())

                prefs.hiddenApps = newSet
                if (newSet.isEmpty())
                    findNavController().popBackStack()
                if (prefs.firstHide) {
                    _binding?.search?.hideKeyboard()
                    prefs.firstHide = false
                    viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
                }
                viewModel.getAppList()
                viewModel.getHiddenApps()
            },
            appRenameListener = { appModel, renameLabel ->
                reapplyQueryOnReload = false
                prefs.setAppRenameLabel(appModel.appPackage, renameLabel)
                viewModel.getAppList()
            },
            appGrayscaleListener = {
                if (isAdded) GrayscaleSheet.toggleApp(this, it.appPackage) { dialog -> viewModel.showDialog.postValue(dialog) }
            }
        )
        adapter.showIcons = prefs.showIcons
        adapter.iconPackPackage = prefs.iconPackPackage
        adapter.openCounts = AppOpenCounter.getCounts(requireContext())
        // Read when the filter publishes, so fast typing only makes it stricter
        adapter.autoLaunchGuard = {
            !isSearchComposing() && !QuickActionParser.isActionPrefix(searchTextView?.text?.toString().orEmpty())
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
                    checkMessageAndExit()
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
            viewModel.hiddenApps.observe(viewLifecycleOwner) {
                it?.let {
                    adapter.setAppList(it.toMutableList())
                }
            }
        } else {
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
                    requireContext().copyToClipboard(mode.result)
                    requireContext().showToast(getString(R.string.copied))
                    return@setOnClickListener
                }
                is OmniboxMode.Conversion -> {
                    requireContext().copyToClipboard(mode.result)
                    requireContext().showToast(getString(R.string.copied))
                    return@setOnClickListener
                }
                is OmniboxMode.Currency -> {
                    requireContext().copyToClipboard(mode.result)
                    requireContext().showToast(getString(R.string.copied))
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

    private fun checkMessageAndExit() {
        if (!isAdded) return
        findNavController().popBackStack()
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
