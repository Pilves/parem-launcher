package com.parem.launcher.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
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
import com.parem.launcher.helper.UsageStatsHelper
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.copyToClipboard
import com.parem.launcher.helper.ExpressionEvaluator
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.hideKeyboard
import com.parem.launcher.helper.isEinkDisplay
import com.parem.launcher.helper.isSystemApp
import com.parem.launcher.helper.openAppInfo
import com.parem.launcher.helper.openSearch
import com.parem.launcher.helper.openUrl
import com.parem.launcher.helper.showKeyboard
import com.parem.launcher.helper.showToast
import com.parem.launcher.helper.uninstall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope


class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false
    private var scrollListener: RecyclerView.OnScrollListener? = null
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var searchTextView: TextView? = null
    private var cachedIsCjkKeyboard: Boolean? = null

    // Omnibox state: non-null when the query is an arithmetic expression or a
    // dialable number, true when a leading space marks the query as a web search
    private var calcResult: String? = null
    private var dialNumber: String? = null
    private var webSearchMode = false

    companion object {
        // Digits with optional leading + and spaces, at least 4 digits total.
        // Hyphenated numbers lose to the calculator (they parse as subtraction).
        private val DIAL_REGEX = Regex("^\\+?[0-9][0-9 ]{2,}[0-9]$")
    }

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
        }
        initViews()
        initSearch()
        initAdapter()
        initObservers()
        initClickListeners()
    }

    private fun initViews() {
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP)
            binding.search.queryHint = "Please select an app"
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
                when {
                    calcResult != null -> {
                        ctx.copyToClipboard(calcResult!!)
                        ctx.showToast(getString(R.string.copied))
                    }
                    dialNumber != null -> {
                        try {
                            startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_DIAL,
                                    android.net.Uri.parse("tel:" + dialNumber!!.replace(" ", ""))
                                )
                            )
                        } catch (e: Exception) {
                            ctx.showToast(getString(R.string.unable_to_open_app))
                        }
                    }
                    webSearchMode ->
                        ctx.openUrl(Constants.URL_GOOGLE_SEARCH + java.net.URLEncoder.encode(q.trim(), "UTF-8"))
                    q.startsWith("!") ->
                        ctx.openUrl(Constants.URL_DUCK_SEARCH + java.net.URLEncoder.encode(q, "UTF-8"))
                    adapter.itemCount == 0 -> ctx.openSearch(q.trim())
                    else -> adapter.launchFirstInList()
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                _binding?.appRename?.visibility = if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                updateOmniboxState(newText)
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    try {
                        adapter.filter.filter(newText) {
                            _binding?.let { b ->
                                if (calcResult != null || dialNumber != null || webSearchMode) return@let
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
                }
                searchHandler.postDelayed(searchRunnable!!, 150)
                return true
            }
        })
    }

    /**
     * The search field doubles as an omnibox: arithmetic shows a live result
     * (tap or submit to copy), and a leading space turns the query into a
     * Google search on submit.
     */
    private fun updateOmniboxState(newText: String) {
        val b = _binding ?: return
        calcResult = null
        dialNumber = null
        webSearchMode = false
        val trimmed = newText.trim()

        if (ExpressionEvaluator.looksLikeExpression(trimmed)) {
            ExpressionEvaluator.evaluate(trimmed)?.let { value ->
                calcResult = ExpressionEvaluator.format(value)
                b.appDrawerTip.text = "= $calcResult"
                b.appDrawerTip.visibility = View.VISIBLE
                return
            }
        }
        if (DIAL_REGEX.matches(trimmed) && trimmed.count { it.isDigit() } >= 4) {
            dialNumber = trimmed
            b.appDrawerTip.text = getString(R.string.call_hint, trimmed)
            b.appDrawerTip.visibility = View.VISIBLE
            return
        }
        if (newText.startsWith(" ") && trimmed.isNotEmpty()) {
            webSearchMode = true
            b.appDrawerTip.text = getString(R.string.google_search_hint, trimmed)
            b.appDrawerTip.visibility = View.VISIBLE
            return
        }
        if (flag == Constants.FLAG_LAUNCH_APP) b.appDrawerTip.visibility = View.GONE
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            appClickListener = {
                if (!isAdded || it.appPackage.isEmpty())
                    return@AppDrawerAdapter
                if (flag == Constants.FLAG_LAUNCH_APP && checkBadHabitAndLaunch(it)) {
                    // Bad habit check is handling launch asynchronously
                } else {
                    viewModel.selectedApp(it, flag)
                    if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                        findNavController().popBackStack(R.id.mainFragment, false)
                    else
                        findNavController().popBackStack()
                }
            },
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
                prefs.setAppRenameLabel(appModel.appPackage, renameLabel)
                viewModel.getAppList()
            }
        )
        adapter.showIcons = prefs.showIcons
        adapter.iconPackPackage = prefs.iconPackPackage
        adapter.openCounts = AppOpenCounter.getCounts(requireContext())
        adapter.autoLaunchGuard = { !isSearchComposing() }

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

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
        scrollListener = getRecyclerViewOnScrollListener()
        binding.recyclerView.addOnScrollListener(scrollListener!!)
        binding.recyclerView.itemAnimator = null
        if (requireContext().isEinkDisplay().not())
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
        val wantSortByUsage = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
                && requireContext().appUsagePermissionGranted()
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
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
            && requireContext().appUsagePermissionGranted()
        ) {
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
            calcResult?.let { result ->
                requireContext().copyToClipboard(result)
                requireContext().showToast(getString(R.string.copied))
                return@setOnClickListener
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
                            if (!onTop && isRemoving.not())
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

    /**
     * Returns true if the app is a bad habit and we're handling it (async check),
     * false if we should proceed with normal launch.
     */
    private fun checkBadHabitAndLaunch(appModel: AppModel): Boolean {
        val ctx = context ?: return false
        if (!AppLimitManager.hasLimit(ctx, appModel.appPackage)) return false
        val limitMinutes = AppLimitManager.getLimit(ctx, appModel.appPackage) ?: return false

        viewLifecycleOwner.lifecycleScope.launch {
            val usageMs = withContext(Dispatchers.IO) {
                UsageStatsHelper.getUsageForApp(ctx, appModel.appPackage)
            }
            if (!isAdded) return@launch
            val usageMinutes = usageMs / 60_000
            if (usageMinutes >= limitMinutes) {
                showBadHabitWarningDialog(appModel, usageMinutes, limitMinutes)
            } else {
                viewModel.selectedApp(appModel, flag)
                findNavController().popBackStack(R.id.mainFragment, false)
            }
        }
        return true
    }

    private fun showBadHabitWarningDialog(appModel: AppModel, usageMinutes: Long, limitMinutes: Int) {
        val ctx = context ?: return
        val appName = appModel.appLabel.ifEmpty { appModel.appPackage }
        BadHabitDialogs.showLimitWarning(ctx, appName, usageMinutes, limitMinutes) {
            if (!isAdded) return@showLimitWarning
            viewModel.selectedApp(appModel, flag)
            findNavController().popBackStack(R.id.mainFragment, false)
        }
    }

    override fun onStart() {
        super.onStart()
        cachedIsCjkKeyboard = null
        binding.search.showKeyboard(prefs.autoShowKeyboard)
    }

    override fun onStop() {
        _binding?.search?.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        scrollListener?.let { binding.recyclerView.removeOnScrollListener(it) }
        scrollListener = null
        searchTextView = null
        super.onDestroyView()
        _binding = null
    }
}