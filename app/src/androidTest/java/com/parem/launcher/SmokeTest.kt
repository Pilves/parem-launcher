package com.parem.launcher

import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import androidx.core.os.bundleOf
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.lifecycle.ViewModelProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.recyclerview.widget.RecyclerView
import com.parem.launcher.ui.AppDrawerAdapter
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.RecentHistoryAdapter
import com.parem.launcher.ui.CreateFolderDialog
import com.parem.launcher.ui.FocusModeDialog
import com.parem.launcher.ui.dismissOnDestroy
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.formattedTimeSpent
import com.parem.launcher.helper.AppOpenCounter
import com.parem.launcher.helper.WallpaperWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val testName = TestName()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val pkg = context.packageName

    // Resolved from the installed APK's own resource table: whether the debug
    // applicationId suffix shows up in accessibility resource ids is not guessed
    private val homeId = context.resources.getResourceName(R.id.homeAppsLayout)
    private val searchId = context.resources.getResourceName(R.id.search)

    @Before
    fun setUp() {
        // clearPackageData wiped prefs: skip the role chooser and onboarding
        Prefs(context).apply {
            firstOpen = false
            onboardingVersionSeen = Constants.ONBOARDING_VERSION
        }
        val out = device.executeShellCommand(
            "cmd package set-home-activity $pkg/${MainActivity::class.java.name}"
        )
        Log.i(TAG, "set-home-activity: ${out.trim()}; ids: $homeId, $searchId")
        device.pressHome()
        // checkTheme() may recreate the activity right after start, so wait on views
        assertTrue(
            "home layout not shown",
            device.wait(Until.hasObject(By.res(homeId)), TIMEOUT)
        )
    }

    @Test
    fun emptyHomeShowsOneHintAndKeepsFilledSlots() {
        val prefs = Prefs(context)
        device.executeShellCommand("mkdir -p $DIAG_DIR")
        fun home(block: (MainActivity, com.parem.launcher.ui.home.HomeSlotsController) -> Unit) {
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
                val fragment = host.childFragmentManager.primaryNavigationFragment as com.parem.launcher.ui.HomeFragment
                block(activity, fragment.slotsController!!)
            }
        }
        home { _, slots ->
            prefs.homeAppsNum = 4
            slots.populateHomeScreen(false)
            assertEquals(View.VISIBLE, slots.homeAppViews[0].visibility)
            assertEquals(context.getString(R.string.long_press_to_select_app), slots.homeAppViews[0].hint.toString())
            assertEquals(context.getString(R.string.long_press_to_select_app), slots.homeAppViews[0].contentDescription.toString())
            assertTrue(slots.homeAppViews.drop(1).all { it.visibility == View.GONE })
        }
        device.waitForIdle()
        device.executeShellCommand("screencap -p $DIAG_DIR/empty-home-first.png")
        assertTrue(device.executeShellCommand("ls $DIAG_DIR/empty-home-first.png").contains("empty-home-first.png"))
        home { _, slots ->
            prefs.setHomeAppName(1, "Settings")
            prefs.setHomeAppPackage(1, "com.android.settings")
            prefs.setHomeAppUser(1, android.os.Process.myUserHandle().toString())
            slots.populateHomeScreen(false)
            assertEquals("Settings", slots.homeAppViews[0].text.toString())
            assertEquals(View.VISIBLE, slots.homeAppViews[1].visibility)
            assertTrue(slots.homeAppViews.drop(2).all { it.visibility == View.GONE })
        }
        device.waitForIdle()
        device.executeShellCommand("screencap -p $DIAG_DIR/empty-home-one-slot.png")
        assertTrue(device.executeShellCommand("ls $DIAG_DIR/empty-home-one-slot.png").contains("empty-home-one-slot.png"))
        home { _, slots ->
            prefs.setHomeAppName(3, "Example")
            prefs.setHomeAppUrl(3, "https://example.com")
            slots.populateHomeScreen(false)
            assertEquals(View.VISIBLE, slots.homeAppViews[1].visibility)
            assertEquals(View.VISIBLE, slots.homeAppViews[2].visibility)
            assertEquals("Example", slots.homeAppViews[2].text.toString())
            com.parem.launcher.helper.FolderManager(context).createFolder(4, "Folder", emptyList())
            slots.populateHomeScreen(false)
            assertEquals(View.VISIBLE, slots.homeAppViews[3].visibility)
            assertEquals("Folder", slots.homeAppViews[3].text.toString())
            prefs.homeLayoutLocked = true
            slots.populateHomeScreen(false)
            assertEquals(View.GONE, slots.homeAppViews[1].visibility)
            assertEquals(View.VISIBLE, slots.homeAppViews[2].visibility)
        }
    }

    @Test
    fun removingAndUndoingLastSlotRestoresTheHintAndApp() {
        val prefs = Prefs(context)
        prefs.setHomeAppName(1, "Settings")
        prefs.setHomeAppPackage(1, "com.android.settings")
        prefs.setHomeAppUser(1, android.os.Process.myUserHandle().toString())
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
            val fragment = host.childFragmentManager.primaryNavigationFragment as com.parem.launcher.ui.HomeFragment
            fragment.slotsController!!.apply { populateHomeScreen(false); showHomeSlotMenu(1) }
        }
        val remove = device.wait(Until.findObject(By.text(context.getString(R.string.app_menu_remove_home))), TIMEOUT)
        assertNotNull("Remove from home missing", remove)
        remove.click()
        val undoId = context.resources.getResourceName(com.google.android.material.R.id.snackbar_action)
        val undo = device.wait(Until.findObject(By.res(undoId)), TIMEOUT)
        if (undo == null) fail("Undo missing\n${screenDiagnostics()}")
        assertTrue("empty hint missing after removal", device.wait(Until.hasObject(
            By.desc(context.getString(R.string.long_press_to_select_app))), TIMEOUT))
        undo.click()
        assertTrue("app not restored", device.wait(Until.hasObject(By.text("Settings")), TIMEOUT))
        assertEquals("com.android.settings", prefs.getHomeAppPackage(1))
    }

    @Test
    fun drawerSearchHasEqualGuttersAndRoomForRename() {
        openDrawer()
        device.waitForIdle()
        device.executeShellCommand("mkdir -p $DIAG_DIR")
        device.executeShellCommand("screencap -p $DIAG_DIR/drawer-search.png")
        assertTrue(device.executeShellCommand("ls $DIAG_DIR/drawer-search.png").contains("drawer-search.png"))
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            val search = activity.findViewById<SearchView>(R.id.search)
            val params = search.layoutParams as android.view.ViewGroup.MarginLayoutParams
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                val gutter = (20 * context.resources.displayMetrics.density + .5f).toInt()
                assertEquals(gutter, params.marginStart)
                assertEquals(gutter, params.marginEnd)
            } else {
                assertEquals(0, params.marginStart)
                assertEquals(0, params.marginEnd)
            }
            activity.findNavController(R.id.nav_host_fragment).popBackStack()
        }
        assertTrue("home did not return", device.wait(Until.hasObject(By.res(homeId)), TIMEOUT))
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            activity.findNavController(R.id.nav_host_fragment).navigate(R.id.appListFragment,
                bundleOf(Constants.Key.FLAG to Constants.FLAG_SET_HOME_APP_1,
                    Constants.Key.RENAME to true, Constants.Key.QUERY to "New name"))
        }
        val renameId = context.resources.getResourceName(R.id.appRename)
        assertTrue("rename action did not appear", device.wait(Until.hasObject(By.res(renameId)), TIMEOUT))
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT
        var separated = false
        while (!separated && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                val search = activity.findViewById<View>(R.id.search)
                val rename = activity.findViewById<View>(R.id.appRename)
                separated = search.width > 0 && rename.width > 0 &&
                    (search.right <= rename.left || rename.right <= search.left)
            }
            if (!separated) SystemClock.sleep(100)
        }
        if (!separated) fail("search overlaps rename\n${screenDiagnostics()}")
    }

    @Test
    fun wallpaperWorkRunsAndPeriodicJobIsScheduledWithoutForegroundService() {
        val prefs = Prefs(context)
        prefs.dailyWallpaper = false // Exercise the real worker without an HTTP dependency.
        val manager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<WallpaperWorker>().build()
        try {
            manager.enqueue(request).result.get()
            val deadline = SystemClock.elapsedRealtime() + 30_000
            var state: WorkInfo.State? = null
            while (SystemClock.elapsedRealtime() < deadline) {
                state = manager.getWorkInfoById(request.id).get()?.state
                if (state?.isFinished == true) break
                SystemClock.sleep(100)
            }
            assertEquals("WallpaperWorker did not complete", WorkInfo.State.SUCCEEDED, state)

            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                ViewModelProvider(activity)[MainViewModel::class.java].setWallpaperWorker()
            }
            val scheduleDeadline = SystemClock.elapsedRealtime() + 10_000
            var scheduled = false
            while (!scheduled && SystemClock.elapsedRealtime() < scheduleDeadline) {
                scheduled = manager.getWorkInfosForUniqueWork(Constants.WALLPAPER_WORKER_NAME).get()
                    .any { !it.state.isFinished }
                if (!scheduled) SystemClock.sleep(100)
            }
            assertTrue("periodic wallpaper job not scheduled", scheduled)
        } finally {
            manager.cancelWorkById(request.id).result.get()
            manager.cancelUniqueWork(Constants.WALLPAPER_WORKER_NAME).result.get()
        }
    }

    @Test
    fun hiddenDrawerKeepsQueryWhenItsAppListReloads() {
        val alpha = AppModel("Alpha Fixture", null, "fixture.alpha", null, false, android.os.Process.myUserHandle())
        val beta = AppModel("Beta Fixture", null, "fixture.beta", null, false, android.os.Process.myUserHandle())
        lateinit var model: MainViewModel
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            model = ViewModelProvider(activity)[MainViewModel::class.java]
            model.hiddenApps.value = listOf(alpha, beta)
            activity.findNavController(R.id.nav_host_fragment).navigate(
                R.id.appListFragment, bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS))
        }
        val field = device.wait(Until.findObject(By.res(
            context.resources.getResourceName(androidx.appcompat.R.id.search_src_text))), TIMEOUT)
        assertNotNull("hidden drawer search missing", field)
        field.text = "Alpha Fixture"
        assertTrue("filtered hidden app missing", device.wait(Until.hasObject(By.text("Alpha Fixture")), TIMEOUT))
        instrumentation.runOnMainSync { model.hiddenApps.value = listOf(beta) }
        assertTrue("reloaded hidden drawer did not reapply query", device.wait(
            Until.hasObject(By.text(context.getString(R.string.no_apps_found))), TIMEOUT))
        assertFalse("unrelated app shown after reload", device.hasObject(By.text("Beta Fixture")))
    }

    @Test
    fun pickerEnterWaitsForTheMatchingResults() {
        val fixture = AppModel("Alpha Fixture", null, "com.android.settings", null, false, android.os.Process.myUserHandle())
        lateinit var model: MainViewModel
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            model = ViewModelProvider(activity)[MainViewModel::class.java]
            activity.findNavController(R.id.nav_host_fragment).navigate(
                R.id.appListFragment, bundleOf(Constants.Key.FLAG to Constants.FLAG_SET_HOME_APP_1))
            model.appList.value = listOf(fixture)
        }
        assertTrue("picker fixture missing", device.wait(Until.hasObject(By.text("Alpha Fixture")), TIMEOUT))
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            activity.findViewById<SearchView>(R.id.search).setQuery("Alpha Fixture", true)
        }
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT
        while (Prefs(context).getHomeAppPackage(1) != "com.android.settings" && SystemClock.elapsedRealtime() < deadline)
            SystemClock.sleep(100)
        assertEquals("immediate Enter was lost", "com.android.settings", Prefs(context).getHomeAppPackage(1))
    }

    @Test
    fun enterCancelsAutoLaunchAlreadyPostedForTheSameResult() {
        val clicks = AtomicInteger()
        val done = CountDownLatch(1)
        val alpha = AppModel("Alpha Fixture", null, "fixture.alpha", null, false, android.os.Process.myUserHandle())
        val beta = AppModel("Beta Fixture", null, "fixture.beta", null, false, android.os.Process.myUserHandle())
        lateinit var adapter: AppDrawerAdapter
        instrumentation.runOnMainSync {
            adapter = AppDrawerAdapter(Constants.FLAG_LAUNCH_APP, android.view.Gravity.START,
                appClickListener = { _, _ -> clicks.incrementAndGet() }, appLongPressListener = {})
            adapter.onFilterCommitted = { query ->
                if (query == "Alpha Fixture") {
                    // Queue Enter first. The adapter posts its auto-launch after this callback.
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        adapter.suppressAutoLaunchForQuery(query)
                        adapter.launchFirstInList()
                        // This runs after the already-posted auto-launch.
                        android.os.Handler(android.os.Looper.getMainLooper()).post { done.countDown() }
                    }
                }
            }
            adapter.setAppList(mutableListOf(alpha, beta))
            adapter.filter.filter("Alpha Fixture")
        }
        assertTrue("controlled launch sequence did not finish", done.await(10, TimeUnit.SECONDS))
        assertEquals("posted auto-launch was not cancelled", 1, clicks.get())
    }

    @Test
    fun immediateEnterLaunchesSingleResultOnlyOnceWithAutoLaunchEnabled() {
        Prefs(context).autoLaunchSingleMatch = true
        val before = AppOpenCounter.getCounts(context)["com.android.settings"] ?: 0
        openDrawer()
        var settingsLoaded = false
        val loadDeadline = SystemClock.elapsedRealtime() + TIMEOUT
        while (!settingsLoaded && SystemClock.elapsedRealtime() < loadDeadline) {
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                settingsLoaded = ViewModelProvider(activity)[MainViewModel::class.java].appList.value
                    ?.any { it.appPackage == "com.android.settings" && it.appLabel == "Settings" } == true
            }
            if (!settingsLoaded) SystemClock.sleep(100)
        }
        if (!settingsLoaded) fail("Settings missing from loaded app list\n${screenDiagnostics()}")
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            activity.findViewById<SearchView>(R.id.search).setQuery("Settings", true)
        }
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT
        while ((AppOpenCounter.getCounts(context)["com.android.settings"] ?: 0) == before &&
            SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        // Let a wrongly posted second auto-launch run before checking the count.
        SystemClock.sleep(1_000)
        assertEquals("immediate Enter launched Settings more than once", before + 1,
            AppOpenCounter.getCounts(context)["com.android.settings"] ?: 0)
    }

    @Test
    fun launcherIsHome() {
        assertTrue(device.wait(Until.hasObject(By.res(homeId)), TIMEOUT))
        assertEquals(pkg, device.currentPackageName)
    }

    @Test
    fun swipeUpOpensDrawer() {
        openDrawer()
    }

    @Test
    fun recentAppTapPassesItsLabelAsLaunchSource() {
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            val app = AppModel("Recent Fixture", null, "fixture.recent", null, false, android.os.Process.myUserHandle())
            var clickedApp: AppModel? = null
            var clickedView: View? = null
            val adapter = RecentHistoryAdapter(android.view.Gravity.START,
                onApp = { model, view -> clickedApp = model; clickedView = view }, onQuery = {})
            adapter.submit(listOf(RecentHistoryAdapter.Row.App(app)))
            val holder = adapter.onCreateViewHolder(RecyclerView(activity), 1)
            adapter.onBindViewHolder(holder, 1)
            holder.itemView.performClick()
            assertEquals(app, clickedApp)
            assertTrue("recent app label was not passed as launch source", clickedView === holder.itemView)
        }
    }

    @Test
    fun sheetOptionCapturesLabelBoundsBeforeDismissal() {
        val done = CountDownLatch(1)
        var bounds: Rect? = null
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            BottomSheetMenu(activity).optionWithSource("Fixture source") {
                bounds = it
                done.countDown()
            }.show()
        }
        val row = device.wait(Until.findObject(By.desc("Fixture source")), TIMEOUT)
        assertNotNull("sheet row was not shown", row)
        row!!.click()
        assertTrue("sheet action was not invoked", done.await(5, TimeUnit.SECONDS))
        assertTrue("label bounds were not captured before dismissal", bounds?.isEmpty == false)
    }

    @Test
    fun drawerClosesAfterPullPastThresholdWithReleaseVelocity() {
        Prefs(context).autoShowKeyboard = false
        openDrawer()
        val bounds = Rect()
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            val list = activity.findViewById<RecyclerView>(R.id.recyclerView)
            list.scrollToPosition(0)
            assertTrue("drawer list has no visible bounds", list.getGlobalVisibleRect(bounds))
        }
        device.waitForIdle()
        val density = context.resources.displayMetrics.density
        val startY = bounds.top + (12 * density).toInt()
        val endY = minOf(bounds.bottom - 1, startY + (190 * density).toInt())
        assertTrue("drawer list too short for pull test", endY - startY > 100 * density)
        // 100 steps is approximately 500 ms; ACTION_UP retains fling velocity.
        device.swipe(bounds.centerX(), startY, bounds.centerX(), endY, 100)
        assertTrue("drawer stayed open after a threshold pull", device.wait(Until.hasObject(By.res(homeId)), TIMEOUT))
        assertFalse("drawer search remained visible", device.hasObject(By.res(searchId)))
    }

    @Test
    fun omniboxLaunchesApp() {
        openDrawer()
        // R.id.search is the SearchView container; setText only works on its inner field
        val field = device.wait(
            Until.findObject(By.res("${searchId.substringBefore(':')}:id/search_src_text")),
            TIMEOUT
        )
        assertNotNull("search field not found", field)
        field.click()
        field.text = "Settings"
        // Filtering and list submission are asynchronous. Do not submit an old row.
        val settingsRow = By.res(context.resources.getResourceName(R.id.appTitle)).text("Settings")
        val ready = device.wait(Until.hasObject(By.pkg(SETTINGS_PKG).depth(0)), 500L)
            || device.wait(Until.hasObject(settingsRow), TIMEOUT)
        if (ready && device.currentPackageName != SETTINGS_PKG) device.pressEnter()
        val launched = device.wait(Until.hasObject(By.pkg(SETTINGS_PKG).depth(0)), TIMEOUT)
        if (!launched) {
            val hierarchy = ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchy)
            assertTrue("Settings not launched; package=${device.currentPackageName}; " +
                hierarchy.toString("UTF-8"), false)
        }
    }

    @Test
    fun drawerHintStaysBelowSearchAtNormalAndLargeFont() {
        val originalScale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            for (scale in listOf("1.0", "2.0")) {
                device.executeShellCommand("settings put system font_scale $scale")
                device.pressHome()
                assertTrue(device.wait(Until.hasObject(By.res(homeId)), TIMEOUT))
                openDrawer()
                val field = device.wait(Until.findObject(By.res(
                    context.resources.getResourceName(androidx.appcompat.R.id.search_src_text)
                )), TIMEOUT)
                assertNotNull("search field missing at scale $scale", field)
                field.text = "focus"
                val tip = device.wait(Until.findObject(By.res(
                    context.resources.getResourceName(R.id.appDrawerTip)
                ).textContains("Focus")), TIMEOUT)
                assertNotNull("settings hint missing at scale $scale", tip)
                assertTrue("hint overlaps search at scale $scale",
                    tip.visibleBounds.top >= field.visibleBounds.bottom)
            }
        } finally {
            if (originalScale == "null") device.executeShellCommand("settings delete system font_scale")
            else device.executeShellCommand("settings put system font_scale $originalScale")
        }
    }

    @Test
    fun focusSearchKeepsResultsAboveKeyboard() {
        var sheet: BottomSheetDialog? = null
        try {
            device.waitForIdle(TIMEOUT)
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                sheet = FocusModeDialog(activity, (1..12).map {
                    "example.$it" to "Example $it"
                }).show().dismissOnDestroy(activity)
            }
            val search = device.wait(Until.findObject(By.clazz(EditText::class.java)
                .text(context.getString(R.string.search_apps))), TIMEOUT)
            assertNotNull("Focus app search missing", search)
            search.click()
            search.text = "Example"
            val deadline = System.currentTimeMillis() + TIMEOUT
            var visible = false
            var bounds = ""
            while (!visible && System.currentTimeMillis() < deadline) {
                var keyboardTop = 0
                instrumentation.runOnMainSync {
                    val decor = sheet!!.window!!.decorView
                    val insets = ViewCompat.getRootWindowInsets(decor)
                    if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                        keyboardTop = device.displayHeight -
                            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    }
                }
                // Filtering can replace a row between lookup and reading its bounds.
                val result = try {
                    device.findObject(By.text("Example 1"))?.visibleBounds
                } catch (_: StaleObjectException) { null }
                bounds = "keyboardTop=$keyboardTop result=$result"
                visible = keyboardTop > 0 && result != null && result.height() > 0 &&
                    result.bottom <= keyboardTop
                if (!visible) android.os.SystemClock.sleep(100)
            }
            assertTrue("Focus results hidden below keyboard: $bounds", visible)
        } finally {
            instrumentation.runOnMainSync { sheet?.dismiss() }
        }
    }

    @Test
    fun folderPickerDragDownScrollsListInsteadOfClosingSheet() {
        var sheet: BottomSheetDialog? = null
        var list: RecyclerView? = null
        fun listOffset(): Int {
            var offset = 0
            instrumentation.runOnMainSync { offset = list!!.computeVerticalScrollOffset() }
            return offset
        }
        try {
            device.waitForIdle(TIMEOUT)
            // Outside touch mode the sheet would focus its name field and could raise
            // the keyboard mid-test, resizing the list under the swipes
            instrumentation.setInTouchMode(true)
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                val apps = (1..40).map {
                    AppModel("Example $it", null, "example.$it", "Main", user = android.os.Process.myUserHandle())
                }
                sheet = CreateFolderDialog(activity, apps) { _, _ -> }.show().dismissOnDestroy(activity)
            }
            assertNotNull("Folder picker missing", device.wait(Until.findObject(By.text("Example 1")), TIMEOUT))
            device.waitForIdle(TIMEOUT)
            val bounds = Rect()
            instrumentation.runOnMainSync {
                fun find(view: View): RecyclerView? = view as? RecyclerView
                    ?: (view as? ViewGroup)?.let { group ->
                        (0 until group.childCount).firstNotNullOfOrNull { find(group.getChildAt(it)) }
                    }
                list = find(sheet!!.window!!.decorView)
                // Visible part only, in screen coordinates: the swipes must start on the list
                list!!.getGlobalVisibleRect(bounds)
                val window = IntArray(2).also { list!!.rootView.getLocationOnScreen(it) }
                bounds.offset(window[0], window[1])
            }
            val x = bounds.centerX()
            val upper = bounds.top + bounds.height() / 5
            val lower = bounds.bottom - bounds.height() / 5
            device.swipe(x, lower, x, upper, 100)
            device.waitForIdle(TIMEOUT)
            val scrolled = listOffset()
            assertTrue("List did not scroll down; bounds=$bounds", scrolled > 0)

            device.swipe(x, upper, x, lower, 100)
            device.waitForIdle(TIMEOUT)
            var showing = false
            var state = -1
            instrumentation.runOnMainSync {
                showing = sheet!!.isShowing
                state = sheet!!.behavior.state
            }
            assertTrue("Dragging the list down closed the sheet", showing)
            assertEquals("Dragging the list down moved the sheet", BottomSheetBehavior.STATE_EXPANDED, state)
            val after = listOffset()
            assertTrue("List did not scroll back up: $scrolled -> $after", after < scrolled)
        } finally {
            instrumentation.runOnMainSync { sheet?.dismiss() }
        }
    }

    @Test
    fun rotatesOnLargeScreenOnly() {
        try {
            device.setOrientationLandscape()
            if (context.resources.configuration.smallestScreenWidthDp >= 600) {
                assertTrue(
                    "home layout not shown in landscape",
                    device.wait(Until.hasObject(By.res(homeId)), TIMEOUT)
                )
                // Save real emulator frames on each tablet for WP28 review.
                // The fixture here has no widget; this is not a complete WP28 pass.
                device.executeShellCommand("mkdir -p $DIAG_DIR")
                device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-landscape-before.png")
                openDrawer()
                device.pressBack()
                if (!device.wait(Until.hasObject(By.res(homeId)), 1500)) device.pressBack()
                assertTrue("home missing after drawer", device.wait(Until.hasObject(By.res(homeId)), TIMEOUT))
                device.waitForIdle()
                device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-landscape-after.png")
            } else {
                // The portrait lock wins below sw600
                device.waitForIdle()
                assertEquals(0, device.displayRotation)
            }
        } finally {
            device.setOrientationNatural()
        }
    }

    @Test
    fun homePaddingClearsClockInBothOrientations() {
        fun assertClearance() {
            device.waitForIdle()
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                val clock = activity.findViewById<View>(R.id.dateTimeLayout)
                val apps = activity.findViewById<View>(R.id.homeAppsLayout)
                val widgetColumn = activity.findViewById<View>(R.id.widgetScrollViewAbove).parent as View
                val clockAt = IntArray(2)
                clock.getLocationInWindow(clockAt)
                for (column in listOf(apps, widgetColumn).distinct()) {
                    val at = IntArray(2)
                    column.getLocationInWindow(at)
                    val gap = (8 * context.resources.displayMetrics.density).toInt()
                    assertEquals("clock clearance must use the column's window origin",
                        (clockAt[1] + clock.height - at[1] + gap).coerceAtLeast(0), column.paddingTop)
                }
            }
        }
        assertClearance()
        if (context.resources.configuration.smallestScreenWidthDp >= 600) {
            try {
                device.setOrientationLandscape()
                assertTrue(device.wait(Until.hasObject(By.res(homeId)), TIMEOUT))
                assertClearance()
            } finally {
                device.setOrientationNatural()
            }
        }
    }

    @Test
    fun landscapeFirstRunAndScreenTime() {
        if (context.resources.configuration.smallestScreenWidthDp < 600) return
        try {
            device.setOrientationLandscape()
            device.waitForIdle()
            device.executeShellCommand("appops set $pkg GET_USAGE_STATS allow")
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                activity.recreate()
            }
            assertTrue("home missing", device.wait(Until.hasObject(By.res(homeId)), TIMEOUT))
            device.waitForIdle()
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                ViewModelProvider(activity)[MainViewModel::class.java].apply {
                    firstOpen.value = true
                    isParemDefault.value = false
                }
            }
            val banner = By.res(context.resources.getResourceName(R.id.setDefaultLauncher))
            val tips = By.res(context.resources.getResourceName(R.id.firstRunTips))
            assertTrue("default banner missing", device.wait(Until.hasObject(banner), TIMEOUT))
            assertFalse("first-run tips overlap the default banner", device.hasObject(tips))
            device.executeShellCommand("mkdir -p $DIAG_DIR")
            device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-landscape-banner.png")
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                ViewModelProvider(activity)[MainViewModel::class.java].isParemDefault.value = true
            }
            assertTrue("first-run tips missing after default selection", device.wait(Until.hasObject(tips), TIMEOUT))
            assertFalse("default banner remains", device.hasObject(banner))
            assertTrue("screen-time value missing", device.wait(Until.hasObject(By.res(context.resources.getResourceName(R.id.tvScreenTime))), TIMEOUT))
            device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-landscape-first-run.png")
        } finally {
            device.setOrientationNatural()
        }
    }

    @Test
    fun landscapeWidgetSurvivesDrawerRoundTrip() {
        if (context.resources.configuration.smallestScreenWidthDp < 600) return
        try {
            device.setOrientationLandscape()
            device.waitForIdle()
            device.executeShellCommand("appwidget grantbind --package $pkg --user 0")
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                val provider = ComponentName(instrumentation.context.packageName, LandscapeFixtureWidget::class.java.name)
                val id = activity.appWidgetHost.allocateAppWidgetId()
                assertTrue("fixture widget bind refused", activity.appWidgetManager.bindAppWidgetIdIfAllowed(id, provider))
                Prefs(context).apply {
                    setWidgetIdList(listOf(id))
                    setWidgetProvider(id, provider.flattenToString())
                    setWidgetHeight(id, 500)
                    homeAppsNum = 4
                    setHomeAppName(1, "Settings")
                    setHomeAppPackage(1, "com.android.settings")
                    setHomeAppUser(1, android.os.Process.myUserHandle().toString())
                }
                activity.recreate()
            }
            assertTrue("fixture widget missing", device.wait(Until.hasObject(By.text("WP28 fixture widget")), TIMEOUT))
            device.waitForIdle()
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                val scroll = listOfNotNull(
                    activity.findViewById<ScrollView>(R.id.widgetScrollViewAbove),
                    activity.findViewById<ScrollView>(R.id.widgetScrollViewBelow),
                ).first { it.isShown }
                val container = scroll.getChildAt(0) as android.view.ViewGroup
                val wrapper = container.getChildAt(0) as android.view.ViewGroup
                val handle = wrapper.getChildAt(wrapper.childCount - 1)
                val before = wrapper.layoutParams.height
                val now = android.os.SystemClock.uptimeMillis()
                fun send(action: Int, y: Float) {
                    val event = android.view.MotionEvent.obtain(now, now, action, 10f, y, 0)
                    handle.dispatchTouchEvent(event)
                    event.recycle()
                }
                send(android.view.MotionEvent.ACTION_DOWN, 10f)
                send(android.view.MotionEvent.ACTION_MOVE, 293f)
                send(android.view.MotionEvent.ACTION_UP, 293f)
                assertTrue("widget resize did not increase its height", wrapper.layoutParams.height > before)
            }
            device.waitForIdle()
            assertTrue("filled slot disappeared after widget resize", device.wait(Until.hasObject(By.text("Settings")), TIMEOUT))
            assertTrue("empty slot hint disappeared after widget resize", device.wait(Until.hasObject(
                By.desc(context.getString(R.string.long_press_to_select_app))), TIMEOUT))
            device.executeShellCommand("mkdir -p $DIAG_DIR")
            device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-widget-landscape-before.png")
            openDrawer()
            device.pressBack()
            if (!device.wait(Until.hasObject(By.res(homeId)), 1500)) device.pressBack()
            assertTrue("fixture widget missing after drawer", device.wait(Until.hasObject(By.text("WP28 fixture widget")), TIMEOUT))
            device.waitForIdle()
            device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-widget-landscape-after.png")
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                ViewModelProvider(activity)[MainViewModel::class.java].apply {
                    firstOpen.value = true
                    isParemDefault.value = true
                }
            }
            val tips = device.wait(Until.findObject(By.res(context.resources.getResourceName(R.id.firstRunTips))), TIMEOUT)
            assertNotNull("first-run tips missing with widget", tips)
            device.waitForIdle()
            val widget = device.wait(Until.findObject(By.text("WP28 fixture widget")), TIMEOUT)
            assertNotNull("widget missing with first-run tips", widget)
            device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-widget-and-tips.png")
            assertFalse("first-run tips overlap the widget: tips=${tips.visibleBounds}, widget=${widget.visibleBounds}",
                Rect.intersects(tips.visibleBounds, widget.visibleBounds))
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                val scroll = listOfNotNull(
                    activity.findViewById<ScrollView>(R.id.widgetScrollViewAbove),
                    activity.findViewById<ScrollView>(R.id.widgetScrollViewBelow),
                ).first { it.isShown }
                scroll.scrollTo(0, scroll.getChildAt(0).height)
                assertTrue("tall widget cannot scroll within its reserved viewport", scroll.scrollY > 0)
            }
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                ViewModelProvider(activity)[MainViewModel::class.java].isParemDefault.value = false
            }
            val banner = device.wait(Until.findObject(By.res(context.resources.getResourceName(R.id.setDefaultLauncher))), TIMEOUT)
            assertNotNull("default-home banner missing with widget", banner)
            device.waitForIdle()
            val widgetWithBanner = device.wait(Until.findObject(By.text("WP28 fixture widget")), TIMEOUT)
            assertNotNull("widget missing with default-home banner", widgetWithBanner)
            device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-widget-and-banner.png")
            assertFalse("default-home banner overlaps the widget: banner=${banner.visibleBounds}, widget=${widgetWithBanner.visibleBounds}",
                Rect.intersects(banner.visibleBounds, widgetWithBanner.visibleBounds))
        } finally {
            device.setOrientationNatural()
        }
    }

    @Test
    fun durationsFollowThePhoneLanguage() {
        fun inLocale(tag: String) = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        ).formattedTimeSpent(12 * 60_000L)
        assertEquals("12m", inLocale("en"))
        assertEquals("12 Min.", inLocale("de"))
        assertEquals("12 мин", inLocale("ru"))
    }

    /**
     * A main-thread stall delivers the down event after GestureDetector's
     * long-press deadline, which it counts from the hardware down time. The
     * swipe's moves are batched to the next frame, so the overdue timer runs
     * first unless the app judges the hold by what it has actually seen.
     */
    @Test
    fun swipeDeliveredAfterAStallStillOpensDrawer() {
        device.waitForIdle(TIMEOUT)
        val x = device.displayWidth / 2f
        val startY = device.displayHeight * 3 / 4f
        val endY = device.displayHeight / 4f
        val downTime = SystemClock.uptimeMillis() - ViewConfiguration.getLongPressTimeout() - 200
        fun inject(action: Int, y: Float, time: Long) {
            val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue("injection failed", instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        inject(MotionEvent.ACTION_DOWN, startY, downTime)
        for (step in 1..20) {
            inject(MotionEvent.ACTION_MOVE, startY + (endY - startY) * step / 20, SystemClock.uptimeMillis())
            SystemClock.sleep(5)
        }
        inject(MotionEvent.ACTION_UP, endY, SystemClock.uptimeMillis())
        assertDrawerOpened()
    }

    @Test
    fun swipeDownRowStaysWhileItsPickerIsOpen() {
        device.waitForIdle(TIMEOUT)
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            activity.findNavController(R.id.nav_host_fragment).navigate(R.id.action_mainFragment_to_settingsFragment)
        }
        device.waitForIdle(TIMEOUT)
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            val row = activity.findViewById<View>(R.id.flSwipeDown)
            val picker = activity.findViewById<View>(R.id.swipeDownSelectLayout)
            activity.findViewById<View>(R.id.tvGestures).performClick()
            assertTrue("row not revealed", row.isVisible)
            row.performClick()
            assertTrue("row vanished on tap", row.isVisible)
            assertTrue("picker not shown", picker.isVisible)
            activity.findViewById<View>(R.id.notifications).performClick()
            assertTrue("row vanished after picking", row.isVisible)
            assertFalse("picker stayed open", picker.isVisible)
        }
    }

    private fun openDrawer() {
        // A visible home node can precede the last startup/rotation layout pass.
        device.waitForIdle(TIMEOUT)
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20)
        assertDrawerOpened()
    }

    private fun assertDrawerOpened() {
        if (!device.wait(Until.hasObject(By.res(searchId)), TIMEOUT)) {
            val diagnostics = screenDiagnostics()
            Log.e(TAG, diagnostics)
            fail("drawer not opened\n$diagnostics")
        }
    }

    /** Taken at the moment of failure: instrumentation exit kills the process and the evidence with it. */
    private fun screenDiagnostics(): String {
        fun shell(command: String) = device.executeShellCommand(command)
        val name = "${System.currentTimeMillis()}-${testName.methodName}"
        shell("mkdir -p $DIAG_DIR")
        shell("screencap -p $DIAG_DIR/$name.png")
        // executeShellCommand has no shell, so sh -c needs one word: IFS stands in for the spaces
        fun save(command: String, file: String) =
            shell("sh -c ${command.replace(" ", "\${IFS}")}>$DIAG_DIR/$name-$file.txt")
        save("dumpsys window", "window")
        save("dumpsys activity activities", "activities")
        save("dumpsys SurfaceFlinger", "surfaceflinger")
        save("logcat -d -v threadtime", "logcat")
        val hierarchy = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString("UTF-8")
        val resumed = shell("dumpsys activity activities").lines()
            .filter { "ResumedActivity" in it || "mFocusedApp" in it || "topResumedActivity" in it }
        val focus = shell("dumpsys window windows").lines().filter { "mCurrentFocus" in it || "mFocusedApp" in it }
        val logs = shell("logcat -d -v threadtime -t 2500").lines().filter { line ->
            LOG_MARKERS.any { it in line }
        }.takeLast(120)
        val nav = shell("settings get secure navigation_mode").trim()
        return listOf(
            "screenshot: $DIAG_DIR/$name.png  package=${device.currentPackageName} nav_mode=$nav",
            "display: ${device.displayWidth}x${device.displayHeight} rotation=${device.displayRotation}",
            "resumed: $resumed", "focus: $focus",
            "logs:", logs.joinToString("\n"),
            "hierarchy:", hierarchy,
        ).joinToString("\n")
    }


    private companion object {
        const val DIAG_DIR = "/data/local/tmp/parem-diag"
        val LOG_MARKERS = listOf("ParemGesture", "ActivityTaskManager", "InputDispatcher", "Choreographer", "AndroidRuntime", "Davey")
        const val TAG = "SmokeTest"
        const val TIMEOUT = 10_000L
        const val SETTINGS_PKG = "com.android.settings"
    }
}
