package com.parem.launcher

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.ui.makePickerScroller
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
            assertEquals(3, sheet.childCount)
            assertSame(list, sheet.getChildAt(0))
            assertSame(action, sheet.getChildAt(1))
            assertSame(cancel, sheet.getChildAt(2))
            assertEquals(21, list.adapter!!.itemCount) // header and 20 choices; actions are pinned
            sheet.measure(
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
            )
            sheet.layout(0, 0, 200, 240)
            assertTrue("the list must contain more choices than fit", list.canScrollVertically(1))
            assertTrue("the list must have room for choices", list.height > 75)
            assertEquals("the action must be visible before scrolling", list.bottom, action.top)
            assertEquals(240, cancel.bottom)
            assertEquals("the action must span the picker width", list.width, action.width)
            assertEquals("search must span the picker width", list.width, search.width)

            // A wrap-content dialog supplies an AT_MOST height, not always EXACTLY.
            sheet.measure(
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.AT_MOST),
            )
            sheet.layout(0, 0, 200, sheet.measuredHeight)
            assertTrue("list must retain space under a dialog height limit", list.height > 75)
            assertTrue("last action must remain inside the dialog", cancel.bottom <= sheet.height)
            assertTrue("long list must remain scrollable", list.canScrollVertically(1))
        }
    }
}
