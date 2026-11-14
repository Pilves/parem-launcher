package com.parem.launcher

import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.ui.WidgetPickerOverflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPickerOverflowTest {
    @Test fun shortWindowMovesChromeIntoTheSingleScrollableListAndRestoresIt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val handle = View(context).apply { layoutParams = LinearLayout.LayoutParams(100, 10) }
            val title = View(context).apply { layoutParams = LinearLayout.LayoutParams(100, 45) }
            val search = View(context).apply { layoutParams = LinearLayout.LayoutParams(100, 60) }
            val list = ListView(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
            val header = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            list.addHeaderView(header, null, false)
            list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, (0..19).map { "Widget $it" })
            container.addView(handle)
            container.addView(title)
            container.addView(search)
            container.addView(list)
            val overflow = WidgetPickerOverflow(container, title, search, list, header, 30)
            fun layout(height: Int) {
                container.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                container.layout(0, 0, 240, height)
            }

            layout(200)
            overflow.update()
            assertSame("normal title should remain pinned", container, title.parent)
            assertSame("normal search should remain pinned", container, search.parent)
            assertSame("widget ListView remains the single scrolling list", container, list.parent)
            assertTrue("normal list needs room", list.height >= 30)

            // Fixed handle/title/search alone are 115px, greater than the viewport.
            layout(100)
            overflow.update()
            layout(100)
            assertSame("short-window title must be in the list header", header, title.parent)
            assertSame("short-window search must be in the list header", header, search.parent)
            assertSame("widget ListView must not be wrapped by another scroller", container, list.parent)
            assertEquals(1, list.headerViewsCount)
            assertTrue("list must remain usable", list.height > 0 && list.canScrollVertically(1))
            list.setSelection(0)
            assertTrue("title and search can be reached from the top", title.parent === header && search.parent === header)
            list.setSelection(list.count - 1)
            assertEquals("last item remains addressable", 21, list.count)

            layout(200)
            overflow.update()
            layout(200)
            assertSame("title returns to its pinned position", container, title.parent)
            assertSame("search returns to its pinned position", container, search.parent)
            assertSame(list, container.getChildAt(3))
        }
    }
}
