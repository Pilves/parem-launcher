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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PickerSheetLayoutTest {
    @Test
    fun headingsSearchChoicesAndActionsShareOneScrollerInShortWindow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val sheet = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val title = View(context).apply { layoutParams = LinearLayout.LayoutParams(200, 70) }
            val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val search = EditText(context)
            val choices = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = 3
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
            val action = View(context).apply { layoutParams = LinearLayout.LayoutParams(200, 70) }
            content.addView(search)
            content.addView(list)
            sheet.addView(title)
            sheet.addView(content)
            sheet.addView(action)

            sheet.makePickerScroller()
            assertEquals(1, sheet.childCount)
            assertSame(list, sheet.getChildAt(0))
            assertEquals(5, list.adapter!!.itemCount) // header, three choices, footer
            sheet.measure(
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
            )
            sheet.layout(0, 0, 200, 160)
            assertEquals(160, list.measuredHeight)
            list.scrollToPosition(4)
            list.measure(
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
            )
            list.layout(0, 0, 200, 160)
            val footer = list.findViewHolderForAdapterPosition(4)
            assertNotNull("footer is reachable in a short window", footer)
            assertTrue(footer!!.itemView.bottom <= list.height)
        }
    }
}
