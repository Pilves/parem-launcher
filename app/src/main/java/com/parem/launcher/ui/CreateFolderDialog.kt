package com.parem.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Process
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.R
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.FolderApp
import com.parem.launcher.data.FolderGroup
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
            textSize = 14f
            setTextColor(context.getColorFromAttr(R.attr.textSecondaryColor))
        }
        container.addView(nameLabel)

        val nameInput = EditText(context).apply {
            textSize = 16f
            setTextColor(context.getColorFromAttr(R.attr.textColor))
            setHintTextColor(context.getColorFromAttr(R.attr.textSecondaryColor))
            hint = context.getString(R.string.folder_name_hint)
            background = null
            setPadding(0, 8.dpToPx(), 0, 16.dpToPx())
            setText(initial?.name.orEmpty())
        }
        container.addView(nameInput)

        // App selection label
        val appsLabel = TextView(context).apply {
            text = context.getString(R.string.select_apps)
            textSize = 14f
            setTextColor(context.getColorFromAttr(R.attr.textSecondaryColor))
            setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
        }
        container.addView(appsLabel)

        // Selection is keyed per row and lives in the adapter's data, so it
        // survives filtering and row recycling; click order is preserved.
        fun rowKey(pkg: String, activity: String, user: String) = "$pkg|$activity|$user"
        fun rowId(app: AppModel) = rowKey(app.appPackage, app.activityClassName.orEmpty(), app.user.toString())
        fun savedRowId(app: FolderApp) = rowKey(app.packageName, app.activityClassName, app.userString)
        val appsById = apps.associateBy(::rowId)
        // By ID, not via the list: a hidden app is not offered but must survive an edit
        val initialApps = initial?.apps.orEmpty().filter { it.url.isEmpty() }.associateBy(::savedRowId)
        val pickerAdapter = AppPickerAdapter(
            textColor = context.getColorFromAttr(R.attr.textColor),
            maxSelected = MAX_ITEMS,
            entries = apps.map { AppPickerAdapter.Entry(rowId(it), it.appLabel) },
            initiallySelected = initialApps.keys,
        )

        container.addAppPicker(pickerAdapter, searchPaddingLeft = 0, searchPaddingTop = 8.dpToPx())

        // Websites sit beside the picked apps under the same 4-item cap;
        // unchecking one drops it
        val websites = mutableListOf<FolderApp>()
        val websiteRows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val textColor = context.getColorFromAttr(R.attr.textColor)
        fun addWebsite(site: FolderApp) {
            websites.add(site)
            websiteRows.addView(CheckBox(context).apply {
                text = site.appName
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
        }
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
