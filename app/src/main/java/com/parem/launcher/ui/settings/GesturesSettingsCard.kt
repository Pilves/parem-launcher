package com.parem.launcher.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.parem.launcher.MainActivity
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentSettingsBinding
import com.parem.launcher.helper.DoubleTapActionManager
import com.parem.launcher.helper.GestureLetterManager
import com.parem.launcher.helper.animateAlpha
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.isAccessServiceEnabled
import com.parem.launcher.helper.openUrl
import com.parem.launcher.helper.showToast
import com.parem.launcher.listener.DeviceAdmin
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.SettingsFragment

/**
 * Card 4 ("Gestures"): swipe left/right/down actions, double-tap action
 * (including the accessibility/device-admin lock permission flow), auto-show
 * keyboard, and gesture letters.
 *
 * Extracted from SettingsFragment; mirrors HomeWidgetController's shape.
 */
class GesturesSettingsCard(
    private val fragment: SettingsFragment,
    private val binding: FragmentSettingsBinding,
    private val prefs: Prefs,
    private val viewModel: MainViewModel,
    private val onWellbeingChanged: () -> Unit,
) : View.OnClickListener, View.OnLongClickListener {

    private val context get() = binding.root.context

    private val deviceManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val componentName = ComponentName(context, DeviceAdmin::class.java)

    fun bind() {
        populateKeyboardText()
        populateSwipeApps()
        populateSwipeDownAction()
        populateDoubleTapAction()

        initClickListeners()
        initObservers()
    }

    private fun initClickListeners() {
        binding.autoShowKeyboard.setOnClickListener(this)
        binding.doubleTapAction?.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.swipeDownAction.setOnClickListener(this)
        binding.search.setOnClickListener(this)
        binding.notifications.setOnClickListener(this)
        binding.actionAccessibility.setOnClickListener(this)
        binding.closeAccessibility.setOnClickListener(this)
        binding.notWorking.setOnClickListener(this)
        binding.gestureLettersToggle?.setOnClickListener(this)
        binding.tvGestures?.setOnClickListener(this)

        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
    }

    private fun initObservers() {
        viewModel.updateSwipeApps.observe(fragment.viewLifecycleOwner) {
            populateSwipeApps()
        }
    }

    override fun onClick(view: View) {
        fragment.resetOpenPickers(view.id)
        when (view.id) {
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.actionAccessibility -> openAccessibilityService()
            R.id.closeAccessibility -> toggleAccessibilityVisibility(false)
            R.id.notWorking -> if (Constants.URL_DOUBLE_TAP.isNotEmpty()) context.openUrl(Constants.URL_DOUBLE_TAP)

            R.id.tvGestures -> binding.flSwipeDown.visibility = View.VISIBLE

            R.id.swipeLeftApp -> showSwipeActionPicker(isLeft = true)
            R.id.swipeRightApp -> showSwipeActionPicker(isLeft = false)
            R.id.swipeDownAction -> binding.swipeDownSelectLayout.visibility = View.VISIBLE
            R.id.notifications -> updateSwipeDownAction(Constants.SwipeDownAction.NOTIFICATIONS)
            R.id.search -> updateSwipeDownAction(Constants.SwipeDownAction.SEARCH)

            R.id.doubleTapAction -> showDoubleTapActionPicker()
            R.id.gestureLettersToggle -> {
                if (GestureLetterManager.isEnabled(context)) {
                    GestureLetterConfigDialog(
                        context,
                        viewModel,
                        onPickAppForLetter = { showAppListForSwipe(Constants.FLAG_SET_GESTURE_LETTER_APP) },
                        onDisabled = onWellbeingChanged,
                    ).show()
                } else {
                    GestureLetterManager.setEnabled(context, true)
                    onWellbeingChanged()
                }
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp -> toggleSwipeRight()
        }
        return true
    }

    private fun toggleSwipeLeft() {
        prefs.swipeLeftEnabled = !prefs.swipeLeftEnabled
        if (prefs.swipeLeftEnabled) {
            binding.swipeLeftApp.setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            context.showToast(context.getString(R.string.swipe_left_app_enabled))
        } else {
            binding.swipeLeftApp.setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            context.showToast(context.getString(R.string.swipe_left_app_disabled))
        }
    }

    private fun toggleSwipeRight() {
        prefs.swipeRightEnabled = !prefs.swipeRightEnabled
        if (prefs.swipeRightEnabled) {
            binding.swipeRightApp.setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            context.showToast(context.getString(R.string.swipe_right_app_enabled))
        } else {
            binding.swipeRightApp.setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            context.showToast(context.getString(R.string.swipe_right_app_disabled))
        }
    }

    private fun toggleKeyboardText() {
        if (prefs.autoShowKeyboard && prefs.keyboardMessageShown.not()) {
            viewModel.showDialog.postValue(Constants.Dialog.KEYBOARD)
            prefs.keyboardMessageShown = true
        } else {
            prefs.autoShowKeyboard = !prefs.autoShowKeyboard
            populateKeyboardText()
        }
    }

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) binding.autoShowKeyboard.text = context.getString(R.string.on)
        else binding.autoShowKeyboard.text = context.getString(R.string.off)
    }

    private fun toggleAccessibilityVisibility(show: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            binding.notWorking.visibility = View.VISIBLE
        if (isAccessServiceEnabled(context))
            binding.actionAccessibility.text = context.getString(R.string.disable)
        binding.accessibilityLayout.isVisible = show
        binding.scrollView.animateAlpha(if (show) 0.5f else 1f)
    }

    private fun openAccessibilityService() {
        toggleAccessibilityVisibility(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            fragment.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun removeActiveAdmin(toastMessage: String? = null) {
        try {
            deviceManager.removeActiveAdmin(componentName) // for backward compatibility
            context.showToast(toastMessage)
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Failed to remove device admin", e)
        }
    }

    private fun populateSwipeDownAction() {
        binding.swipeDownAction.text = when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.NOTIFICATIONS -> context.getString(R.string.notifications)
            else -> context.getString(R.string.search)
        }
    }

    private fun updateSwipeDownAction(swipeDownFor: Int) {
        if (prefs.swipeDownAction == swipeDownFor) return
        prefs.swipeDownAction = swipeDownFor
        populateSwipeDownAction()
    }

    private fun populateSwipeApps() {
        binding.swipeLeftApp.text = gestureActionLabel(prefs.getEffectiveSwipeLeftAction(), prefs.appNameSwipeLeft)
        binding.swipeRightApp.text = gestureActionLabel(prefs.getEffectiveSwipeRightAction(), prefs.appNameSwipeRight)
        if (!prefs.swipeLeftEnabled)
            binding.swipeLeftApp.setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
        if (!prefs.swipeRightEnabled)
            binding.swipeRightApp.setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
    }

    private fun gestureActionLabel(action: Int, appName: String): String {
        return when (action) {
            Constants.GestureAction.OPEN_APP -> appName
            Constants.GestureAction.OPEN_NOTIFICATIONS -> context.getString(R.string.notifications)
            Constants.GestureAction.OPEN_SEARCH -> context.getString(R.string.search)
            Constants.GestureAction.LOCK_SCREEN -> context.getString(R.string.lock_screen)
            Constants.GestureAction.OPEN_CAMERA -> context.getString(R.string.camera)
            Constants.GestureAction.TOGGLE_FLASHLIGHT -> context.getString(R.string.flashlight)
            Constants.GestureAction.NONE -> context.getString(R.string.none)
            else -> appName
        }
    }

    private fun showSwipeActionPicker(isLeft: Boolean) {
        if (isLeft && !prefs.swipeLeftEnabled) {
            context.showToast(context.getString(R.string.long_press_to_enable))
            return
        }
        if (!isLeft && !prefs.swipeRightEnabled) {
            context.showToast(context.getString(R.string.long_press_to_enable))
            return
        }

        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.select_action))
        for ((label, actionValue) in gestureActionChoices()) {
            menu.option(label) {
                if (isLeft) prefs.swipeLeftAction = actionValue
                else prefs.swipeRightAction = actionValue
                if (actionValue == Constants.GestureAction.OPEN_APP) {
                    showAppListForSwipe(if (isLeft) Constants.FLAG_SET_SWIPE_LEFT_APP else Constants.FLAG_SET_SWIPE_RIGHT_APP)
                } else {
                    populateSwipeApps()
                }
            }
        }
        menu.show()
    }

    private fun gestureActionChoices() = arrayOf(
        context.getString(R.string.open_app) to Constants.GestureAction.OPEN_APP,
        context.getString(R.string.notifications) to Constants.GestureAction.OPEN_NOTIFICATIONS,
        context.getString(R.string.search) to Constants.GestureAction.OPEN_SEARCH,
        context.getString(R.string.lock_screen) to Constants.GestureAction.LOCK_SCREEN,
        context.getString(R.string.camera) to Constants.GestureAction.OPEN_CAMERA,
        context.getString(R.string.flashlight) to Constants.GestureAction.TOGGLE_FLASHLIGHT,
        context.getString(R.string.none) to Constants.GestureAction.NONE,
    )

    private fun getDoubleTapLabel(): String {
        return when (DoubleTapActionManager.getAction(context)) {
            Constants.GestureAction.OPEN_APP -> context.getString(R.string.open_app)
            Constants.GestureAction.OPEN_NOTIFICATIONS -> context.getString(R.string.notifications)
            Constants.GestureAction.OPEN_SEARCH -> context.getString(R.string.search)
            Constants.GestureAction.LOCK_SCREEN -> context.getString(R.string.lock_screen)
            Constants.GestureAction.OPEN_CAMERA -> context.getString(R.string.camera)
            Constants.GestureAction.TOGGLE_FLASHLIGHT -> context.getString(R.string.flashlight)
            Constants.GestureAction.NONE -> context.getString(R.string.none)
            else -> context.getString(R.string.lock_screen)
        }
    }

    private fun populateDoubleTapAction() {
        binding.doubleTapAction?.text = getDoubleTapLabel()
    }

    private fun showDoubleTapActionPicker() {
        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.double_tap_action))
        // Lock screen first: it is the double-tap default
        val choices = gestureActionChoices()
            .sortedByDescending { it.second == Constants.GestureAction.LOCK_SCREEN }
        for ((label, actionValue) in choices) {
            menu.option(label) {
                DoubleTapActionManager.setAction(context, actionValue)
                if (actionValue == Constants.GestureAction.OPEN_APP) {
                    showAppListForSwipe(Constants.FLAG_SET_DOUBLE_TAP_APP)
                }
                if (actionValue == Constants.GestureAction.LOCK_SCREEN) {
                    ensureLockPermission()
                }
                populateDoubleTapAction()
            }
        }
        menu.show()
    }

    private fun ensureLockPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!isAccessServiceEnabled(context)) {
                toggleAccessibilityVisibility(true)
            }
        } else {
            if (!deviceManager.isAdminActive(componentName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    context.getString(R.string.admin_permission_message)
                )
                (fragment.requireActivity() as MainActivity).enableAdminLauncher.launch(intent)
            }
        }
    }

    private fun showAppListForSwipe(flag: Int) {
        viewModel.getAppList(true)
        fragment.findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to flag)
        )
    }
}
