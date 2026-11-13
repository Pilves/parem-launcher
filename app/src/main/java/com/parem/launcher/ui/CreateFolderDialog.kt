package com.parem.launcher.ui

import android.content.Context
import android.os.Process
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.R
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.FolderApp
import com.parem.launcher.data.FolderGroup
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.isEinkDisplay
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
    private val initial: FolderGroup? = null,
    private val onSave: (folderName: String, selected: List<FolderApp>) -> Unit,
) {

    private companion object {
        const val MAX_ITEMS = 4
    }

    fun show(): BottomSheetDialog {
        lateinit var dialog: BottomSheetDialog
        val menu = BottomSheetMenu(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 0, 24.dpToPx(), 0)
        }
        // A seed from a slot has no name yet: that is still creating a folder
        menu.title(context.getString(if (initial?.name.isNullOrEmpty()) R.string.create_folder else R.string.edit_folder))

        // Folder name input
        val nameLabel = TextView(context).apply {
            text = context.getString(R.string.folder_name)
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetCaption)
            setTextColor(context.getColorFromAttr(R.attr.textSecondaryColor))
        }
        container.addView(nameLabel)

        val nameInput = EditText(context).apply {
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetBody)
            setTextColor(context.getColorFromAttr(R.attr.textColor))
            setHintTextColor(context.getColorFromAttr(R.attr.textSecondaryColor))
            hint = context.getString(R.string.folder_name_hint)
            background = null
            setPadding(0, 8.dpToPx(), 0, 16.dpToPx())
            setText(initial?.name.orEmpty())
        }
        container.addView(nameInput)

        // Selection is keyed per row and lives in the adapter's data, so it
        // survives filtering and row recycling; click order is preserved.
        fun rowKey(pkg: String, activity: String, user: String) = "$pkg|$activity|$user"
        fun rowId(app: AppModel) = rowKey(app.appPackage, app.activityClassName.orEmpty(), app.user.toString())
        fun savedRowId(app: FolderApp) = rowKey(app.packageName, app.activityClassName, app.userString)
        val appsById = apps.associateBy(::rowId)
        // By ID, not via the list: a hidden app is not offered but must survive an edit
        val initialApps = initial?.apps.orEmpty().filter { it.url.isEmpty() }.associateBy(::savedRowId)
        lateinit var selectAppsRow: TextView
        lateinit var pickerAdapter: AppPickerAdapter
        pickerAdapter = AppPickerAdapter(
            textColor = context.getColorFromAttr(R.attr.textColor),
            maxSelected = MAX_ITEMS,
            entries = apps.map { AppPickerAdapter.Entry(rowId(it), it.appLabel) },
            initiallySelected = initialApps.keys,
            onSelectionChanged = {
                selectAppsRow.text = context.getString(R.string.picker_selected_count,
                    context.getString(R.string.select_apps), pickerAdapter.selectedIds.size)
            },
        )

        selectAppsRow = TextView(context).apply {
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetBody)
            text = context.getString(R.string.picker_selected_count,
                context.getString(R.string.select_apps), pickerAdapter.selectedIds.size)
            setTextColor(context.getColorFromAttr(R.attr.textColor))
            minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
            setPadding(0, 12.dpToPx(), 0, 12.dpToPx())
            setBackgroundResource(if (context.isEinkDisplay()) R.drawable.bg_eink_control else context.selectableBackgroundRes())
            setOnClickListener {
                pickerAdapter.filter("")
                val pickerContent = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addAppPicker(pickerAdapter)
                }
                val picker = BottomSheetMenu(context)
                    .title(context.getString(R.string.select_apps))
                    .customView(pickerContent)
                    .option(context.getString(android.R.string.ok)) {}
                    .show(resizeForKeyboard = true, scrollContent = false, fitPicker = true)
                picker.dismissWithParent(dialog)
            }
        }
        container.addView(selectAppsRow)

        // Websites sit beside the picked apps under the same 4-item cap;
        // unchecking one drops it
        val websites = mutableListOf<FolderApp>()
        val websiteRows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val textColor = context.getColorFromAttr(R.attr.textColor)
        fun addWebsite(site: FolderApp) {
            websites.add(site)
            websiteRows.addView(SheetCheckRow(context).apply {
                text = site.appName
                checked = true
                setOnClickListener {
                    websites.remove(site)
                    websiteRows.removeView(this)
                }
            })
        }
        container.addView(TextView(context).apply {
            text = context.getString(R.string.add_website)
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetCaption)
            setTextColor(textColor)
            setPadding(0, 12.dpToPx(), 0, 4.dpToPx())
            setOnClickListener {
                if (pickerAdapter.selectedIds.size + websites.size >= MAX_ITEMS) {
                    context.showToast(context.getString(R.string.folder_full))
                    return@setOnClickListener
                }
                WebsiteDialog(context) { url, label ->
                    addWebsite(FolderApp(label, "", "", Process.myUserHandle().toString(), url))
                }.show()
            }
        })
        initial?.apps.orEmpty().filter { it.url.isNotEmpty() }.forEach(::addWebsite)
        container.addView(websiteRows)

        menu.customView(container).option(context.getString(R.string.save), dismissOnClick = false) {
            val folderName = nameInput.text.toString().trim()
            val selectedApps = pickerAdapter.selectedIds.mapNotNull { id ->
                initialApps[id] ?: appsById[id]?.let {
                    FolderApp(it.appLabel, it.appPackage, it.activityClassName ?: "", it.user.toString())
                }
            } + websites
            if (folderName.isEmpty() || selectedApps.isEmpty()) {
                context.showToast(context.getString(R.string.folder_needs_name_and_app))
                return@option
            }
            if (selectedApps.size > MAX_ITEMS) {
                context.showToast(context.getString(R.string.folder_full))
                return@option
            }
            onSave(folderName, selectedApps)
            dialog.dismiss()
        }

        dialog = menu.show(resizeForKeyboard = true)
        return dialog
    }
}
