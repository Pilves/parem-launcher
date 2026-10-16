package com.parem.launcher.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentOnboardingBinding
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.helper.DoubleTapActionManager
import com.parem.launcher.helper.GrayscaleController
import com.parem.launcher.helper.GrayscalePairing
import com.parem.launcher.helper.WellbeingRowState
import com.parem.launcher.helper.showToast
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.isAccessServiceEnabled
import com.parem.launcher.helper.isParemDefault
import com.parem.launcher.helper.padForSystemBars
import com.parem.launcher.helper.notifications.QuietNotificationsManager
import com.parem.launcher.helper.notifications.QuietNotificationsManager.State
import com.parem.launcher.listener.DeviceAdmin

/**
 * Two screens: "make Parem your home screen", then optional Calm-phone setup.
 * Each Calm row runs the same flow as its settings row; not tapping a row is
 * the skip. State is re-derived in onResume because every grant lands in
 * system settings or a system dialog, and becoming home may recreate the activity.
 */
class OnboardingFragment : BaseFragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val ctx = context
        if (isAdded && _binding != null && ctx != null) {
            if (granted && GrayscalePairing.isSupported()) GrayscalePairing.start(ctx)
            else ctx.showToast(R.string.grayscale_pair_needs_notif)
        }
    }

    private var showingCalm = false
    // The role dialog may stay suppressed after one denial, so the second tap goes to settings
    private var askedForHome = false
    private val rows = mutableListOf<Row>()

    private val backPress = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = if (showingCalm) finish() else showCalm()
    }

    private class Row(val view: View, val value: TextView, val state: (Context) -> String)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        askedForHome = savedInstanceState?.getBoolean(KEY_ASKED) ?: false
        binding.root.padForSystemBars()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPress)
        binding.btnSetHome.setOnClickListener { askForHome() }
        binding.btnNotNow.setOnClickListener { showCalm() }
        binding.btnDone.setOnClickListener { finish() }
        addCalmRows()
        addFeatureGuide()
        setGuideExpanded(savedInstanceState?.getBoolean(KEY_GUIDE) == true)
        binding.featureGuideToggle.setOnClickListener {
            setGuideExpanded(binding.featureGuideRows.visibility != View.VISIBLE)
        }

        if (savedInstanceState?.getBoolean(KEY_CALM) == true) showCalm() else showHome()
    }

    override fun onResume() {
        super.onResume()
        if (!showingCalm && isParemDefault(requireContext())) showCalm()
        else if (showingCalm) refreshRows()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_CALM, showingCalm)
        outState.putBoolean(KEY_ASKED, askedForHome)
        outState.putBoolean(KEY_GUIDE, binding.featureGuideRows.visibility == View.VISIBLE)
    }

    override fun onDestroyView() {
        rows.clear()
        _binding = null
        super.onDestroyView()
    }

    private fun showHome() {
        showingCalm = false
        binding.homeScreen.visibility = View.VISIBLE
        binding.calmScreen.visibility = View.GONE
    }

    private fun showCalm() {
        if (_binding == null) return
        showingCalm = true
        binding.homeScreen.visibility = View.GONE
        binding.calmScreen.visibility = View.VISIBLE
        refreshRows()
    }

    // Marked seen only on the way out: a kill or recreate mid-onboarding must bring it back
    private fun finish() {
        prefs.onboardingVersionSeen = Constants.ONBOARDING_VERSION
        // A double tap on Done would otherwise pop home too
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.onboardingFragment) nav.popBackStack()
    }

    private fun askForHome() {
        if (!askedForHome) {
            askedForHome = true
            viewModel.resetLauncherLiveData.call()
            return
        }
        // "A matching Activity may not exist" for either action
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                viewModel.resetLauncherLiveData.call()
            }
        }
    }

    // Order per DESIGN.md §14: each row's prerequisite comes before it
    private fun addCalmRows() {
        val on = getString(R.string.on)
        val setUp = getString(R.string.onboarding_set_up)
        addRow(R.string.screen_time, R.string.onboarding_screen_time_caption, { if (it.appUsagePermissionGranted()) on else setUp }) {
            val ctx = context ?: return@addRow
            if (!ctx.appUsagePermissionGranted()) BadHabitDialogs.showUsageAccessSheet(ctx).dismissOnDestroy(viewLifecycleOwner)
            else showExplanation(R.string.screen_time, R.string.onboarding_guide_calm_body)
        }
        addRow(R.string.onboarding_row_slow_down, R.string.onboarding_row_slow_down_caption,
            { ctx -> if (AppLimitManager.getAllLimits(ctx).keys.any { AppLimitManager.hasPause(ctx, it) }) on else setUp }
        ) { openAppLimits() }
        addRow(R.string.onboarding_row_quiet, R.string.onboarding_row_quiet_caption, { ctx ->
            when (QuietNotificationsManager.state(ctx)) {
                State.OFF -> setUp
                State.NEEDS_ACCESS -> getString(R.string.quiet_needs_access)
                State.ON -> on
            }
        }) { openQuietNotifications() }
        addRow(R.string.double_tap_lock, R.string.onboarding_lock_caption, { if (lockWorks(it)) on else setUp }) { openLock() }
        addRow(R.string.grayscale, R.string.onboarding_row_grayscale_caption, { ctx ->
            when (WellbeingRowState.grayscale(
                GrayscaleController.isGranted(ctx), GrayscaleController.isManual(ctx),
                GrayscaleController.isOnFocus(ctx), GrayscaleController.isOnLimit(ctx),
            )) {
                WellbeingRowState.Row.SET_UP -> setUp
                WellbeingRowState.Row.ON -> on
                WellbeingRowState.Row.AUTOMATIC -> getString(R.string.grayscale_automatic)
                else -> getString(R.string.off)
            }
        }) { GrayscaleSheet.open(this, onPair = ::startGrayscalePairing, onChanged = ::refreshRows) }
    }

    private fun startGrayscalePairing() {
        val ctx = context ?: return
        if (!GrayscalePairing.isSupported()) return
        if (GrayscalePairing.canNotify(ctx)) GrayscalePairing.start(ctx)
        else notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun setGuideExpanded(expanded: Boolean) {
        binding.featureGuideRows.visibility = if (expanded) View.VISIBLE else View.GONE
        ViewCompat.setStateDescription(binding.featureGuideToggle, getString(
            if (expanded) R.string.onboarding_guide_expanded else R.string.onboarding_guide_collapsed
        ))
    }

    private fun addFeatureGuide() {
        val topics = listOf(
            R.string.onboarding_guide_home to R.string.onboarding_guide_home_body,
            R.string.onboarding_guide_search to R.string.onboarding_guide_search_body,
            R.string.onboarding_guide_tools to R.string.onboarding_guide_tools_body,
            R.string.onboarding_guide_gestures to R.string.onboarding_guide_gestures_body,
            R.string.onboarding_guide_calm to R.string.onboarding_guide_calm_body,
            R.string.onboarding_guide_quiet to R.string.onboarding_guide_quiet_body,
            R.string.onboarding_guide_appearance to R.string.onboarding_guide_appearance_body,
            R.string.onboarding_guide_data to R.string.onboarding_guide_data_body,
        )
        for ((title, body) in topics) {
            val row = layoutInflater.inflate(R.layout.item_onboarding_row, binding.featureGuideRows, false)
            row.findViewById<TextView>(R.id.rowLabel).setText(title)
            row.findViewById<TextView>(R.id.rowValue).setText(R.string.onboarding_guide_read)
            row.contentDescription = getString(title)
            row.setOnClickListener { showExplanation(title, body) }
            binding.featureGuideRows.addView(row)
        }
    }

    private fun showExplanation(title: Int, body: Int) {
        val ctx = context ?: return
        BottomSheetMenu(ctx).title(getString(title)).message(getString(body))
            .option(getString(R.string.close)) {}
            .show().dismissOnDestroy(viewLifecycleOwner)
    }

    private fun addRow(label: Int, caption: Int?, state: (Context) -> String, onClick: () -> Unit) {
        val view = layoutInflater.inflate(R.layout.item_onboarding_row, binding.calmRows, false)
        view.findViewById<TextView>(R.id.rowLabel).setText(label)
        caption?.let {
            view.findViewById<TextView>(R.id.rowCaption).apply {
                setText(it)
                visibility = View.VISIBLE
            }
        }
        view.contentDescription = listOfNotNull(getString(label), caption?.let { getString(it) }).joinToString(". ")
        view.setOnClickListener { onClick() }
        binding.calmRows.addView(view, binding.calmRows.indexOfChild(binding.calmFooter))
        rows += Row(view, view.findViewById(R.id.rowValue), state)
    }

    private fun refreshRows() {
        if (!isAdded || _binding == null) return
        val ctx = requireContext()
        for (row in rows) {
            val value = row.state(ctx)
            row.value.text = value
            ViewCompat.setStateDescription(row.view, value)
        }
    }

    private fun openAppLimits() {
        val ctx = context ?: return
        if (!ctx.appUsagePermissionGranted()) {
            BadHabitDialogs.showUsageAccessSheet(ctx).dismissOnDestroy(viewLifecycleOwner)
            return
        }
        val usageMap = viewModel.perAppScreenTime.value ?: prefs.getCachedUsageStats()
        val menu = BottomSheetMenu(ctx)
            .title(getString(R.string.onboarding_row_slow_down))
            .message(getString(R.string.onboarding_pause_steps))
        if (usageMap.isNotEmpty()) {
            menu.option(getString(R.string.onboarding_open_limits)) {
                if (isAdded && _binding != null) {
                    ScreenTimeLimitDialog(ctx, usageMap).show().apply {
                        setOnDismissListener { refreshRows() }
                    }.dismissOnDestroy(viewLifecycleOwner)
                }
            }
        } else menu.message(getString(R.string.onboarding_pause_no_usage))
        menu.option(getString(R.string.not_now), dimmed = true) {}
            .show().dismissOnDestroy(viewLifecycleOwner)
    }

    private fun openQuietNotifications() {
        val ctx = context ?: return
        val isAlive = { _binding != null }
        when (QuietNotificationsManager.state(ctx)) {
            State.OFF -> QuietListSheet.startTurnOn(this, prefs, isAlive, onChanged = ::refreshRows)
            State.NEEDS_ACCESS -> QuietListSheet.showNeedsAccess(ctx, starting = false, onChanged = ::refreshRows)
            State.ON -> QuietListSheet.editAllowed(this, prefs, isAlive)
        }
    }

    /** Declining changes nothing; the double-tap keeps its own first-use consent. */
    private fun openLock() {
        val ctx = context ?: return
        if (lockWorks(ctx)) {
            showExplanation(R.string.double_tap_lock, R.string.onboarding_lock_enabled_body)
            return
        }
        showLockConsent(
            ctx,
            onAccept = {
                if (isAdded) {
                    DoubleTapActionManager.setAction(ctx, Constants.GestureAction.LOCK_SCREEN)
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            },
            onDecline = {},
        )
    }

    private fun lockWorks(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val canLock = isAccessServiceEnabled(ctx) || dpm.isAdminActive(ComponentName(ctx, DeviceAdmin::class.java))
        return canLock && DoubleTapActionManager.getAction(ctx) == Constants.GestureAction.LOCK_SCREEN
    }

    private companion object {
        const val KEY_GUIDE = "onboarding_guide"
        const val KEY_CALM = "onboarding_calm"
        const val KEY_ASKED = "onboarding_asked_home"
    }
}
