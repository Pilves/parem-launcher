package com.parem.launcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.helper.SettingsSearch
import com.parem.launcher.ui.settings.SettingsSearchIndex
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSearchAliasesTest {
    @Test
    fun renamedLabelsFindCanonicalRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rows = SettingsSearchIndex.rows(context)
        val oldAndNew = listOf(
            Triple(R.string.set_as_default_launcher, R.string.settings_default_home, R.id.setLauncher),
            Triple(R.string.change_default_launcher, R.string.settings_default_home, R.id.setLauncher),
            Triple(R.string.show_date_time, R.string.settings_clock_date, R.id.dateTime),
            Triple(R.string.notification_bar, R.string.settings_status_bar, R.id.statusBar),
            Triple(R.string.quiet_notif, R.string.settings_quiet_notifications, R.id.quietNotifToggle),
            Triple(R.string.wellbeing, R.string.settings_calm_section, R.id.screenTimeOnOff),
            Triple(R.string.usage_access, R.string.settings_screen_time_access, R.id.screenTimeOnOff),
        )
        for ((old, canonical, anchor) in oldAndNew) {
            val result = SettingsSearch.match(context.getString(old), rows)
            assertEquals(context.getString(old), anchor, result?.anchor)
            assertEquals(context.getString(old), context.getString(canonical), result?.title)
        }
        for (alias in listOf("wellbeing", "digital wellbeing")) {
            assertEquals(R.id.screenTimeOnOff, SettingsSearch.match(alias, rows)?.anchor)
        }
    }
}
