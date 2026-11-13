package com.parem.launcher.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.util.Log
import android.util.SizeF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.helper.CrashReporter
import com.parem.launcher.MainActivity
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentHomeBinding
import com.parem.launcher.helper.WidgetIdBookkeeping
import com.parem.launcher.helper.WidgetPickerLabels
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the home screen's multi-widget system: picking, binding, configuring,
 * reconciling availability, drag-to-resize, reorder, and removal.
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
        reconcileJob?.cancel()
        binding.widgetContainerAbove?.removeAllViews()
        binding.widgetContainerBelow?.removeAllViews()
    }

    // No landscape branch needed here: layout-land/fragment_home.xml places both
    // above/below scroll views inside the single right-hand widget column, so
    // either placement pref value resolves to that column beside the apps.
    private val above get() = prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE

    private fun getActiveContainer() = if (above) binding.widgetContainerAbove else binding.widgetContainerBelow

    private fun getActiveScrollView() = if (above) binding.widgetScrollViewAbove else binding.widgetScrollViewBelow

    private var isRestoringWidgets = false
    private var reconcileJob: Job? = null
    private var reconcileRequested = false
    private var forceRebuild = false
    private var renderedWidgets: List<Pair<Int, String?>>? = null
    private var renderedAbove: Boolean? = null

    /** Provider callbacks and return-home can coincide with an in-flight scan. */
    fun requestReconcile(force: Boolean = false) {
        if (!isActive()) return
        reconcileRequested = true
        forceRebuild = forceRebuild || force
        if (reconcileJob?.isActive == true) return
        reconcileJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            while (reconcileRequested && isActive()) {
                reconcileRequested = false
                val force = forceRebuild
                forceRebuild = false
                restoreWidgets(force)
            }
        }
    }

    /**
     * Keys of MainActivity.pendingWidgetOp: the system bind/configure dialog the
     * controller is waiting on. It outlives this controller (rotation, process
     * death) and the result comes back through [onPendingWidgetResult].
     */
    private object PendingOp {
        const val STAGE = "stage"
        const val WIDGET_ID = "widgetId"
        const val REPLACE_INDEX = "replaceIndex" // legacy state without a stable target ID
        const val REPLACE_ID = "replaceId"

        const val BIND = 1          // add/swap: ACTION_APPWIDGET_BIND for WIDGET_ID
        const val CONFIGURE = 2     // add/swap: configure activity for the bound WIDGET_ID
        const val RESTORE_BIND = 3  // legacy rebind started by an older process
    }

    private fun setPendingOp(stage: Int, widgetId: Int, replaceId: Int = -1) {
        mainActivity?.pendingWidgetOp = Bundle().apply {
            putInt(PendingOp.STAGE, stage)
            putInt(PendingOp.WIDGET_ID, widgetId)
            putInt(PendingOp.REPLACE_ID, replaceId)
        }
    }

    /** Bind/configure dialog result for the saved pending op, from MainActivity. */
    fun onPendingWidgetResult(success: Boolean) {
        val mainActivity = mainActivity ?: return
        if (!isActive() || isRestoringWidgets) {
            // Let the in-flight snapshot finish before applying a pending result.
            mainActivity.pendingWidgetResult = success
            return
        }
        val op = mainActivity.pendingWidgetOp ?: return
        mainActivity.pendingWidgetOp = null
        val widgetId = op.getInt(PendingOp.WIDGET_ID)
        // Old process-state bundles recorded only an index. It may now refer to
        // another widget, so cancel that legacy swap rather than guess.
        val replaceId = WidgetIdBookkeeping.pendingTarget(
            op.getInt(PendingOp.REPLACE_ID).takeIf { op.containsKey(PendingOp.REPLACE_ID) },
            op.getInt(PendingOp.REPLACE_INDEX, -1),
        )
        if (op.getInt(PendingOp.STAGE) == PendingOp.RESTORE_BIND) {
            // A restore bind started by an older version cannot prove the old
            // widget's profile. Release only the newly allocated pending ID.
            mainActivity.appWidgetHost.deleteAppWidgetId(widgetId)
            requestReconcile()
            return
        }
        // A framework failure is not evidence that the pending widget is gone.
        val info = try { mainActivity.appWidgetManager.getAppWidgetInfo(widgetId) }
        catch (e: Exception) {
            CrashReporter.reportNonFatal("HomeWidgetController: pending widget info failed", e)
            mainActivity.pendingWidgetOp = op
            mainActivity.pendingWidgetResult = success
            return
        }
        when (op.getInt(PendingOp.STAGE)) {
            PendingOp.BIND -> {
                if (success && info != null) {
                    onWidgetBound(widgetId, info, replaceId)
                } else {
                    mainActivity.appWidgetHost.deleteAppWidgetId(widgetId)
                    context.showToast(context.getString(R.string.widget_bind_permission_denied))
                }
            }
            PendingOp.CONFIGURE -> {
                // A cancelled configure still leaves a usable widget when the provider kept it bound
                if (info != null) finishWidgetSetup(widgetId, info, replaceId)
                else mainActivity.appWidgetHost.deleteAppWidgetId(widgetId)
            }
        }
    }

    private suspend fun restoreWidgets(force: Boolean) {
        if (isRestoringWidgets) {
            reconcileRequested = true
            forceRebuild = forceRebuild || force
            return
        }
        isRestoringWidgets = true
        try {
            val activity = mainActivity ?: return
            try { prefs.migrateWidgetIfNeeded(activity.appWidgetManager) }
            catch (e: Exception) {
                // The legacy ID is already retained before provider lookup; keep
                // rendering it even when the manager is temporarily unavailable.
                CrashReporter.reportNonFatal("HomeWidgetController: widget migration lookup failed", e)
            }
            val ids = prefs.getWidgetIdList()
            sweepOrphanIds(activity, ids)
            // The manager may return null or throw for inaccessible profiles; both
            // are unknown and must never be treated as proof of an uninstall.
            val infos = withContext(Dispatchers.IO) {
                ids.associateWith { id ->
                    WidgetIdBookkeeping.queryOrUnknown(
                        query = { activity.appWidgetManager.getAppWidgetInfo(id) },
                        onFailure = { e -> CrashReporter.reportNonFatal("HomeWidgetController: widget info failed for id=$id", e) },
                    )
                }
            }
            if (!isActive()) return
            if (prefs.getWidgetIdList() != ids) {
                reconcileRequested = true
                return
            }
            val signature = ids.map { id -> id to infos[id]?.let { "${it.provider.flattenToString()}@${it.profile}" } }
            if (!force && renderedWidgets == signature && renderedAbove == above) return
            val container = getActiveContainer() ?: return
            cancelPendingLongPresses()
            widgetGestureDetectors.clear()
            container.removeAllViews()
            var failedToRender = false
            for (id in ids) {
                val info = infos[id]
                if (info == null) addUnavailableWidget(id)
                else try {
                    val view = activity.appWidgetHost.createView(context.applicationContext, id, info)
                    addWidgetToContainer(view, id)
                } catch (e: Exception) {
                    CrashReporter.reportNonFatal("HomeWidgetController: widget view failed for id=$id", e)
                    failedToRender = true
                    addUnavailableWidget(id)
                }
            }
            renderedWidgets = if (failedToRender) null else signature
            renderedAbove = above
            updateWidgetContainerVisibility()
        } finally {
            isRestoringWidgets = false
            mainActivity?.let { activity ->
                activity.pendingWidgetResult?.let {
                    activity.pendingWidgetResult = null
                    onPendingWidgetResult(it)
                }
            }
        }
    }

    // Host IDs an interrupted add allocated but never put on the home screen.
    private fun sweepOrphanIds(mainActivity: MainActivity, ids: List<Int>) {
        val pendingId = mainActivity.pendingWidgetOp?.getInt(PendingOp.WIDGET_ID) ?: -1
        WidgetIdBookkeeping.orphanIds(mainActivity.appWidgetHost.appWidgetIds, ids, pendingId)
            .forEach { mainActivity.appWidgetHost.deleteAppWidgetId(it) }
    }

    private fun addUnavailableWidget(widgetId: Int) {
        val container = getActiveContainer() ?: return
        val height = (prefs.getWidgetHeight(widgetId) * context.resources.displayMetrics.density).toInt()
        val wrapper = FrameLayout(context).apply {
            tag = widgetId
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, height)
        }
        wrapper.addView(TextView(context).apply {
            text = context.getString(R.string.widget_unavailable)
            contentDescription = text
            gravity = Gravity.CENTER
            setTextColor(context.getColorFromAttr(R.attr.textColor))
            isFocusable = true
            setOnClickListener { showWidgetOptionsDialog(widgetId) }
            setOnLongClickListener { showWidgetOptionsDialog(widgetId); true }
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addView(wrapper)
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
            contentDescription = context.getString(R.string.a11y_widget)
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
                        parent?.requestDisallowInterceptTouchEvent(true)
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
                                notifyWidgetSize(hostView, widthDp, finalHeightDp)
                            }
                            onWidgetsChanged?.invoke()
                        }
                        // Re-allow scroll interception
                        parent?.requestDisallowInterceptTouchEvent(false)
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
                notifyWidgetSize(hostView, widthDp, heightDp)
            }
        }
    }

    private fun notifyWidgetSize(hostView: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The framework subtracts host padding and supplies the provider's size list.
            hostView.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
        } else {
            hostView.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
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
                    notifyWidgetSize(hostView, widthDp, heightDp)
                }
            }
        }
    }

    private fun showWidgetOptionsDialog(widgetId: Int) {
        val ids = prefs.getWidgetIdList()
        val index = ids.indexOf(widgetId)
        if (index < 0) return

        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.widget_options))
        menu.option(context.getString(R.string.swap_widget)) {
            showWidgetPicker { providerInfo -> bindWidget(providerInfo, replaceId = widgetId) }
        }
        menu.option(context.getString(R.string.remove_widget)) { removeWidget(widgetId) }
        if (ids.size > 1 && index > 0)
            menu.option(context.getString(R.string.move_up)) { moveWidget(widgetId, -1) }
        if (ids.size > 1 && index < ids.size - 1)
            menu.option(context.getString(R.string.move_down)) { moveWidget(widgetId, 1) }
        menu.show()
    }

    private fun moveWidget(widgetId: Int, offset: Int) {
        val ids = prefs.getWidgetIdList()
        val from = ids.indexOf(widgetId)
        val to = from + offset
        if (from !in ids.indices || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        prefs.setWidgetIdList(ids)
        rebuildWidgetContainer()
    }

    private fun rebuildWidgetContainer() {
        renderedWidgets = null
        requestReconcile(force = true)
    }

    private fun removeWidget(widgetId: Int) {
        val mainActivity = mainActivity ?: return
        val ids = prefs.getWidgetIdList()
        if (widgetId !in ids) return
        ids.remove(widgetId)
        mainActivity.appWidgetHost.deleteAppWidgetId(widgetId)
        prefs.removeWidgetProvider(widgetId)
        prefs.removeWidgetHeight(widgetId)

        prefs.setWidgetIdList(ids)

        renderedWidgets = null
        // Remove from container
        val container = getActiveContainer() ?: return
        container.findViewWithTag<View>(widgetId)?.let(container::removeView)
        updateWidgetContainerVisibility()
    }

    fun showWidgetPicker(onSelected: (AppWidgetProviderInfo) -> Unit = { bindWidget(it) }) {
        val mainActivity = mainActivity ?: return
        val pm = context.packageManager

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            data class WidgetEntry(
                val appName: String,
                val isOtherProfile: Boolean,
                val header: String,
                val widgetLabel: String,
                val provider: AppWidgetProviderInfo,
                val preview: RemoteViews?,
            )
            val allEntries = withContext(Dispatchers.IO) {
                val myUser = Process.myUserHandle()
                val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                // installedProviders covers only this user; work-profile providers must be asked for per profile
                val installedProviders = userManager.userProfiles.flatMap { profile ->
                    try {
                        mainActivity.appWidgetManager.getInstalledProvidersForProfile(profile)
                    } catch (e: Exception) {
                        Log.w("HomeWidgetController", "providers for $profile unavailable", e)
                        emptyList()
                    }
                }
                installedProviders.map { provider ->
                    val pkg = provider.provider.packageName
                    val appName = try {
                        // A work-only app has no ApplicationInfo in this user's PackageManager
                        val appInfo = launcherApps.getApplicationInfo(pkg, 0, provider.profile)
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        pkg
                    }
                    val isOtherProfile = provider.profile != myUser
                    val header = if (isOtherProfile) pm.getUserBadgedLabel(appName, provider.profile).toString()
                    else appName
                    WidgetEntry(appName, isOtherProfile, header, provider.loadLabel(pm), provider, loadGeneratedPreview(provider))
                }.sortedWith(
                    compareBy<WidgetEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.appName }
                        .thenBy { it.isOtherProfile }
                ).let { entries ->
                    val needsSize = WidgetPickerLabels.needsSize(entries.map { WidgetPickerLabels.Row(it.header, it.widgetLabel) })
                    entries.mapIndexed { index, entry ->
                        if (needsSize[index]) entry.copy(widgetLabel = sizedLabel(entry.widgetLabel, entry.provider)) else entry
                    }
                }
            }

            if (!isActive()) return@launch

            if (allEntries.isEmpty()) {
                context.showToast(context.getString(R.string.no_widgets_available))
                return@launch
            }

            fun buildItems(query: String): MutableList<Pair<String, WidgetEntry?>> {
                val items = mutableListOf<Pair<String, WidgetEntry?>>()
                val filtered = if (query.isBlank()) allEntries
                else allEntries.filter {
                    it.header.contains(query, true) || it.widgetLabel.contains(query, true)
                }
                var lastApp = ""
                for (entry in filtered) {
                    if (entry.header != lastApp) {
                        items.add(Pair(entry.header, null))
                        lastApp = entry.header
                    }
                    items.add(Pair(entry.widgetLabel, entry))
                }
                return items
            }

            lateinit var dialog: BottomSheetDialog
            val menu = BottomSheetMenu(context)
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
            // RemoteViews inflation is not free; keep each applied preview for the dialog's lifetime
            val previewViews = HashMap<AppWidgetProviderInfo, View?>()

            val adapter = object : android.widget.BaseAdapter() {
                override fun getCount() = currentItems.size
                override fun getItem(position: Int) = currentItems[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getViewTypeCount() = 2
                override fun getItemViewType(position: Int) = if (currentItems[position].second == null) 0 else 1
                override fun isEnabled(position: Int) = currentItems[position].second != null

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val item = currentItems[position]
                    val entry = item.second
                    if (entry == null) {
                        val textView = (convertView as? TextView) ?: TextView(context)
                        textView.text = item.first
                        textView.textSize = 14f
                        textView.setTypeface(null, android.graphics.Typeface.BOLD)
                        textView.setTextColor(headerColor)
                        textView.alpha = 0.6f
                        textView.setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 4.dpToPx())
                        return textView
                    }

                    val row = (convertView as? android.widget.LinearLayout)
                        ?: android.widget.LinearLayout(context).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            // A preview Button is explicitly focusable, and AbsListView skips the item
                            // click for rows with explicit focusables; touch interception can't fix that
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            addView(TextView(context))
                            // Swallow touches so a preview's own click intents never fire; the row click still lands
                            addView(object : FrameLayout(context) {
                                override fun onInterceptTouchEvent(ev: MotionEvent?) = true
                            }.apply {
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                                setPadding(24.dpToPx(), 0, 16.dpToPx(), 8.dpToPx())
                            })
                        }
                    val textView = row.getChildAt(0) as TextView
                    textView.text = item.first
                    textView.textSize = 16f
                    textView.setTextColor(textColor)
                    textView.setPadding(24.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())

                    val previewFrame = row.getChildAt(1) as FrameLayout
                    previewFrame.removeAllViews()
                    val previewView = entry.preview?.let { preview ->
                        previewViews.getOrPut(entry.provider) {
                            try {
                                preview.apply(context, previewFrame)
                            } catch (e: Exception) {
                                Log.w("HomeWidgetController", "preview apply failed", e)
                                null
                            }
                        }
                    }
                    if (previewView != null) {
                        (previewView.parent as? ViewGroup)?.removeView(previewView)
                        // minHeight is the provider's px size; clamp so one tall widget can't flood the list
                        val height = entry.provider.minHeight.coerceIn(48.dpToPx(), 160.dpToPx())
                        previewFrame.addView(previewView, FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT, height
                        ))
                        previewFrame.visibility = View.VISIBLE
                    } else {
                        previewFrame.visibility = View.GONE
                    }
                    return row
                }
            }
            listView.adapter = adapter

            searchField.doAfterTextChanged { s ->
                currentItems = buildItems(s?.toString() ?: "")
                adapter.notifyDataSetChanged()
            }

            listView.setOnItemClickListener { _, _, position, _ ->
                val entry = currentItems[position].second ?: return@setOnItemClickListener
                dialog.dismiss()
                onSelected(entry.provider)
            }

            menu.customView(searchField)
            listView.layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                1f,
            )
            dialog = menu.customView(listView).show(resizeForKeyboard = true, scrollContent = false)
        }
    }

    private fun sizedLabel(label: String, provider: AppWidgetProviderInfo): String {
        val density = context.resources.displayMetrics.density
        fun cells(target: Int, minPx: Int) = target.takeIf { it > 0 } ?: WidgetPickerLabels.cells((minPx / density).toInt())
        // Target cells arrived in Android 12; reading them earlier throws
        val targetCells = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return context.getString(
            R.string.widget_label_with_size, label,
            cells(if (targetCells) provider.targetCellWidth else 0, provider.minWidth),
            cells(if (targetCells) provider.targetCellHeight else 0, provider.minHeight),
        )
    }

    /** Android 15+ generated preview for the home-screen category, or null when the provider has none. */
    private fun loadGeneratedPreview(provider: AppWidgetProviderInfo): RemoteViews? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        val category = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
        if (provider.generatedPreviewCategories and category == 0) return null
        return try {
            mainActivity?.appWidgetManager?.getWidgetPreview(provider.provider, provider.profile, category)
        } catch (e: Exception) {
            Log.w("HomeWidgetController", "generated preview unavailable for ${provider.provider}", e)
            null
        }
    }

    private fun bindWidget(providerInfo: AppWidgetProviderInfo, replaceId: Int = -1) {
        if (replaceId != -1 && replaceId !in prefs.getWidgetIdList()) return
        if (replaceId == -1 && !WidgetIdBookkeeping.canAdd(prefs.getWidgetIdList())) {
            context.showToast(context.getString(R.string.max_widgets_reached), Toast.LENGTH_SHORT)
            return
        }
        if (replaceId == -1 && !newWidgetFits()) {
            context.showToast(context.getString(R.string.widget_doesnt_fit, prefs.homeAppsNum))
            return
        }
        val mainActivity = mainActivity ?: return
        var allocatedId: Int? = null
        try {
            val widgetId = mainActivity.appWidgetHost.allocateAppWidgetId()
            allocatedId = widgetId

            val allowed = mainActivity.appWidgetManager.bindAppWidgetIdIfAllowed(
                widgetId, providerInfo.profile, providerInfo.provider, null
            )

            if (allowed) {
                onWidgetBound(widgetId, providerInfo, replaceId)
            } else {
                setPendingOp(PendingOp.BIND, widgetId, replaceId = replaceId)
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, providerInfo.profile)
                }
                mainActivity.bindWidgetLauncher.launch(intent)
            }
        } catch (e: Exception) {
            allocatedId?.let { id ->
                if (mainActivity.pendingWidgetOp?.getInt(PendingOp.WIDGET_ID) == id)
                    mainActivity.pendingWidgetOp = null
                if (id !in prefs.getWidgetIdList()) mainActivity.appWidgetHost.deleteAppWidgetId(id)
            }
            CrashReporter.reportNonFatal("HomeWidgetController: bindWidget failed", e)
            context.showToast(context.getString(R.string.widget_add_failed))
        }
    }

    /**
     * Like the apps-count picker refuses counts the widgets leave no room for,
     * adding a widget refuses when its default height would push a home app
     * off screen. Only portrait shares the column; null means not measured yet.
     */
    private fun newWidgetFits(): Boolean {
        val slots = fragment.slotsController ?: return true
        if (!slots.widgetsShareColumn) return true
        val density = context.resources.displayMetrics.density
        // Prefs.getWidgetHeight default (200dp) plus the scroll view's 8dp margin the first widget brings in
        val firstWidget = prefs.getWidgetIdList().isEmpty()
        val widgetPx = (200 * density).toInt() + if (firstWidget) (8 * density).toInt() else 0
        return com.parem.launcher.helper.HomeCapacity.canAddWidget(slots.widgetRoomPx, widgetPx)
    }

    private fun onWidgetBound(widgetId: Int, providerInfo: AppWidgetProviderInfo, replaceId: Int) {
        try {
            val mainActivity = mainActivity ?: return

            if (replaceId != -1 && replaceId !in prefs.getWidgetIdList()) {
                mainActivity.appWidgetHost.deleteAppWidgetId(widgetId)
                return
            }
            if (providerInfo.configure != null) {
                setPendingOp(PendingOp.CONFIGURE, widgetId, replaceId = replaceId)
                if (providerInfo.profile != Process.myUserHandle()) {
                    // A configure activity in another profile can't be started by component from this
                    // user; the host routes it and the result comes back through onActivityResult
                    mainActivity.appWidgetHost.startAppWidgetConfigureActivityForResult(
                        mainActivity, widgetId, 0, MainActivity.REQUEST_CONFIGURE_PROFILE_WIDGET, null
                    )
                    return
                }
                val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = providerInfo.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                mainActivity.configureWidgetLauncher.launch(configIntent)
            } else {
                finishWidgetSetup(widgetId, providerInfo, replaceId)
            }
        } catch (e: Exception) {
            mainActivity?.let { activity ->
                if (activity.pendingWidgetOp?.getInt(PendingOp.WIDGET_ID) == widgetId)
                    activity.pendingWidgetOp = null
                if (widgetId !in prefs.getWidgetIdList()) activity.appWidgetHost.deleteAppWidgetId(widgetId)
            }
            CrashReporter.reportNonFatal("HomeWidgetController: onWidgetBound failed", e)
            context.showToast(context.getString(R.string.widget_add_failed))
        }
    }

    private fun finishWidgetSetup(widgetId: Int, providerInfo: AppWidgetProviderInfo, replaceId: Int) {
        try {
            val mainActivity = mainActivity ?: return
            val ids = prefs.getWidgetIdList()

            if (replaceId != -1) {
                val replaced = WidgetIdBookkeeping.replaceId(ids, replaceId, widgetId)
                if (replaced == null) {
                    // The original swap target disappeared while the system dialog was open.
                    mainActivity.appWidgetHost.deleteAppWidgetId(widgetId)
                    return
                }
                prefs.setWidgetIdList(replaced)
                prefs.setWidgetProvider(widgetId, providerInfo.provider.flattenToString())
                prefs.removeWidgetProvider(replaceId)
                prefs.removeWidgetHeight(replaceId)
                mainActivity.appWidgetHost.deleteAppWidgetId(replaceId)
            } else {
                ids.add(widgetId)
                prefs.setWidgetIdList(ids)
                prefs.setWidgetProvider(widgetId, providerInfo.provider.flattenToString())
            }

            rebuildWidgetContainer()
        } catch (e: Exception) {
            CrashReporter.reportNonFatal("HomeWidgetController: finishWidgetSetup failed", e)
            context.showToast(context.getString(R.string.widget_add_failed))
        }
    }
}
