package com.parem.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.parem.launcher.R
import com.parem.launcher.helper.GrayscaleController
import com.parem.launcher.helper.GrayscalePairing
import com.parem.launcher.helper.copyToClipboard
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.showToast

/**
 * The "Grayscale" settings row: the one-time grant sheet while the permission
 * is missing, the trigger toggles once it is there.
 */
object GrayscaleSheet {

    private const val POLL_MS = 2_000L

    fun open(fragment: Fragment, onPair: () -> Unit, onChanged: () -> Unit) {
        val context = fragment.context ?: return
        if (GrayscaleController.isGranted(context)) showToggles(context, onChanged)
        else showGrant(fragment, onPair, onChanged)
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
        if (GrayscalePairing.isSupported()) {
            menu.message(context.getString(R.string.grayscale_grant_wifi))
            content.addView(actionRow(context, context.getString(R.string.grayscale_pair_start), bold = true) { onPair() })
        }
        content.addView(TextView(context).apply {
            text = context.getString(R.string.grayscale_grant_computer)
            textSize = 16f
            setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 8.dpToPx())
        })
        val urlView = TextView(context).apply {
            text = shortUrl
            textSize = 18f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setTextColor(context.getColorFromAttr(R.attr.primaryColor))
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
            showToggles(context, onChanged)
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

    private fun showToggles(context: Context, onChanged: () -> Unit) {
        val textColor = context.getColorFromAttr(R.attr.primaryColor)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 0, 24.dpToPx(), 8.dpToPx())
        }
        fun toggle(label: Int, checked: Boolean, onToggle: (Boolean) -> Unit) {
            content.addView(CheckBox(context).apply {
                text = context.getString(label)
                textSize = 16f
                setTextColor(textColor)
                buttonTintList = ColorStateList.valueOf(textColor)
                setPadding(8.dpToPx(), 8.dpToPx(), 0, 8.dpToPx())
                isChecked = checked
                setOnCheckedChangeListener { _, on -> onToggle(on) }
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
            .show()
    }

    /** Like BottomSheetMenu.option, but the sheet stays open so the grant poll keeps running. */
    private fun actionRow(context: Context, text: String, bold: Boolean = false, dimmed: Boolean = false, onClick: () -> Unit): View =
        TextView(context).apply {
            this.text = text
            textSize = 16f
            if (bold) setTypeface(null, Typeface.BOLD)
            setTextColor(context.getColorFromAttr(if (dimmed) R.attr.primaryColorTrans50 else R.attr.primaryColor))
            setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
            setBackgroundResource(context.selectableBackgroundRes())
            setOnClickListener { onClick() }
        }

    private const val RELEASE_PACKAGE = "com.parem.launcher"
}
