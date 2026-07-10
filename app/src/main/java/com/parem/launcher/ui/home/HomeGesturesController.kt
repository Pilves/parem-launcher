package com.parem.launcher.ui.home

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentHomeBinding
import com.parem.launcher.helper.DoubleTapActionManager
import com.parem.launcher.helper.GestureLetterManager
import com.parem.launcher.helper.SwipeUpAppManager
import com.parem.launcher.helper.expandNotificationDrawer
import com.parem.launcher.helper.isAccessServiceEnabled
import com.parem.launcher.helper.openCameraApp
import com.parem.launcher.helper.openDialerApp
import com.parem.launcher.helper.openSearch
import com.parem.launcher.helper.showToast
import com.parem.launcher.listener.OnSwipeTouchListener
import com.parem.launcher.listener.ViewSwipeTouchListener
import com.parem.launcher.ui.HomeFragment
import com.parem.launcher.ui.transparentSheetFrame

/**
 * Owns the home screen's touch input and gesture actions: the screen and
 * per-slot swipe listeners, gesture-letter overlay wiring/forwarding, swipe
 * and double-tap actions, the torch toggle (with its state-tracking callback),
 * and phone locking.
 *
 * Extracted from HomeFragment; mirrors HomeWidgetController's shape. Created
 * in HomeFragment.onViewCreated; the fragment calls [unregisterTorchCallback]
 * and [cleanupListeners] from onDestroyView.
 */
class HomeGesturesController(
    private val fragment: HomeFragment,
    private val binding: FragmentHomeBinding,
    private val prefs: Prefs,
    private val viewModel: MainViewModel,
) {

    private val context get() = binding.root.context
    private val deviceManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private var screenTouchListener: OnSwipeTouchListener? = null
    private val viewTouchListeners = mutableListOf<ViewSwipeTouchListener>()
    private var flashlightOn: Boolean = false
    private var torchCallback: android.hardware.camera2.CameraManager.TorchCallback? = null

    // Track the real torch state so toggleFlashlight() can't desync when
    // another app (or quick settings) switches the torch
    fun registerTorchCallback() {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            torchCallback = object : android.hardware.camera2.CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    flashlightOn = enabled
                }
            }.also { cameraManager.registerTorchCallback(it, null) }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Failed to register torch callback", e)
        }
    }

    fun unregisterTorchCallback() {
        torchCallback?.let {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                cameraManager.unregisterTorchCallback(it)
            } catch (_: Exception) {}
        }
        torchCallback = null
    }

    fun cleanupListeners() {
        screenTouchListener?.cleanup()
        screenTouchListener = null
        viewTouchListeners.forEach { it.cleanup() }
        viewTouchListeners.clear()
    }

    fun initSwipeTouchListener() {
        viewTouchListeners.forEach { it.cleanup() }
        viewTouchListeners.clear()
        screenTouchListener = getSwipeGestureListener(context)
        binding.mainLayout.setOnTouchListener(screenTouchListener)
        fragment.slotsController?.homeAppViews?.forEach { it.setOnTouchListener(getViewSwipeTouchListener(context, it)) }
    }

    fun initGestureLetterOverlay() {
        binding.gestureLetterOverlay?.onLetterDetected = { letter ->
            val mapping = GestureLetterManager.getMapping(context, letter)
            if (mapping != null) {
                // Recognition tick: confirms the letter registered before the app appears
                binding.gestureLetterOverlay?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                fragment.slotsController?.launchApp("", mapping.first, mapping.second, mapping.third)
            } else {
                context.showToast(context.getString(R.string.no_app_for_letter, letter.toString()))
            }
        }
    }

    fun updateGestureLetterOverlay() {
        val enabled = GestureLetterManager.isEnabled(context)
        binding.gestureLetterOverlay?.isGestureLettersEnabled = enabled
        binding.gestureLetterOverlay?.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    /**
     * Forwards a touch event to the gesture-letter overlay, translating from
     * [sourceView]'s local coordinates when needed. Returns true while the
     * overlay is capturing a letter — the caller must then swallow the event
     * so swipe/click detection doesn't run on the same stroke.
     */
    private fun forwardToLetterOverlay(sourceView: View?, motionEvent: MotionEvent): Boolean {
        val overlay = binding.gestureLetterOverlay ?: return false
        if (!overlay.isGestureLettersEnabled) return false
        val wasCapturing = overlay.isCapturing
        if (sourceView == null || sourceView === binding.mainLayout) {
            overlay.forwardTouchEvent(motionEvent)
        } else {
            // Per-view listeners deliver view-local coordinates; shift into the
            // overlay's frame so strokes starting on an app label track correctly
            val src = IntArray(2).also { sourceView.getLocationInWindow(it) }
            val dst = IntArray(2).also { overlay.getLocationInWindow(it) }
            val copy = MotionEvent.obtain(motionEvent)
            copy.offsetLocation((src[0] - dst[0]).toFloat(), (src[1] - dst[1]).toFloat())
            overlay.forwardTouchEvent(copy)
            copy.recycle()
        }
        return wasCapturing || overlay.isCapturing
    }

    private fun getSwipeGestureListener(context: Context): OnSwipeTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                if (forwardToLetterOverlay(null, motionEvent)) return true
                return super.onTouch(view, motionEvent)
            }

            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                fragment.showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick() {
                super.onLongClick()
                try {
                    // GestureDetector callbacks bypass the framework's long-press
                    // haptic, so fire it ourselves — silence here feels broken
                    binding.mainLayout.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    showHomeLongPressMenu()
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Failed to show home long press menu", e)
                }
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                DoubleTapActionManager.execute(
                    context,
                    lockPhone = { lockPhone() },
                    toggleFlashlight = { toggleFlashlight() }
                )
            }

            override fun onClick() {
                super.onClick()
            }
        }
    }

    private fun getViewSwipeTouchListener(context: Context, view: View): ViewSwipeTouchListener {
        val listener = object : ViewSwipeTouchListener(context, view) {
            override fun onTouch(v: View, motionEvent: MotionEvent): Boolean {
                if (forwardToLetterOverlay(v, motionEvent)) {
                    v.isPressed = false
                    return true
                }
                return super.onTouch(v, motionEvent)
            }

            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                val slot = try { view.tag.toString().toInt() } catch (_: Exception) { 0 }
                if (slot in 1..8 && SwipeUpAppManager.hasSwipeUpApp(context, slot)) {
                    fragment.slotsController?.launchApp(
                        SwipeUpAppManager.getSwipeUpAppName(context, slot),
                        SwipeUpAppManager.getSwipeUpAppPackage(context, slot),
                        SwipeUpAppManager.getSwipeUpAppActivity(context, slot),
                        SwipeUpAppManager.getSwipeUpAppUser(context, slot)
                    )
                } else {
                    fragment.showAppList(Constants.FLAG_LAUNCH_APP)
                }
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }
        }
        viewTouchListeners.add(listener)
        return listener
    }

    private fun textOnClick(view: View) = fragment.onClick(view)

    private fun textOnLongClick(view: View) = fragment.onLongClick(view)

    private fun swipeDownAction() {
        when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.SEARCH -> context.openSearch()
            else -> expandNotificationDrawer(context)
        }
    }

    private fun openSwipeRightApp() {
        if (!prefs.swipeRightEnabled) return
        executeGestureAction(prefs.getEffectiveSwipeRightAction()) {
            // Fallback for OPEN_APP action
            if (prefs.appPackageSwipeRight.isNotEmpty())
                fragment.slotsController?.launchApp(
                    prefs.appNameSwipeRight,
                    prefs.appPackageSwipeRight,
                    prefs.appActivityClassNameSwipeRight,
                    prefs.appUserSwipeRight
                )
            else openDialerApp(context)
        }
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        executeGestureAction(prefs.getEffectiveSwipeLeftAction()) {
            // Fallback for OPEN_APP action
            if (prefs.appPackageSwipeLeft.isNotEmpty())
                fragment.slotsController?.launchApp(
                    prefs.appNameSwipeLeft,
                    prefs.appPackageSwipeLeft,
                    prefs.appActivityClassNameSwipeLeft,
                    prefs.appUserSwipeLeft
                )
            else openCameraApp(context)
        }
    }

    private fun executeGestureAction(action: Int, openAppFallback: () -> Unit) {
        when (action) {
            Constants.GestureAction.OPEN_APP -> openAppFallback()
            Constants.GestureAction.OPEN_NOTIFICATIONS -> expandNotificationDrawer(context)
            Constants.GestureAction.OPEN_SEARCH -> context.openSearch()
            Constants.GestureAction.LOCK_SCREEN -> lockPhone()
            Constants.GestureAction.OPEN_CAMERA -> openCameraApp(context)
            Constants.GestureAction.TOGGLE_FLASHLIGHT -> toggleFlashlight()
            Constants.GestureAction.NONE -> { /* do nothing */ }
        }
    }

    private fun toggleFlashlight() {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            flashlightOn = !flashlightOn
            cameraManager.setTorchMode(cameraId, flashlightOn)
        } catch (e: Exception) {
            Log.e("HomeFragment", "Failed to toggle flashlight", e)
        }
    }

    private fun lockPhone() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isAccessServiceEnabled(context)) {
                // Trap #1 (ARCHITECTURE.md): clicking the invisible lock view emits the
                // accessibility event MyAccessibilityService matches (by contentDescription)
                // to perform GLOBAL_ACTION_LOCK_SCREEN. The no-op click handler lives in
                // HomeFragment.onClick (R.id.lock).
                binding.lock.performClick()
            } else {
                deviceManager.lockNow()
            }
        } catch (e: SecurityException) {
            prefs.lockModeOn = false
            context.showToast(context.getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
            fragment.findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
        } catch (e: Exception) {
            context.showToast(context.getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
        }
    }

    private fun showHomeLongPressMenu() {
        val dialog = BottomSheetDialog(context)
        val view = fragment.layoutInflater.inflate(R.layout.dialog_home_menu, null)
        view.findViewById<TextView>(R.id.menuAddWidget).setOnClickListener {
            dialog.dismiss()
            fragment.widgetController?.showWidgetPicker()
        }
        view.findViewById<TextView>(R.id.menuSettings).setOnClickListener {
            dialog.dismiss()
            fragment.findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            viewModel.firstOpen(false)
        }
        dialog.setContentView(view)
        dialog.transparentSheetFrame()
        dialog.show()
    }
}
