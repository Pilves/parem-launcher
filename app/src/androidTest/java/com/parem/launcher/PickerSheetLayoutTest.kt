package com.parem.launcher

import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.ui.PickerSheetLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PickerSheetLayoutTest {
    @Test
    fun listShrinksWhenAvailableHeightShrinksAndFooterRemainsVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val sheet = PickerSheetLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                fitPicker = true
                setPadding(0, 10, 0, 10)
            }
            fun fixed(height: Int) = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(200, height)
            }
            sheet.addView(fixed(30)) // title
            val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            content.addView(fixed(30)) // search
            val list = RecyclerView(context).apply {
                layoutParams = LinearLayout.LayoutParams(200, 300)
            }
            content.addView(list)
            sheet.addView(content)
            val footer = fixed(50)
            sheet.addView(footer)

            fun measure(height: Int) {
                sheet.measure(
                    View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                sheet.layout(0, 0, 200, height)
            }
            measure(200)
            assertEquals(70, list.measuredHeight)
            assertTrue(footer.bottom <= 200 - sheet.paddingBottom)
            measure(160) // the window shrinks, as when the keyboard appears
            assertEquals(30, list.measuredHeight)
            assertTrue(footer.bottom <= 160 - sheet.paddingBottom)
        }
    }
}
