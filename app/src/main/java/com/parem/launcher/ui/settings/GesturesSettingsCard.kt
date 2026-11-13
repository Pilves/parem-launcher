package com.parem.launcher.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import com.parem.launcher.MainActivity
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentSettingsBinding
import com.parem.launcher.helper.DoubleTapActionManager
import com.parem.launcher.helper.GestureLetterManager
import com.parem.launcher.helper.appLabelOrNull
import com.parem.launcher.helper.isAccessServiceEnabled
import com.parem.launcher.helper.notifications.QuietNotificationsManager
import com.parem.launcher.helper.showToast
import com.parem.launcher.listener.DeviceAdmin
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.SettingsFragment
import com.parem.launcher.ui.dismissOnDestroy
import com.parem.launcher.ui.requestLockAdmin
import com.parem.launcher.ui.showLockConsent

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
) : View.OnClickListener {

    private val context get() = binding.root.context
    private val viewLifecycle = fragment.viewLifecycleOwner.lifecycle

    private val deviceManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val componentName = ComponentName(context, DeviceAdmin::class.java)

    fun bind() {
        populateKeyboardText()
        populateSwipeApps()
        populateSwipeDownAction()
        populateDoubleTapAction()
        populateLockMethod()

        initClickListeners()
        initObservers()
        fragment.viewLifecycleOwner.lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && fragment.isBindingAlive()) {
                populateSwipeApps()
                populateDoubleTapAction()
                populateLockMethod()
            }
        })
    }

    private fun initClickListeners() {
        binding.autoShowKeyboardRow.setOnClickListener(this)
        binding.doubleTapRow?.setOnClickListener(this)
        binding.swipeLeftRow.setOnClickListener(this)
        binding.swipeRightRow.setOnClickListener(this)
        binding.flSwipeDown.setOnClickListener(this)
        binding.gestureLettersRow?.setOnClickListener(this)
        binding.lockMethod.setOnClickListener(this)
    }

    private fun initObservers() {
        viewModel.updateSwipeApps.observe(fragment.viewLifecycleOwner) {
            populateSwipeApps()
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.autoShowKeyboardRow -> toggleKeyboardText()

            R.id.swipeLeftRow -> showSwipeActionPicker(isLeft = true)
            R.id.swipeRightRow -> showSwipeActionPicker(isLeft = false)
            R.id.flSwipeDown -> showSwipeDownPicker()

            R.id.doubleTapRow -> showDoubleTapActionPicker()
            R.id.lockMethod -> toggleLockAdmin()
            R.id.gestureLettersRow -> {
                if (GestureLetterManager.isEnabled(context)) {
                    GestureLetterConfigDialog(
                        context,
                        fragment.viewLifecycleOwner,
                        onPickAppForLetter = { showAppListForSwipe(Constants.FLAG_SET_GESTURE_LETTER_APP, it) },
                        onDisabled = onWellbeingChanged,
                    ).show()
                } else {
                    GestureLetterManager.setEnabled(context, true)
                    onWellbeingChanged()
                }
            }
        }
    }

    private fun toggleKeyboardText() {
        prefs.autoShowKeyboard = !prefs.autoShowKeyboard
        populateKeyboardText()
    }

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) binding.autoShowKeyboard.text = context.getString(R.string.on)
        else binding.autoShowKeyboard.text = context.getString(R.string.off)
        binding.autoShowKeyboardRow.bindSwitchRow(prefs.autoShowKeyboard)
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
        binding.flSwipeDown.bindValueRow(binding.swipeDownAction.text)
    }

    private fun showSwipeDownPicker() {
        val menu = BottomSheetMenu(context).title(context.getString(R.string.swipe_down_for))
        listOf(R.string.notifications to Constants.SwipeDownAction.NOTIFICATIONS,
            R.string.search to Constants.SwipeDownAction.SEARCH).forEach { (label, value) ->
            menu.option(context.getString(label), checked = prefs.swipeDownAction == value, singleChoice = true) { updateSwipeDownAction(value) }
        }
        menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    private fun updateSwipeDownAction(swipeDownFor: Int) {
        if (prefs.swipeDownAction == swipeDownFor) return
        prefs.swipeDownAction = swipeDownFor
        populateSwipeDownAction()
    }

    private fun populateSwipeApps() {
        // A swipe turned off by an older version does nothing, so it reads None until a new pick
        binding.swipeLeftApp.text = if (prefs.swipeLeftEnabled) gestureActionLabel(prefs.getEffectiveSwipeLeftAction(), prefs.appNameSwipeLeft)
            else context.getString(R.string.none)
        binding.swipeRightApp.text = if (prefs.swipeRightEnabled) gestureActionLabel(prefs.getEffectiveSwipeRightAction(), prefs.appNameSwipeRight)
            else context.getString(R.string.none)
        binding.swipeLeftRow.bindValueRow(binding.swipeLeftApp.text)
        binding.swipeRightRow.bindValueRow(binding.swipeRightApp.text)
    }

    private fun actionLabelRes(action: Int): Int? = when (action) {
        Constants.GestureAction.OPEN_APP -> R.string.open_app
        Constants.GestureAction.OPEN_NOTIFICATIONS -> R.string.notifications
        Constants.GestureAction.OPEN_SEARCH -> R.string.search
        Constants.GestureAction.LOCK_SCREEN -> R.string.lock_screen
        Constants.GestureAction.OPEN_CAMERA -> R.string.camera
        Constants.GestureAction.TOGGLE_FLASHLIGHT -> R.string.flashlight
        Constants.GestureAction.NONE -> R.string.none
        Constants.GestureAction.QUIET_LIST -> R.string.quiet_list
        else -> null
    }

    private fun lockNeedsAccess(action: Int): Boolean =
        action == Constants.GestureAction.LOCK_SCREEN &&
            !isAccessServiceEnabled(context) && !deviceManager.isAdminActive(componentName)

    private fun gestureActionLabel(action: Int, appName: String): String = when {
        lockNeedsAccess(action) -> context.getString(R.string.lock_needs_access)
        action == Constants.GestureAction.OPEN_APP -> appName
        else -> actionLabelRes(action)?.let(context::getString) ?: appName
    }

    private fun showSwipeActionPicker(isLeft: Boolean) {
        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.select_action))
        val selected = if (isLeft) {
            if (prefs.swipeLeftEnabled) prefs.getEffectiveSwipeLeftAction() else Constants.GestureAction.NONE
        } else {
            if (prefs.swipeRightEnabled) prefs.getEffectiveSwipeRightAction() else Constants.GestureAction.NONE
        }
        for ((label, actionValue) in gestureActionChoices()) {
            menu.option(label, checked = actionValue == selected, singleChoice = true) {
                val previous = if (isLeft) prefs.swipeLeftAction else prefs.swipeRightAction
                if (isLeft) {
                    prefs.swipeLeftAction = actionValue
                    prefs.swipeLeftEnabled = true
                } else {
                    prefs.swipeRightAction = actionValue
                    prefs.swipeRightEnabled = true
                }
                if (actionValue == Constants.GestureAction.OPEN_APP) {
                    showAppListForSwipe(if (isLeft) Constants.FLAG_SET_SWIPE_LEFT_APP else Constants.FLAG_SET_SWIPE_RIGHT_APP)
                } else {
                    populateSwipeApps()
                }
                if (actionValue == Constants.GestureAction.LOCK_SCREEN) {
                    ensureLockPermission(onDecline = {
                        val reverted = revertedLockAction(previous)
                        if (isLeft && prefs.swipeLeftAction == Constants.GestureAction.LOCK_SCREEN) prefs.swipeLeftAction = reverted
                        else if (!isLeft && prefs.swipeRightAction == Constants.GestureAction.LOCK_SCREEN) prefs.swipeRightAction = reverted
                        if (viewLifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                            populateSwipeApps()
                            populateLockMethod()
                        }
                    })
                }
                populateLockMethod()
            }
        }
        menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    private fun gestureActionChoices() = listOfNotNull(
        context.getString(R.string.open_app) to Constants.GestureAction.OPEN_APP,
        context.getString(R.string.notifications) to Constants.GestureAction.OPEN_NOTIFICATIONS,
        context.getString(R.string.search) to Constants.GestureAction.OPEN_SEARCH,
        context.getString(R.string.lock_screen) to Constants.GestureAction.LOCK_SCREEN,
        context.getString(R.string.camera) to Constants.GestureAction.OPEN_CAMERA,
        context.getString(R.string.flashlight) to Constants.GestureAction.TOGGLE_FLASHLIGHT,
        // Offered only once the filter has been turned on; the gesture itself still handles Off
        (context.getString(R.string.quiet_list) to Constants.GestureAction.QUIET_LIST).takeIf {
            QuietNotificationsManager.state(context) != QuietNotificationsManager.State.OFF
        },
        context.getString(R.string.none) to Constants.GestureAction.NONE,
    )

    private fun getDoubleTapLabel(): String {
        val action = DoubleTapActionManager.getAction(context)
        if (action == Constants.GestureAction.OPEN_APP)
            context.appLabelOrNull(DoubleTapActionManager.getAppPackage(context))?.let { return it }
        // The double-tap picker never offers the quiet list, so it falls to the default like any unknown value
        val res = if (action == Constants.GestureAction.QUIET_LIST) null else actionLabelRes(action)
        return context.getString(res ?: R.string.lock_screen)
    }

    private fun populateDoubleTapAction() {
        val needsAccess = lockNeedsAccess(DoubleTapActionManager.getAction(context))
        binding.doubleTapAction?.text = if (needsAccess) context.getString(R.string.lock_needs_access) else getDoubleTapLabel()
        binding.doubleTapRow?.bindValueRow(binding.doubleTapAction?.text)
    }

    private fun showDoubleTapActionPicker() {
        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.double_tap_action))
        // Lock screen first: it is the double-tap default
        val choices = gestureActionChoices()
            .filter { it.second != Constants.GestureAction.QUIET_LIST }
            .sortedByDescending { it.second == Constants.GestureAction.LOCK_SCREEN }
        for ((label, actionValue) in choices) {
            menu.option(label, checked = actionValue == DoubleTapActionManager.getAction(context), singleChoice = true) {
                val previous = DoubleTapActionManager.getAction(context)
                DoubleTapActionManager.setAction(context, actionValue)
                if (actionValue == Constants.GestureAction.OPEN_APP) {
                    showAppListForSwipe(Constants.FLAG_SET_DOUBLE_TAP_APP)
                }
                if (actionValue == Constants.GestureAction.LOCK_SCREEN) {
                    ensureLockPermission(onDecline = {
                        if (DoubleTapActionManager.getAction(context) == Constants.GestureAction.LOCK_SCREEN)
                            DoubleTapActionManager.setAction(context, revertedLockAction(previous))
                        if (viewLifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                            populateDoubleTapAction()
                            populateLockMethod()
                        }
                    })
                }
                populateDoubleTapAction()
                populateLockMethod()
            }
        }
        menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    /**
     * Declining the lock consent must not leave LOCK_SCREEN stored with the
     * service off. Restoring the prior action would do exactly that when the
     * user re-picked Lock screen (double-tap defaults to it), so fall to NONE.
     */
    private fun revertedLockAction(previous: Int): Int =
        if (previous == Constants.GestureAction.LOCK_SCREEN) Constants.GestureAction.NONE else previous

    private fun ensureLockPermission(onDecline: () -> Unit) {
        if (isAccessServiceEnabled(context) || deviceManager.isAdminActive(componentName)) return
        showLockConsent(
            context,
            onAccept = {
                if (fragment.isAdded)
                    fragment.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDecline = onDecline,
            // A cancelled activation reverts exactly like "Not now"
            onUseAdmin = {
                (fragment.activity as? MainActivity)?.let { activity ->
                    requestLockAdmin(activity) { ok ->
                        if (!ok) onDecline()
                        else if (viewLifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) populateLockMethod()
                    }
                } ?: onDecline()
            },
        ).dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    private fun hasLockGesture() =
        DoubleTapActionManager.getAction(context) == Constants.GestureAction.LOCK_SCREEN ||
            prefs.getEffectiveSwipeLeftAction() == Constants.GestureAction.LOCK_SCREEN ||
            prefs.getEffectiveSwipeRightAction() == Constants.GestureAction.LOCK_SCREEN

    /**
     * The device-admin lock fallback's one permanent entry point (M4-WP18).
     * [adminActive] is passed after a removal because removeActiveAdmin
     * finishes asynchronously and isAdminActive can still say true.
     */
    private fun populateLockMethod(adminActive: Boolean = deviceManager.isAdminActive(componentName)) {
        binding.lockMethod.visibility = View.VISIBLE
        when {
            adminActive -> binding.lockMethod.text = context.getString(R.string.lock_admin_on_row)
            !isAccessServiceEnabled(context) && hasLockGesture() ->
                binding.lockMethod.text = context.getString(R.string.lock_admin_off_row)
            else -> binding.lockMethod.visibility = View.GONE
        }
    }

    private fun toggleLockAdmin() {
        if (deviceManager.isAdminActive(componentName)) {
            removeActiveAdmin()
            populateLockMethod(adminActive = false)
        } else {
            // Cancel changes nothing: the gesture was stored before this row showed
            val activity = fragment.activity as? MainActivity ?: return
            requestLockAdmin(activity) { if (fragment.isAdded) populateLockMethod() }
        }
    }

    private fun showAppListForSwipe(flag: Int, letter: Char? = null) {
        viewModel.getAppList(true)
        val args = bundleOf(Constants.Key.FLAG to flag)
        letter?.let { args.putChar(Constants.Key.LETTER, it) }
        fragment.navigateFromSettings(R.id.action_settingsFragment_to_appListFragment, args)
    }
}
