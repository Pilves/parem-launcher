package com.parem.launcher

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SettingsRowsLayoutTest {
    @Test
    fun defaultHomeRowsStartHiddenBeforeRoleIsObserved() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            val context = ContextThemeWrapper(app, R.style.AppTheme)
            val root = LayoutInflater.from(context).inflate(R.layout.fragment_settings, null)
            assertEquals(View.GONE, root.findViewById<View>(R.id.setLauncherTop).visibility)
            assertEquals(View.GONE, root.findViewById<View>(R.id.setLauncher).visibility)
        }
    }

    @Test
    fun titleAndLongValueFitAtLargeFont() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            val config = Configuration(app.resources.configuration).apply {
                fontScale = 2f
                setLocale(Locale.ENGLISH)
            }
            val context = ContextThemeWrapper(app.createConfigurationContext(config), R.style.AppTheme)
            val root = LayoutInflater.from(context).inflate(R.layout.fragment_settings, null)
            val rows = listOf(
                R.id.screenTimeLayout, R.id.screenTimeLimitsRow, R.id.focusModeRow, R.id.grayscaleRow,
                R.id.quietNotifRow,
                R.id.homeAppsNumRow, R.id.dateTimeRow, R.id.alignmentRow, R.id.homeLayoutLockRow,
                R.id.showIconsRow, R.id.widgetPlacementRow, R.id.weatherRow, R.id.autoShowKeyboardRow,
                R.id.sortByUsageLayout, R.id.contactSearchLayout, R.id.hiddenAppsRow, R.id.autoLaunchRow,
                R.id.swipeLeftRow, R.id.swipeRightRow, R.id.flSwipeDown, R.id.doubleTapRow,
                R.id.gestureLettersRow, R.id.appThemeRow, R.id.textSizeRow, R.id.boldFontRow,
                R.id.dailyWallpaperRow, R.id.dimWallpaperRow, R.id.statusBarRow, R.id.crashReportsRow,
            )
            val width = (450 * context.resources.displayMetrics.density).toInt()
            rows.forEach { id ->
                val row = root.findViewById<LinearLayout>(id)
                row.visibility = View.VISIBLE
                val title = row.getChildAt(0) as TextView
                val value = row.getChildAt(1) as TextView
                title.visibility = View.VISIBLE
                value.visibility = View.VISIBLE
                value.text = context.getString(R.string.lock_needs_access)
                row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                row.layout(0, 0, width, row.measuredHeight)
                val name = context.resources.getResourceEntryName(id)
                assertTrue("$name title or value has no width", title.width > 0 && value.width > 0)
                assertTrue("$name text views overlap", title.right <= value.left)
                assertEquals("$name title is truncated", title.text.length, title.layout.getLineEnd(title.lineCount - 1))
                assertEquals("$name value is truncated", value.text.length, value.layout.getLineEnd(value.lineCount - 1))
                listOf(title, value).forEach { text ->
                    repeat(text.lineCount) { line ->
                        assertEquals("$name text is ellipsized", 0, text.layout.getEllipsisCount(line))
                    }
                }
            }
            val history = root.findViewById<LinearLayout>(R.id.searchHistoryRow)
            val historyTitle = history.getChildAt(0) as TextView
            val controls = history.getChildAt(1) as LinearLayout
            val clear = root.findViewById<TextView>(R.id.searchHistoryClear)
            clear.visibility = View.VISIBLE
            history.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            history.layout(0, 0, width, history.measuredHeight)
            assertTrue("history title and controls overlap", historyTitle.right <= controls.left)
            assertTrue("history clear action has no width", clear.width > 0)
            assertEquals("history title is truncated", historyTitle.text.length,
                historyTitle.layout.getLineEnd(historyTitle.lineCount - 1))
            assertEquals("history clear action is truncated", clear.text.length,
                clear.layout.getLineEnd(clear.lineCount - 1))
        }
    }
}
