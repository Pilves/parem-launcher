package com.parem.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Process
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.R
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.FolderApp
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.showToast

/**
 * "Create folder" sheet: name input plus a searchable app picker and
 * "Add website…" (max 4 items together),
 * extracted from HomeFragment. Pure UI — the caller loads the app list and
 * persists the created folder in [onSave], which only fires with a non-empty
 * name and at least one selected app.
 */
class CreateFolderDialog(
    private val context: Context,
    private val apps: List<AppModel>,
    private val onSave: (folderName: String, selected: List<FolderApp>) -> Unit,
) {

    private companion object {
        const val MAX_ITEMS = 4
    }

    fun show(): BottomSheetDialog {
        val dialog = BottomSheetDialog(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_bottom_sheet)
            setPadding(24.dpToPx(), 12.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }
        container.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
        })

        // Title
        val title = TextView(context).apply {
            text = context.getString(R.string.create_folder)
            textSize = 14f
            setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12.dpToPx())
        }
        container.addView(title)

        // Folder name input
        val nameLabel = TextView(context).apply {
            text = context.getString(R.string.folder_name)
            textSize = 14f
            setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
        }
        container.addView(nameLabel)

        val nameInput = EditText(context).apply {
            textSize = 16f
            setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            setHintTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            hint = context.getString(R.string.folder_name_hint)
            background = null
            setPadding(0, 8.dpToPx(), 0, 16.dpToPx())
        }
        container.addView(nameInput)

        // App selection label
        val appsLabel = TextView(context).apply {
            text = context.getString(R.string.select_apps)
            textSize = 14f
            setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
        }
        container.addView(appsLabel)

        // Selection is keyed per row and lives in the adapter's data, so it
        // survives filtering and row recycling; click order is preserved.
        fun rowId(app: AppModel) = "${app.appPackage}|${app.activityClassName ?: ""}|${app.user}"
        val appsById = apps.associateBy(::rowId)
        val pickerAdapter = AppPickerAdapter(
            textColor = context.getColorFromAttr(R.attr.primaryColor),
            maxSelected = MAX_ITEMS,
            entries = apps.map { AppPickerAdapter.Entry(rowId(it), it.appLabel) }
        )

        container.addAppPicker(pickerAdapter, searchPaddingLeft = 0, searchPaddingTop = 8.dpToPx())

        // Websites sit beside the picked apps under the same 4-item cap;
        // unchecking one drops it
        val websites = mutableListOf<FolderApp>()
        val websiteRows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val textColor = context.getColorFromAttr(R.attr.primaryColor)
        container.addView(TextView(context).apply {
            text = context.getString(R.string.add_website)
            textSize = 14f
            setTextColor(textColor)
            setPadding(0, 12.dpToPx(), 0, 4.dpToPx())
            setOnClickListener {
                if (pickerAdapter.selectedIds.size + websites.size >= MAX_ITEMS) {
                    context.showToast(context.getString(R.string.folder_full))
                    return@setOnClickListener
                }
                WebsiteDialog(context) { url, label ->
                    val site = FolderApp(label, "", "", Process.myUserHandle().toString(), url)
                    websites.add(site)
                    websiteRows.addView(CheckBox(context).apply {
                        text = label
                        textSize = 14f
                        setTextColor(textColor)
                        buttonTintList = ColorStateList.valueOf(textColor)
                        setPadding(8.dpToPx(), 2.dpToPx(), 0, 2.dpToPx())
                        isChecked = true
                        setOnCheckedChangeListener { box, _ ->
                            websites.remove(site)
                            websiteRows.removeView(box)
                        }
                    })
                }.show()
            }
        })
        container.addView(websiteRows)

        // Save button
        val saveButton = TextView(context).apply {
            text = context.getString(R.string.save)
            textSize = 16f
            setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16.dpToPx(), 0, 0)
            setOnClickListener {
                val folderName = nameInput.text.toString().trim()
                val selectedApps = pickerAdapter.selectedIds.mapNotNull { appsById[it] }.map {
                    FolderApp(it.appLabel, it.appPackage, it.activityClassName ?: "", it.user.toString())
                } + websites
                if (folderName.isEmpty() || selectedApps.isEmpty()) {
                    context.showToast(context.getString(R.string.folder_needs_name_and_app))
                    return@setOnClickListener
                }
                if (selectedApps.size > MAX_ITEMS) {
                    context.showToast(context.getString(R.string.folder_full))
                    return@setOnClickListener
                }
                onSave(folderName, selectedApps)
                dialog.dismiss()
            }
        }
        container.addView(saveButton)

        // Nested-scroll wrapper + expanded: on landscape heights the sheet
        // exceeds the screen and the save row must stay reachable
        dialog.setContentView(NestedScrollView(context).apply { addView(container) })
        dialog.transparentSheetFrame()
        dialog.disableAnimationsOnEink()
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        // Keep the filtered list visible above the keyboard, as the widget picker does
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
        return dialog
    }
}
