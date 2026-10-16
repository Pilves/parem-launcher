package com.parem.launcher.ui

import android.os.Process
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.text.BidiFormatter
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.helper.AppListRebuilder
import com.parem.launcher.helper.AppMenuPolicy
import com.parem.launcher.helper.AppMenuPolicy.Action
import com.parem.launcher.helper.GrayscaleController
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.formattedTimeSpent
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.hideKeyboard
import com.parem.launcher.helper.isSystemApp
import com.parem.launcher.helper.openAppInfo
import com.parem.launcher.helper.uninstall
import com.parem.launcher.ui.isAlive
import java.lang.ref.WeakReference

/** Shared app actions; home appends its existing slot-editing routes. */
object AppMenu {
    private var openMenu: WeakReference<BottomSheetDialog>? = null

    fun show(
        fragment: Fragment,
        app: AppModel,
        prefs: Prefs,
        viewModel: MainViewModel,
        homeSlot: Int? = null,
        hidden: Boolean = false,
        homeActions: ((BottomSheetMenu) -> Unit)? = null,
        onChanged: () -> Unit,
    ) {
        val context = fragment.context ?: return
        if (fragment.view == null || app.appPackage.isEmpty() || app.shortcutId != null) return
        if (openMenu?.get()?.isShowing == true) return
        val personal = app.user == Process.myUserHandle()
        val hiddenKey = AppListRebuilder.hiddenKey(app.appPackage, app.user.toString())
        val isHidden = hidden || hiddenKey in prefs.hiddenApps || app.appPackage in prefs.hiddenApps
        val limit = AppLimitManager.getLimit(context, app.appPackage)
        val menu = BottomSheetMenu(context).title(BidiFormatter.getInstance().unicodeWrap(app.appLabel), prominent = true)
        val actions = AppMenuPolicy.actions(personal, app.isPrivate, hidden, homeSlot != null,
            limit != null, personal && !context.isSystemApp(app.appPackage))
        for (action in actions) when (action) {
            Action.INFO -> menu.option(context.getString(R.string.a11y_app_info)) {
                if (fragment.isAlive) openAppInfo(context, app.user, app.appPackage)
            }
            Action.RENAME -> menu.option(context.getString(R.string.rename)) {
                if (fragment.isAlive) rename(fragment, app.appLabel, homeSlot != null) { name ->
                    if (homeSlot != null) prefs.setHomeAppName(homeSlot, name)
                    else prefs.setAppRenameLabel(app.appPackage, name)
                    onChanged()
                }
            }
            Action.LIMIT -> menu.option(context.getString(R.string.app_menu_time_limit), summary =
                limit?.let { context.formattedTimeSpent(it * 60_000L) } ?: context.getString(R.string.none)) {
                if (fragment.isAlive) {
                    if (context.appUsagePermissionGranted()) {
                        BadHabitDialogs.showTimeLimitPicker(context, app.appPackage, onChanged)
                            ?.dismissOnDestroy(fragment.viewLifecycleOwner)
                    } else BadHabitDialogs.showUsageAccessSheet(context).dismissOnDestroy(fragment.viewLifecycleOwner)
                }
            }
            Action.PAUSE -> menu.option(context.getString(R.string.app_menu_mindful_pause),
                checked = AppLimitManager.hasPause(context, app.appPackage)) {
                if (fragment.isAlive) {
                    AppLimitManager.setPause(context, app.appPackage, !AppLimitManager.hasPause(context, app.appPackage))
                    onChanged()
                }
            }
            Action.GRAYSCALE -> {
                val marked = GrayscaleController.isAppMarked(context, app.appPackage)
                val state = if (marked) R.string.on else if (GrayscaleController.appModeReady(context)) R.string.off else R.string.grayscale_set_up
                menu.option(context.getString(R.string.grayscale), summary = context.getString(state)) {
                    if (fragment.isAlive) {
                        GrayscaleSheet.toggleApp(fragment, app.appPackage) { viewModel.showDialog.postValue(it) }
                        onChanged()
                    }
                }
            }
            Action.HIDE, Action.SHOW -> menu.option(
                context.getString(if (isHidden) R.string.app_menu_show else R.string.app_menu_hide),
                summary = if (homeSlot != null) context.getString(R.string.app_menu_stays_home) else null,
            ) {
                if (fragment.isAlive) {
                    prefs.hiddenApps = prefs.hiddenApps.toMutableSet().apply {
                        if (isHidden) { remove(app.appPackage); remove(hiddenKey) } else add(hiddenKey)
                    }
                    onChanged()
                }
            }
            Action.UNINSTALL -> menu.gap().option(context.getString(R.string.app_menu_uninstall)) {
                if (fragment.isAlive) context.uninstall(app.appPackage)
            }
        }
        homeActions?.invoke(menu)
        val dialog = menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
        openMenu = WeakReference(dialog)
    }

    /** Home names remain local; app-list aliases remain personal-profile-only. */
    fun rename(fragment: Fragment, name: String, onHome: Boolean, onSave: (String) -> Unit) {
        val context = fragment.context ?: return
        if (fragment.view == null) return
        val input = AppCompatEditText(context).apply {
            id = View.generateViewId()
            TextViewCompat.setTextAppearance(this, R.style.TextSmall)
            setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            isCursorVisible = true
            setText(name)
            setSelectAllOnFocus(true)
        }
        val fields = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
            addView(TextView(context).apply {
                TextViewCompat.setTextAppearance(this, R.style.TextSmall)
                text = context.getString(R.string.app_menu_name)
                labelFor = input.id
                setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans80))
            })
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        lateinit var dialog: BottomSheetDialog
        fun save() {
            if (!fragment.isAdded || fragment.view == null) return
            val value = input.text.toString().trim()
            if (value.isEmpty()) { input.error = context.getString(R.string.app_menu_enter_name); return }
            onSave(value)
            input.hideKeyboard()
            dialog.dismiss()
        }
        val menu = BottomSheetMenu(context)
            .title(context.getString(if (onHome) R.string.app_menu_rename_home else R.string.app_menu_rename_list), prominent = true)
            .customView(fields)
            .option(context.getString(R.string.save), dismissOnClick = false) { save() }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { save(); true } else false
        }
        dialog = menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        input.requestFocus()
    }
}
