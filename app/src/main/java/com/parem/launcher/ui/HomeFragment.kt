package com.parem.launcher.ui

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentHomeBinding
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.showToast
import com.parem.launcher.ui.home.HomeClockController
import com.parem.launcher.ui.home.HomeGesturesController
import com.parem.launcher.ui.home.HomeSlotsController
import kotlinx.coroutines.launch

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    internal var widgetController: HomeWidgetController? = null
        private set
    internal var slotsController: HomeSlotsController? = null
        private set
    internal var gesturesController: HomeGesturesController? = null
        private set
    internal var clockController: HomeClockController? = null
        private set

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        slotsController = HomeSlotsController(this, binding, prefs, viewModel)
        gesturesController = HomeGesturesController(this, binding, prefs, viewModel)
        clockController = HomeClockController(this, binding, prefs, viewModel)

        widgetController = HomeWidgetController(this, binding, prefs)
        // Widgets are restored asynchronously; re-fit the app rows once their
        // heights are real, otherwise apps overflow behind the widgets
        widgetController?.onWidgetsChanged = {
            if (isAdded && _binding != null) slotsController?.populateHomeScreen(false)
        }

        gesturesController?.registerTorchCallback()

        initObservers()
        slotsController?.setHomeAlignment(prefs.homeAlignment)
        gesturesController?.initSwipeTouchListener()
        initClickListeners()
        gesturesController?.initGestureLetterOverlay()
        viewLifecycleOwner.lifecycleScope.launch {
            widgetController?.restoreWidgets()
        }

        if (prefs.onboardingVersionSeen < Constants.ONBOARDING_VERSION) {
            prefs.onboardingVersionSeen = Constants.ONBOARDING_VERSION
            findNavController().navigate(R.id.action_mainFragment_to_onboardingFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        // Cancel any pending long-press timers from before the app was backgrounded
        widgetController?.cancelPendingLongPresses()
        slotsController?.populateHomeScreen(false)
        viewModel.isParemDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
        FocusModeManager.checkAndExpire(requireContext())
        viewModel.getWeather()
        gesturesController?.updateGestureLetterOverlay()
    }

    override fun onClick(view: View) {
        when (view.id) {
            // The lock view must stay clickable but do nothing here: clicking it emits the
            // accessibility event MyAccessibilityService matches (by contentDescription) to
            // perform GLOBAL_ACTION_LOCK_SCREEN. See HomeGesturesController.lockPhone().
            R.id.lock -> {}
            R.id.clock -> clockController?.openClockApp()
            R.id.date -> clockController?.openCalendarApp()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> clockController?.openScreenTimeDigitalWellbeing()

            else -> {
                try { // Launch app
                    val appLocation = view.tag.toString().toInt()
                    slotsController?.homeAppClicked(appLocation)
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Failed to launch app", e)
                }
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        val homeAppViews = slotsController?.homeAppViews.orEmpty()
        val slot = homeAppViews.indexOf(view) + 1
        if (slot in 1..homeAppViews.size) {
            slotsController?.showHomeSlotMenu(slot)
            return true
        }
        when (view.id) {
            // Don't clear the current clock/calendar prefs here: if the user backs out
            // of the app list without choosing, their existing choice must survive.
            R.id.clock -> showAppList(Constants.FLAG_SET_CLOCK_APP)

            R.id.date -> showAppList(Constants.FLAG_SET_CALENDAR_APP)

            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isParemDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
        }
        return true
    }

    private fun initObservers() {
        viewModel.refreshHome.observe(viewLifecycleOwner) {
            slotsController?.populateHomeScreen(it)
            if (widgetController?.needsRestore() == true) {
                viewLifecycleOwner.lifecycleScope.launch { widgetController?.restoreWidgets() }
            }
        }
        viewModel.isParemDefault.observe(viewLifecycleOwner) {
            if (it != true) {
                if (prefs.dailyWallpaper) {
                    prefs.dailyWallpaper = false
                    viewModel.cancelWallpaperWorker()
                }
                prefs.homeBottomAlignment = false
                slotsController?.setHomeAlignment()
            }
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            slotsController?.setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            clockController?.populateDateTime()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let { binding.tvScreenTime.text = it }
        }
        viewModel.weatherValue.observe(viewLifecycleOwner) {
            clockController?.populateDateTime()
        }
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        binding.tvScreenTime.setOnClickListener(this)
    }

    internal fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
            Log.e("HomeFragment", "Navigation to app list failed, using fallback", e)
        }
    }

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION", "InlinedApi")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
        else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    override fun onDestroyView() {
        gesturesController?.unregisterTorchCallback()
        widgetController?.cleanup()
        widgetController = null
        gesturesController?.cleanupListeners()
        gesturesController = null
        slotsController?.cleanup()
        slotsController = null
        clockController = null
        super.onDestroyView()
        _binding = null
    }
}
