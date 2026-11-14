package com.parem.launcher

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.ui.makePickerScroller
import com.parem.launcher.helper.dpToPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PickerSheetLayoutTest {
    @Test
    fun actionsStayVisibleWithoutScrollingInShortWindow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val sheet = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val title = View(context).apply { layoutParams = LinearLayout.LayoutParams(40, 35) }
            val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val search = EditText(context).apply { layoutParams = LinearLayout.LayoutParams(200, 40) }
            val choices = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = 20
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    object : RecyclerView.ViewHolder(View(context).apply {
                        layoutParams = RecyclerView.LayoutParams(200, 80)
                    }) {}
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
            }
            val list = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = choices
            }
            val action = View(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 35) }
            val cancel = View(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 35) }
            content.addView(search)
            content.addView(list)
            sheet.addView(title)
            sheet.addView(content)
            sheet.addView(action)
            sheet.addView(cancel)

            sheet.makePickerScroller()
            assertEquals(5, sheet.childCount)
            assertSame(title, sheet.getChildAt(0))
            assertSame(content, sheet.getChildAt(1))
            assertSame(list, sheet.getChildAt(2))
            assertSame(action, sheet.getChildAt(3))
            assertSame(cancel, sheet.getChildAt(4))
            assertEquals(20, list.adapter!!.itemCount) // search and actions stay outside the single list
            val wrapper = NestedScrollView(context).apply {
                isFillViewport = true
                addView(sheet)
            }
            fun measure(height: Int) {
                wrapper.measure(
                    View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                wrapper.layout(0, 0, 200, height)
            }
            val fixedHeight = 35 + 40 + 35 + 35
            val minimumList = 48.dpToPx()
            val normalHeight = fixedHeight + minimumList + 80
            measure(normalHeight)
            assertTrue("the list must contain more choices than fit", list.canScrollVertically(1))
            assertTrue("the list must have room for choices", list.height >= minimumList)
            assertEquals("the action must be visible before scrolling", list.bottom, action.top)
            assertEquals(normalHeight, cancel.bottom)
            assertEquals("the action must span the picker width", list.width, action.width)
            assertEquals("search must span the picker width", list.width, search.width)
            assertTrue("normal layout must not need outer scrolling", !wrapper.canScrollVertically(1))

            // Fixed rows alone exceed this viewport: scrolling must reveal both
            // ends, while the bounded app list retains its own scroll behavior.
            val overflowHeight = fixedHeight - 20
            measure(overflowHeight)
            assertTrue("overflow must be scrollable", wrapper.canScrollVertically(1))
            assertTrue("list must remain bounded and usable", list.height >= minimumList)
            assertTrue("long list must remain scrollable", list.canScrollVertically(1))
            wrapper.scrollTo(0, sheet.height - wrapper.height)
            assertTrue("last action must be reachable", cancel.bottom - wrapper.scrollY <= wrapper.height)
            wrapper.scrollTo(0, 0)
            assertTrue("title must be reachable", title.top >= wrapper.scrollY)

        }
    }
}
