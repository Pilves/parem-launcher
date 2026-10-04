package com.parem.launcher.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentSettingsBinding
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.GestureLetterManager
import com.parem.launcher.helper.WeatherManager
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.skipAnimations
import com.parem.launcher.ui.settings.AppInfoSettingsCard
import com.parem.launcher.ui.settings.AppearanceSettingsCard
import com.parem.launcher.ui.settings.GesturesSettingsCard
import com.parem.launcher.ui.settings.HomeScreenSettingsCard
import com.parem.launcher.ui.settings.WellbeingSettingsCard

/**
 * Settings screen. Split into per-card helper classes under ui/settings/
 * (see ARCHITECTURE.md), each mirroring HomeWidgetController's shape: a class
 * taking (fragment, binding, prefs [, viewModel]) that owns one visual card's
 * click listeners, populate functions, and observers.
 *
 * This fragment keeps only: binding lifecycle, constructing/wiring the cards,
 * and logic that's genuinely cross-section:
 *  - [resetOpenPickers], called by every card's onClick, since any row's click
 *    should collapse every other card's open inline picker (original behavior).
 *  - [populateWellbeingSection], since it sets three different cards' toggle
 *    texts (weather, gesture letters, focus mode) from one place.
 *  - [importSettingsLauncher], since ActivityResultLauncher must be registered
 *    in onCreate, before the cards (which own the export/import logic) exist.
 */
class SettingsFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    /** Exposes binding liveness to card classes for async-callback guards. */
    internal fun isBindingAlive() = _binding != null

    private lateinit var importSettingsLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestContactsPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var appInfoCard: AppInfoSettingsCard
    private lateinit var homeScreenCard: HomeScreenSettingsCard
    private lateinit var wellbeingCard: WellbeingSettingsCard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.data?.let { uri -> appInfoCard.importSettings(uri) }
            }
        }
        // Registered here (ActivityResult contracts must register before RESUMED);
        // only ever launched by the contact-search toggle in HomeScreenSettingsCard.
        requestContactsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (_binding != null) homeScreenCard.onContactsPermissionResult(granted)
        }
        // Only launched by the grayscale pairing flow (the code is typed into a notification)
        requestNotificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (_binding != null) wellbeingCard.onNotificationPermissionResult(granted)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        viewModel.isParemDefault()

        // Closes any open inline picker when the background is tapped
        binding.scrollLayout.setOnClickListener { resetOpenPickers(it.id) }

        appInfoCard = AppInfoSettingsCard(this, binding, prefs, viewModel, importSettingsLauncher)
        appInfoCard.bind()
        homeScreenCard = HomeScreenSettingsCard(this, binding, prefs, viewModel, requestContactsPermissionLauncher)
        homeScreenCard.bind()
        AppearanceSettingsCard(this, binding, prefs, viewModel, onWellbeingChanged = ::populateWellbeingSection).bind()
        GesturesSettingsCard(this, binding, prefs, viewModel, onWellbeingChanged = ::populateWellbeingSection).bind()
        wellbeingCard = WellbeingSettingsCard(
            this, binding, prefs, viewModel, onWellbeingChanged = ::populateWellbeingSection,
            requestNotificationPermission = requestNotificationPermissionLauncher,
        )
        wellbeingCard.bind()

        populateWellbeingSection()

        // Consumed once: returning from a picker this screen opened rebuilds
        // the view, and that must not jump back to the searched row.
        arguments?.getInt(Constants.Key.SETTING)?.takeIf { it != 0 }?.let { anchor ->
            arguments?.remove(Constants.Key.SETTING)
            binding.scrollView.post { scrollToSetting(anchor) }
        }

        // Focus mode / screen-time dialogs rank apps by today's usage; without this
        // the list is empty unless the app drawer happened to load first
        if (requireContext().appUsagePermissionGranted())
            viewModel.getPerAppScreenTime()
    }

    /**
     * Collapses every card's inline picker layout except the one just opened
     * (identified by [clickedId]). Called first by every card's onClick, since
     * opening one picker should close whichever other one was left open.
     */
    internal fun resetOpenPickers(clickedId: Int) {
        if (_binding == null) return
        binding.appsNumSelectLayout.visibility = View.GONE
        binding.dateTimeSelectLayout.visibility = View.GONE
        binding.swipeDownSelectLayout.visibility = View.GONE
        binding.flSwipeDown.visibility = View.GONE
        binding.textSizesLayout.visibility = View.GONE
        if (clickedId != R.id.alignmentBottom)
            binding.alignmentSelectLayout.visibility = View.GONE
    }

    /**
     * Navigates only while Settings is still the current destination: a fast
     * double-tap on a row would otherwise fire the settings action a second
     * time from the app list, which has no such action, and crash.
     */
    internal fun navigateFromSettings(actionId: Int, args: Bundle? = null) {
        if (!isAdded) return
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.settingsFragment) return
        nav.navigate(actionId, args)
    }

    /**
     * Puts the row an omnibox search named a third of the way down and fades
     * it in once so the eye lands on it. The row is the anchor's ancestor
     * directly inside its card, so a quiet sub-row hidden while the feature is
     * off lands on the quiet group. A row this layout lacks (layout-land has
     * fewer rows) leaves the screen at the top.
     */
    private fun scrollToSetting(anchorId: Int) {
        val b = _binding ?: return
        val anchor = b.root.findViewById<View>(anchorId) ?: return
        // anchor … row, card — everything below scrollLayout
        val chain = generateSequence(anchor) { it.parent as? View }.takeWhile { it !== b.scrollLayout }.toList()
        val row = chain.getOrNull(chain.size - 2) ?: anchor
        if (chain.dropWhile { it !== row }.any { it.visibility != View.VISIBLE }) return
        val rect = android.graphics.Rect()
        row.getDrawingRect(rect)
        b.scrollView.offsetDescendantRectToMyCoords(row, rect)
        b.scrollView.scrollTo(0, (rect.top - b.scrollView.height / 3).coerceAtLeast(0))
        if (!requireContext().skipAnimations()) {
            row.alpha = 0.2f
            row.animate().alpha(1f).setDuration(700).start()
        }
    }

    internal fun populateWellbeingSection() {
        // Also reached from sheet dismiss listeners, which can run after onDestroyView
        if (_binding == null) return
        binding.focusModeToggle?.text = if (FocusModeManager.isActive(requireContext())) getString(R.string.on) else getString(R.string.off)

        binding.gestureLettersToggle?.text = if (GestureLetterManager.isEnabled(requireContext())) getString(R.string.on) else getString(R.string.off)
        binding.weatherToggle?.text = if (WeatherManager.isEnabled(requireContext())) {
            val city = WeatherManager.getCityName(requireContext())
            city.ifEmpty { getString(R.string.on) }
        } else getString(R.string.off)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
