package com.parem.launcher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.helper.ChannelFeatures
import com.parem.launcher.helper.GrayscaleController
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.copyToClipboard
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.isEinkDisplay
import com.parem.launcher.helper.isParemDefault
import com.parem.launcher.helper.showToast

/**
 * The "Grayscale" settings row: the one-time grant sheet while the permission
 * is missing, the trigger toggles once it is there.
 */
object GrayscaleSheet {

    private const val POLL_MS = 2_000L

    fun open(fragment: Fragment, onPair: () -> Unit, onChanged: () -> Unit) {
        val context = fragment.context ?: return
        if (GrayscaleController.isGranted(context)) showToggles(fragment, onChanged)
        else showGrant(fragment, onPair, onChanged)
    }

    /**
     * The app menu's "Grayscale" row (M4-WP21). Marking walks the user through
     * what the feature needs, one step per tap: the grant, usage access, Parem
     * as default home. The app is marked only once all three are in place, so a
     * mark never waits on a settings screen. Unmarking never asks.
     */
    fun toggleApp(fragment: Fragment, pkg: String, showDialog: (String) -> Unit) {
        val context = fragment.context ?: return
        when {
            GrayscaleController.isAppMarked(context, pkg) -> GrayscaleController.toggleAppMark(context, pkg)
            !GrayscaleController.isGranted(context) -> showGrant(fragment, onPair = { startPairing(fragment) }, onChanged = {})
            !context.appUsagePermissionGranted() -> showDialog(Constants.Dialog.GRAYSCALE_USAGE_ACCESS)
            !isParemDefault(context) -> showDialog(Constants.Dialog.GRAYSCALE_DEFAULT_HOME)
            else -> GrayscaleController.toggleAppMark(context, pkg)
        }
    }

    /** Settings has its own permission launcher; from an app menu, a refused prompt just means "tap again". */
    private fun startPairing(fragment: Fragment) {
        val context = fragment.context ?: return
        ChannelFeatures.startPairing(context) { permission ->
            ActivityCompat.requestPermissions(fragment.requireActivity(), arrayOf(permission), 0)
        }
    }

    /**
     * Polls for the grant while shown and resumed: the computer page and the adb
     * command land with Parem in the foreground, so onResume alone would miss
     * them. The grant turns grayscale on, since tapping "Grayscale" was the intent.
     */
    private fun showGrant(fragment: Fragment, onPair: () -> Unit, onChanged: () -> Unit) {
        val context = fragment.requireContext()
        val pkg = context.packageName
        val shortUrl = context.getString(R.string.grayscale_grant_url) +
            if (pkg != RELEASE_PACKAGE) "?pkg=$pkg" else ""
        val command = "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"

        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.grayscale_grant_title))
            .message(context.getString(R.string.grayscale_grant_body))
        ChannelFeatures.pairingPrompt(context)?.let { (steps, action) ->
            menu.message(steps)
            content.addView(actionRow(context, action, bold = true) { onPair() })
        }
        content.addView(TextView(context).apply {
            text = context.getString(R.string.grayscale_grant_computer)
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetBody)
            setTextColor(context.getColorFromAttr(R.attr.textColor))
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 8.dpToPx())
        })
        val urlView = TextView(context).apply {
            text = shortUrl
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetBody)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setTextColor(context.getColorFromAttr(R.attr.textColor))
            setPadding(24.dpToPx(), 4.dpToPx(), 24.dpToPx(), 8.dpToPx())
        }
        content.addView(urlView)
        content.addView(actionRow(context, context.getString(R.string.grayscale_share_link)) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://$shortUrl")
            context.startActivity(Intent.createChooser(send, null))
        })
        content.addView(actionRow(context, context.getString(R.string.grayscale_copy_link)) {
            context.copyToClipboard("https://$shortUrl")
            context.showToast(R.string.copied)
        })
        content.addView(actionRow(context, context.getString(R.string.grayscale_copy_command)) {
            context.copyToClipboard(command)
            context.showToast(R.string.copied)
        })

        lateinit var check: Runnable
        content.addView(actionRow(context, context.getString(R.string.grayscale_check_again), dimmed = true) {
            if (!GrayscaleController.isGranted(context)) context.showToast(R.string.grayscale_not_granted)
            else check.run()
        })
        val dialog = menu.customView(content).show()

        val lifecycle = fragment.viewLifecycleOwner.lifecycle
        check = Runnable {
            if (!dialog.isShowing) return@Runnable
            if (!GrayscaleController.isGranted(context)) {
                urlView.postDelayed(check, POLL_MS)
                return@Runnable
            }
            dialog.dismiss()
            GrayscaleController.setManual(context, true)
            context.showToast(R.string.grayscale_ready)
            onChanged()
            if (fragment.isAdded && fragment.view != null) showToggles(fragment, onChanged)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { urlView.removeCallbacks(check); check.run() }
                Lifecycle.Event.ON_PAUSE -> urlView.removeCallbacks(check)
                Lifecycle.Event.ON_DESTROY -> dialog.dismiss()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            urlView.removeCallbacks(check)
            lifecycle.removeObserver(observer)
        }
    }

    private fun showToggles(fragment: Fragment, onChanged: () -> Unit) {
        val context = fragment.context ?: return
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun toggle(label: Int, initial: Boolean, onToggle: (Boolean) -> Unit) {
            content.addView(SheetCheckRow(context).apply {
                text = context.getString(label)
                checked = initial
                setOnClickListener {
                    checked = !checked
                    onToggle(checked)
                }
            })
        }
        toggle(R.string.grayscale_now, GrayscaleController.isManual(context)) { GrayscaleController.setManual(context, it) }
        toggle(R.string.grayscale_during_focus, GrayscaleController.isOnFocus(context)) { GrayscaleController.setOnFocus(context, it) }
        toggle(R.string.grayscale_after_limit, GrayscaleController.isOnLimit(context)) { GrayscaleController.setOnLimit(context, it) }
        BottomSheetMenu(context)
            .title(context.getString(R.string.grayscale))
            .customView(content)
            .message(context.getString(R.string.grayscale_toggles_hint))
            .onDismiss { onChanged() }
            .show().dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    /** Like BottomSheetMenu.option, but the sheet stays open so the grant poll keeps running. */
    private fun actionRow(context: Context, text: String, bold: Boolean = false, dimmed: Boolean = false, onClick: () -> Unit): View =
        TextView(context).apply {
            this.text = text
            TextViewCompat.setTextAppearance(this, if (bold) R.style.TextAppearance_Parem_SheetLabel else R.style.TextAppearance_Parem_SheetBody)
            setTextColor(context.getColorFromAttr(if (dimmed) R.attr.primaryColorTrans50 else R.attr.textColor))
            setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
            setBackgroundResource(if (context.isEinkDisplay()) R.drawable.bg_eink_control else context.selectableBackgroundRes())
            setOnClickListener { onClick() }
        }

    private const val RELEASE_PACKAGE = "com.parem.launcher"
}
