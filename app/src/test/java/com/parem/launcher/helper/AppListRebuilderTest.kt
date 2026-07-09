package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator

class AppListRebuilderTest {

    private val collator = Collator.getInstance()

    private fun raw(
        label: String,
        pkg: String,
        user: String = MAIN_USER,
        firstInstall: Long = 0L,
    ) = AppListRebuilder.RawApp(
        label = label,
        key = collator.getCollationKey(label),
        packageName = pkg,
        activityClassName = "$pkg.MainActivity",
        firstInstallTime = firstInstall,
        user = user,
        userString = user,
    )

    private fun rebuild(
        rawApps: List<AppListRebuilder.RawApp<String>>,
        hiddenApps: Set<String> = emptySet(),
        includeRegularApps: Boolean = true,
        includeHiddenApps: Boolean = false,
        renameLabel: (String) -> String = { "" },
        now: Long = NOW,
    ) = AppListRebuilder.rebuild(
        rawApps, OWN_PACKAGE, hiddenApps,
        includeRegularApps, includeHiddenApps, renameLabel, now, HOUR
    )

    @Test
    fun ownPackage_neverListed() {
        val result = rebuild(
            listOf(raw("Parem Launcher", OWN_PACKAGE), raw("Signal", "org.signal")),
            includeRegularApps = true, includeHiddenApps = true
        )
        assertEquals(listOf("org.signal"), result.map { it.app.packageName })
    }

    @Test
    fun hiddenApp_excludedFromRegularList() {
        val result = rebuild(
            listOf(raw("Signal", "org.signal"), raw("TikTok", "com.tiktok")),
            hiddenApps = setOf("com.tiktok|$MAIN_USER")
        )
        assertEquals(listOf("org.signal"), result.map { it.app.packageName })
    }

    @Test
    fun hiddenList_containsOnlyHiddenApps() {
        val result = rebuild(
            listOf(raw("Signal", "org.signal"), raw("TikTok", "com.tiktok")),
            hiddenApps = setOf("com.tiktok|$MAIN_USER"),
            includeRegularApps = false, includeHiddenApps = true
        )
        assertEquals(listOf("com.tiktok"), result.map { it.app.packageName })
    }

    @Test
    fun bothFlags_returnRegularAndHidden() {
        val result = rebuild(
            listOf(raw("Signal", "org.signal"), raw("TikTok", "com.tiktok")),
            hiddenApps = setOf("com.tiktok|$MAIN_USER"),
            includeRegularApps = true, includeHiddenApps = true
        )
        assertEquals(2, result.size)
    }

    @Test
    fun hiddenKey_isPerProfile() {
        // Same package hidden only in the work profile stays visible in the main one
        val result = rebuild(
            listOf(raw("Chrome", "com.chrome"), raw("Chrome", "com.chrome", user = WORK_USER)),
            hiddenApps = setOf("com.chrome|$WORK_USER")
        )
        assertEquals(listOf(MAIN_USER), result.map { it.app.userString })
    }

    @Test
    fun rename_appliedPerCall_blankFallsBackToOriginal() {
        val apps = listOf(raw("Signal", "org.signal"), raw("Firefox", "org.firefox"))
        val renamed = rebuild(apps, renameLabel = { if (it == "org.signal") "Chat" else "" })
        assertEquals(setOf("Chat", "Firefox"), renamed.map { it.shownLabel }.toSet())
    }

    @Test
    fun sort_usesOriginalLabelKey_evenWhenRenamed() {
        // "Signal" renamed to "Aaa Chat" must NOT jump ahead of "Firefox":
        // the live path always sorted by the original label's collation key
        val result = rebuild(
            listOf(raw("Signal", "org.signal"), raw("Firefox", "org.firefox")),
            renameLabel = { if (it == "org.signal") "Aaa Chat" else "" }
        )
        assertEquals(listOf("Firefox", "Aaa Chat"), result.map { it.shownLabel })
    }

    @Test
    fun isNew_trueOnlyWithinWindowOfInstall() {
        val result = rebuild(
            listOf(
                raw("Fresh", "com.fresh", firstInstall = NOW - HOUR / 2),
                raw("Old", "com.old", firstInstall = NOW - HOUR * 2),
            )
        )
        assertTrue(result.first { it.app.packageName == "com.fresh" }.isNew)
        assertFalse(result.first { it.app.packageName == "com.old" }.isNew)
    }

    companion object {
        private const val OWN_PACKAGE = "com.parem.launcher"
        private const val MAIN_USER = "UserHandle{0}"
        private const val WORK_USER = "UserHandle{10}"
        private const val HOUR = 3_600_000L
        private const val NOW = HOUR * 100
    }
}
