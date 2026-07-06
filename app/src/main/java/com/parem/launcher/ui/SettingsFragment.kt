package com.parem.launcher.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentSettingsBinding
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.GestureLetterManager
import com.parem.launcher.helper.WeatherManager
import com.parem.launcher.helper.appUsagePermissionGranted
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
class SettingsFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    /** Exposes binding liveness to card classes for async-callback guards. */
    internal fun isBindingAlive() = _binding != null

    private lateinit var importSettingsLauncher: ActivityResultLauncher<Intent>
    private lateinit var appInfoCard: AppInfoSettingsCard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.data?.let { uri -> appInfoCard.importSettings(uri) }
            }
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
        HomeScreenSettingsCard(this, binding, prefs, viewModel).bind()
        AppearanceSettingsCard(this, binding, prefs, viewModel, onWellbeingChanged = ::populateWellbeingSection).bind()
        GesturesSettingsCard(this, binding, prefs, viewModel, onWellbeingChanged = ::populateWellbeingSection).bind()
        WellbeingSettingsCard(this, binding, prefs, viewModel, onWellbeingChanged = ::populateWellbeingSection).bind()

        populateWellbeingSection()

        // Focus mode / screen-time dialogs rank apps by today's usage; without this
        // the list is empty unless the app drawer happened to load first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requireContext().appUsagePermissionGranted())
            viewModel.getPerAppScreenTime()
    }

    /**
     * Collapses every card's inline picker layout except the one just opened
     * (identified by [clickedId]). Called first by every card's onClick, since
     * opening one picker should close whichever other one was left open.
     */
    internal fun resetOpenPickers(clickedId: Int) {
        binding.appsNumSelectLayout.visibility = View.GONE
        binding.dateTimeSelectLayout.visibility = View.GONE
        binding.swipeDownSelectLayout.visibility = View.GONE
        binding.flSwipeDown.visibility = View.GONE
        binding.textSizesLayout.visibility = View.GONE
        if (clickedId != R.id.alignmentBottom)
            binding.alignmentSelectLayout.visibility = View.GONE
    }

    internal fun populateWellbeingSection() {
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
