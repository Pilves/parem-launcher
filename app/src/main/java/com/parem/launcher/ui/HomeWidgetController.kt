package com.parem.launcher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.MainActivity
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentHomeBinding
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the home screen's multi-widget system: picking, binding, configuring,
 * restoring after reboot/uninstall, drag-to-resize, reorder, and removal.
 *
 * Created in HomeFragment.onViewCreated and released via [cleanup] in
 * onDestroyView; [isActive] guards every posted/async callback against running
 * after release.
 */
class HomeWidgetController(
    private val fragment: HomeFragment,
    private val binding: FragmentHomeBinding,
    private val prefs: Prefs,
) {

    private val context get() = binding.root.context
    private val mainActivity: MainActivity?
        get() = fragment.activity as? MainActivity

    /** Invoked whenever widget views change so the home screen can re-fit its app rows. */
    var onWidgetsChanged: (() -> Unit)? = null

    private val widgetGestureDetectors = mutableListOf<android.view.GestureDetector>()
    private var released = false

    private fun isActive(): Boolean = fragment.isAdded && !released

    fun needsRestore(): Boolean =
        getActiveContainer()?.childCount == 0 && prefs.getWidgetIdList().isNotEmpty()

    /** Cancel pending long-press timers (e.g. when returning from background). */
    fun cancelPendingLongPresses() {
        if (widgetGestureDetectors.isEmpty()) return
        val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        widgetGestureDetectors.forEach { it.onTouchEvent(cancel) }
        cancel.recycle()
    }

    fun cleanup() {
        released = true
        widgetGestureDetectors.clear()
        widgetRestoreQueue.clear()
        mainActivity?.let {
            it.onWidgetBindResult = null
            it.onWidgetConfigureResult = null
        }
        binding.widgetContainerAbove?.removeAllViews()
        binding.widgetContainerBelow?.removeAllViews()
    }

    private fun getActiveContainer(): android.widget.LinearLayout? {
        return if (prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE)
            binding.widgetContainerAbove
        else
            binding.widgetContainerBelow
    }

    private fun getActiveScrollView(): android.widget.ScrollView? {
        return if (prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE)
            binding.widgetScrollViewAbove
        else
            binding.widgetScrollViewBelow
    }

    // Queue of (oldWidgetId, providerComponentString) for widgets that need rebinding with permission
    private val widgetRestoreQueue = mutableListOf<Pair<Int, String>>()
    private var isRestoringWidgets = false

    suspend fun restoreWidgets() {
        if (isRestoringWidgets) return
        isRestoringWidgets = true
        try {
            val mainActivity = mainActivity ?: return
            prefs.migrateWidgetIfNeeded(mainActivity.appWidgetManager)
            val ids = prefs.getWidgetIdList()
            if (ids.isEmpty()) return
            val savedProviders = prefs.getAllWidgetProviders()
            widgetRestoreQueue.clear()

            // Clear existing widget views to prevent duplicates
            getActiveContainer()?.removeAllViews()

            // Gather widget info on background thread
            data class WidgetRestoreInfo(val wid: Int, val info: AppWidgetProviderInfo?, val providerStr: String?)
            val restoreInfoList = withContext(Dispatchers.IO) {
                ids.map { wid ->
                    val info = try {
                        mainActivity.appWidgetManager.getAppWidgetInfo(wid)
                    } catch (e: Exception) {
                        Log.e("HomeWidgetController", "restoreWidget: failed to get info for id=$wid", e)
                        null
                    }
                    WidgetRestoreInfo(wid, info, savedProviders[wid])
                }
            }

            // Process results on main thread
            val validIds = mutableListOf<Int>()
            for (restoreInfo in restoreInfoList) {
                try {
                    if (restoreInfo.info != null) {
                        // Widget still valid
                        val hostView = mainActivity.appWidgetHost.createView(
                            context.applicationContext, restoreInfo.wid, restoreInfo.info
                        )
                        addWidgetToContainer(hostView, restoreInfo.wid)
                        validIds.add(restoreInfo.wid)
                    } else {
                        // Widget invalidated — queue for rebind if we know the provider
                        mainActivity.appWidgetHost.deleteAppWidgetId(restoreInfo.wid)
                        if (restoreInfo.providerStr != null) {
                            widgetRestoreQueue.add(restoreInfo.wid to restoreInfo.providerStr)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeWidgetController", "restoreWidget failed for id=${restoreInfo.wid}", e)
                }
            }
            prefs.setWidgetIdList(validIds)
            updateWidgetContainerVisibility()

            // Process queued widgets that need permission-based rebinding
            if (widgetRestoreQueue.isNotEmpty()) {
                processNextWidgetRestore()
            }
        } finally {
            isRestoringWidgets = false
        }
    }

    private fun processNextWidgetRestore() {
        if (widgetRestoreQueue.isEmpty()) return
        val (oldId, providerStr) = widgetRestoreQueue.removeAt(0)
        val component = android.content.ComponentName.unflattenFromString(providerStr)
        if (component == null) {
            prefs.removeWidgetProvider(oldId)
            processNextWidgetRestore()
            return
        }

        val mainActivity = mainActivity ?: return
        val newId = mainActivity.appWidgetHost.allocateAppWidgetId()

        // Try silent bind first (works if app has system permission)
        if (mainActivity.appWidgetManager.bindAppWidgetIdIfAllowed(newId, component)) {
            completeWidgetRestore(oldId, newId, providerStr)
            return
        }

        // Need user permission — launch bind dialog
        mainActivity.pendingWidgetId = newId
        mainActivity.onWidgetBindResult = result@{ success ->
            if (!isActive()) return@result
            if (success) {
                completeWidgetRestore(oldId, newId, providerStr)
            } else {
                mainActivity.appWidgetHost.deleteAppWidgetId(newId)
                prefs.removeWidgetProvider(oldId)
                processNextWidgetRestore()
            }
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component)
        }
        mainActivity.bindWidgetLauncher.launch(intent)
    }

    private fun completeWidgetRestore(oldId: Int, newId: Int, providerStr: String) {
        val mainActivity = mainActivity ?: return
        val info = mainActivity.appWidgetManager.getAppWidgetInfo(newId)
        if (info == null) {
            mainActivity.appWidgetHost.deleteAppWidgetId(newId)
            prefs.removeWidgetProvider(oldId)
            processNextWidgetRestore()
            return
        }

        // Migrate saved height and provider to new ID
        val height = prefs.getWidgetHeight(oldId)
        prefs.setWidgetHeight(newId, height)
        prefs.setWidgetProvider(newId, providerStr)
        prefs.removeWidgetProvider(oldId)

        // Add to container and update ID list
        val hostView = mainActivity.appWidgetHost.createView(
            context.applicationContext, newId, info
        )
        addWidgetToContainer(hostView, newId)
        val ids = prefs.getWidgetIdList()
        ids.add(newId)
        prefs.setWidgetIdList(ids)
        updateWidgetContainerVisibility()

        // Process next queued widget
        processNextWidgetRestore()
    }

    private fun addWidgetToContainer(hostView: android.appwidget.AppWidgetHostView, widgetId: Int) {
        val container = getActiveContainer() ?: return
        val scrollView = getActiveScrollView() ?: return

        scrollView.layoutParams = (scrollView.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
            height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        }

        val heightDp = prefs.getWidgetHeight(widgetId)
        val heightPx = (heightDp * context.resources.displayMetrics.density).toInt()

        val wrapper = FrameLayout(context).apply {
            tag = widgetId
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                heightPx
            )
            clipChildren = true
            clipToPadding = true
        }

        hostView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        wrapper.addView(hostView)

        // Invisible overlay to capture long-press; forwards all other events to widget
        var longPressDetected = false
        val gestureDetector = android.view.GestureDetector(context,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: android.view.MotionEvent) {
                    longPressDetected = true
                    showWidgetOptionsDialog(widgetId)
                }
            })
        widgetGestureDetectors.add(gestureDetector)
        val overlay = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            contentDescription = "Widget"
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> longPressDetected = false
                    MotionEvent.ACTION_CANCEL -> longPressDetected = false
                }
                gestureDetector.onTouchEvent(event)
                if (longPressDetected) {
                    true
                } else {
                    // Forward event to the widget view underneath
                    hostView.dispatchTouchEvent(event)
                    // Must return true to keep receiving ACTION_UP/MOVE,
                    // otherwise GestureDetector never sees UP and the
                    // long-press timer always fires.
                    true
                }
            }
        }
        wrapper.addView(overlay)

        // Resize handle at the bottom edge of the widget
        val density = context.resources.displayMetrics.density
        val minHeightPx = (80 * density).toInt()
        val maxHeightPx = (600 * density).toInt()

        val handleBar = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                (40 * density).toInt(),
                (3 * density).toInt()
            ).apply { gravity = Gravity.CENTER }
            background = GradientDrawable().apply {
                setColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
                cornerRadius = 2 * density
            }
        }

        val resizeHandle = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (24 * density).toInt()
            ).apply { gravity = Gravity.BOTTOM }
            addView(handleBar)

            var initialY = 0f
            var initialHeight = 0
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = event.rawY
                        initialHeight = wrapper.layoutParams.height
                        // Prevent ScrollView from stealing the drag
                        var p = parent
                        while (p != null) {
                            p.requestDisallowInterceptTouchEvent(true)
                            p = p.parent
                        }
                        handleBar.alpha = 1.0f
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - initialY
                        val newHeight = (initialHeight + dy.toInt()).coerceIn(minHeightPx, maxHeightPx)
                        wrapper.layoutParams = (wrapper.layoutParams).apply {
                            height = newHeight
                        }
                        wrapper.requestLayout()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val finalHeightDp = (wrapper.layoutParams.height / density).toInt()
                        prefs.setWidgetHeight(widgetId, finalHeightDp)
                        // Notify widget of new size
                        wrapper.post {
                            if (!isActive()) return@post
                            val widthDp = (wrapper.width / density).toInt()
                            if (widthDp > 0 && finalHeightDp > 0) {
                                val options = Bundle().apply {
                                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, finalHeightDp)
                                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, finalHeightDp)
                                }
                                hostView.updateAppWidgetSize(options, widthDp, finalHeightDp, widthDp, finalHeightDp)
                            }
                        }
                        // Re-allow scroll interception
                        var p = parent
                        while (p != null) {
                            p.requestDisallowInterceptTouchEvent(false)
                            p = p.parent
                        }
                        handleBar.alpha = 0.5f
                        true
                    }
                    else -> false
                }
            }
        }
        wrapper.addView(resizeHandle)

        container.addView(wrapper)

        wrapper.post {
            if (!isActive()) return@post
            val widthDp = (wrapper.width / context.resources.displayMetrics.density).toInt()
            val heightDp = (wrapper.height / context.resources.displayMetrics.density).toInt()
            if (widthDp > 0 && heightDp > 0) {
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                }
                hostView.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
            }
        }
    }

    private fun updateWidgetContainerVisibility() {
        val ids = prefs.getWidgetIdList()
        val hasWidgets = ids.isNotEmpty()

        if (prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE) {
            binding.widgetScrollViewAbove?.visibility = if (hasWidgets) View.VISIBLE else View.GONE
            binding.widgetScrollViewBelow?.visibility = View.GONE
            binding.widgetContainerBelow?.removeAllViews()
        } else {
            binding.widgetScrollViewBelow?.visibility = if (hasWidgets) View.VISIBLE else View.GONE
            binding.widgetScrollViewAbove?.visibility = View.GONE
            binding.widgetContainerAbove?.removeAllViews()
        }

        resizeWidgetWrappers()
        onWidgetsChanged?.invoke()
    }

    private fun resizeWidgetWrappers() {
        val container = getActiveContainer() ?: return
        val count = container.childCount
        if (count == 0) return

        val density = context.resources.displayMetrics.density
        for (i in 0 until count) {
            val wrapper = container.getChildAt(i)
            val widgetId = wrapper.tag as? Int ?: continue
            val heightDp = prefs.getWidgetHeight(widgetId)
            wrapper.layoutParams = (wrapper.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                height = (heightDp * density).toInt()
            }
            // Notify the widget of its new size so it can re-render
            wrapper.post {
                if (!isActive()) return@post
                val hostView = (wrapper as? FrameLayout)?.getChildAt(0) as? android.appwidget.AppWidgetHostView
                    ?: return@post
                val widthDp = (wrapper.width / density).toInt()
                if (widthDp > 0 && heightDp > 0) {
                    val options = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                    }
                    hostView.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
                }
            }
        }
    }

    private fun showWidgetOptionsDialog(widgetId: Int) {
        val ids = prefs.getWidgetIdList()
        val index = ids.indexOf(widgetId)

        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.widget_options))
        menu.option(context.getString(R.string.swap_widget)) {
            showWidgetPicker { providerInfo -> bindWidget(providerInfo, replaceIndex = index) }
        }
        menu.option(context.getString(R.string.remove_widget)) { removeWidget(widgetId) }
        if (ids.size > 1 && index > 0)
            menu.option(context.getString(R.string.move_up)) { moveWidget(index, index - 1) }
        if (ids.size > 1 && index < ids.size - 1)
            menu.option(context.getString(R.string.move_down)) { moveWidget(index, index + 1) }
        menu.show()
    }

    private fun moveWidget(fromIndex: Int, toIndex: Int) {
        val ids = prefs.getWidgetIdList()
        val id = ids.removeAt(fromIndex)
        ids.add(toIndex, id)
        prefs.setWidgetIdList(ids)
        rebuildWidgetContainer()
    }

    private fun rebuildWidgetContainer() {
        val container = getActiveContainer() ?: return
        container.removeAllViews()
        val scrollView = getActiveScrollView() ?: return
        scrollView.visibility = View.GONE

        val ids = prefs.getWidgetIdList()
        if (ids.isEmpty()) return

        val mainActivity = mainActivity ?: return
        val validIds = mutableListOf<Int>()

        for (wid in ids) {
            try {
                val info = mainActivity.appWidgetManager.getAppWidgetInfo(wid) ?: continue
                val hostView = mainActivity.appWidgetHost.createView(
                    context.applicationContext, wid, info
                )
                addWidgetToContainer(hostView, wid)
                validIds.add(wid)
            } catch (e: Exception) {
                Log.e("HomeWidgetController", "rebuildWidget failed for id=$wid", e)
            }
        }
        prefs.setWidgetIdList(validIds)
        updateWidgetContainerVisibility()
    }

    private fun removeWidget(widgetId: Int) {
        val mainActivity = mainActivity ?: return
        mainActivity.appWidgetHost.deleteAppWidgetId(widgetId)
        prefs.removeWidgetProvider(widgetId)

        val ids = prefs.getWidgetIdList()
        ids.remove(widgetId)
        prefs.setWidgetIdList(ids)

        // Remove from container
        val container = getActiveContainer() ?: return
        for (i in 0 until container.childCount) {
            if (container.getChildAt(i).tag == widgetId) {
                container.removeViewAt(i)
                break
            }
        }
        updateWidgetContainerVisibility()
    }

    fun showWidgetPicker(onSelected: (AppWidgetProviderInfo) -> Unit = { bindWidget(it) }) {
        val mainActivity = mainActivity ?: return
        val pm = context.packageManager

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            data class WidgetEntry(val appName: String, val widgetLabel: String, val provider: AppWidgetProviderInfo)
            val allEntries = withContext(Dispatchers.IO) {
                val installedProviders = mainActivity.appWidgetManager.installedProviders
                installedProviders.map { provider ->
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(provider.provider.packageName, 0)).toString()
                    } catch (e: Exception) {
                        provider.provider.packageName
                    }
                    WidgetEntry(appName, provider.loadLabel(pm), provider)
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
            }

            if (!isActive()) return@launch

            if (allEntries.isEmpty()) {
                context.showToast(context.getString(R.string.no_widgets_available))
                return@launch
            }

            fun buildItems(query: String): MutableList<Pair<String, AppWidgetProviderInfo?>> {
                val items = mutableListOf<Pair<String, AppWidgetProviderInfo?>>()
                val filtered = if (query.isBlank()) allEntries
                else allEntries.filter {
                    it.appName.contains(query, true) || it.widgetLabel.contains(query, true)
                }
                var lastApp = ""
                for (entry in filtered) {
                    if (entry.appName != lastApp) {
                        items.add(Pair(entry.appName, null))
                        lastApp = entry.appName
                    }
                    items.add(Pair(entry.widgetLabel, entry.provider))
                }
                return items
            }

            val dialog = BottomSheetDialog(context)
            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_bottom_sheet)
                setPadding(0, 12.dpToPx(), 0, 0)
            }
            container.addView(View(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = 12.dpToPx()
                }
                setBackgroundColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            })
            val searchField = android.widget.EditText(context).apply {
                hint = context.getString(R.string.search_widgets)
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                textSize = 16f
                setTextColor(context.getColorFromAttr(R.attr.primaryColor))
                setHintTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
                background = null
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                isSingleLine = true
            }
            val listView = android.widget.ListView(context)

            val textColor = context.getColorFromAttr(R.attr.primaryColor)
            val headerColor = textColor
            var currentItems = buildItems("")

            val adapter = object : android.widget.BaseAdapter() {
                override fun getCount() = currentItems.size
                override fun getItem(position: Int) = currentItems[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getViewTypeCount() = 2
                override fun getItemViewType(position: Int) = if (currentItems[position].second == null) 0 else 1
                override fun isEnabled(position: Int) = currentItems[position].second != null

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val item = currentItems[position]
                    val isHeader = item.second == null
                    val textView = (convertView as? TextView) ?: TextView(context)

                    if (isHeader) {
                        textView.text = item.first
                        textView.textSize = 14f
                        textView.setTypeface(null, android.graphics.Typeface.BOLD)
                        textView.setTextColor(headerColor)
                        textView.alpha = 0.6f
                        textView.setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 4.dpToPx())
                    } else {
                        textView.text = item.first
                        textView.textSize = 16f
                        textView.setTypeface(null, android.graphics.Typeface.NORMAL)
                        textView.setTextColor(textColor)
                        textView.alpha = 1.0f
                        textView.setPadding(24.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                    }
                    return textView
                }
            }
            listView.adapter = adapter

            searchField.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    currentItems = buildItems(s?.toString() ?: "")
                    adapter.notifyDataSetChanged()
                }
            })

            listView.setOnItemClickListener { _, _, position, _ ->
                val providerInfo = currentItems[position].second ?: return@setOnItemClickListener
                dialog.dismiss()
                onSelected(providerInfo)
            }

            container.addView(searchField)
            container.addView(listView, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            ))
            dialog.setContentView(container)
            dialog.transparentSheetFrame()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            dialog.show()

            // Expand to full height so search + list stay visible above keyboard
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
        }
    }

    private fun bindWidget(providerInfo: AppWidgetProviderInfo, replaceIndex: Int = -1) {
        if (replaceIndex == -1 && prefs.getWidgetIdList().size >= 6) {
            context.showToast(context.getString(R.string.max_widgets_reached), Toast.LENGTH_SHORT)
            return
        }
        try {
            val mainActivity = mainActivity ?: return
            val widgetId = mainActivity.appWidgetHost.allocateAppWidgetId()
            mainActivity.pendingWidgetId = widgetId
            mainActivity.pendingWidgetInfo = providerInfo

            val allowed = mainActivity.appWidgetManager.bindAppWidgetIdIfAllowed(
                widgetId, providerInfo.provider
            )

            if (allowed) {
                onWidgetBound(widgetId, providerInfo, replaceIndex)
            } else {
                val capturedReplaceIndex = replaceIndex
                mainActivity.onWidgetBindResult = result@{ success ->
                    if (!isActive()) return@result
                    if (success) {
                        mainActivity.pendingWidgetInfo?.let { widgetInfo ->
                            onWidgetBound(mainActivity.pendingWidgetId, widgetInfo, capturedReplaceIndex)
                        }
                    } else {
                        mainActivity.appWidgetHost.deleteAppWidgetId(mainActivity.pendingWidgetId)
                        context.showToast(context.getString(R.string.widget_bind_permission_denied))
                    }
                }
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                }
                mainActivity.bindWidgetLauncher.launch(intent)
            }
        } catch (e: Exception) {
            Log.e("HomeWidgetController", "bindWidget failed", e)
            context.showToast(context.getString(R.string.couldnt_bind_widget, e.message))
        }
    }

    private fun onWidgetBound(widgetId: Int, providerInfo: AppWidgetProviderInfo, replaceIndex: Int) {
        try {
            val mainActivity = mainActivity ?: return

            if (providerInfo.configure != null) {
                val capturedReplaceIndex = replaceIndex
                mainActivity.onWidgetConfigureResult = result@{ success ->
                    if (!isActive()) return@result
                    if (success) {
                        finishWidgetSetup(widgetId, providerInfo, capturedReplaceIndex)
                    } else {
                        val stillValid = mainActivity.appWidgetManager.getAppWidgetInfo(widgetId)
                        if (stillValid != null) {
                            finishWidgetSetup(widgetId, providerInfo, capturedReplaceIndex)
                        } else {
                            mainActivity.appWidgetHost.deleteAppWidgetId(widgetId)
                        }
                    }
                }
                val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = providerInfo.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                mainActivity.configureWidgetLauncher.launch(configIntent)
            } else {
                finishWidgetSetup(widgetId, providerInfo, replaceIndex)
            }
        } catch (e: Exception) {
            Log.e("HomeWidgetController", "onWidgetBound failed", e)
            context.showToast(context.getString(R.string.widget_setup_failed, e.message))
        }
    }

    private fun finishWidgetSetup(widgetId: Int, providerInfo: AppWidgetProviderInfo, replaceIndex: Int) {
        try {
            val mainActivity = mainActivity ?: return
            val ids = prefs.getWidgetIdList()

            if (replaceIndex in ids.indices) {
                // Swap: remove old widget, insert new one at same position
                val oldId = ids[replaceIndex]
                mainActivity.appWidgetHost.deleteAppWidgetId(oldId)
                prefs.removeWidgetProvider(oldId)
                ids[replaceIndex] = widgetId
            } else {
                // Add new
                ids.add(widgetId)
            }
            prefs.setWidgetIdList(ids)
            prefs.setWidgetProvider(widgetId, providerInfo.provider.flattenToString())

            rebuildWidgetContainer()
        } catch (e: Exception) {
            Log.e("HomeWidgetController", "finishWidgetSetup failed", e)
            context.showToast(context.getString(R.string.couldnt_add_widget, e.message))
        }
    }
}
