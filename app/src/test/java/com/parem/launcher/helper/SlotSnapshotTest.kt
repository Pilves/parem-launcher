package com.parem.launcher.helper

import com.parem.launcher.data.FolderApp
import com.parem.launcher.data.FolderGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlotSnapshotTest {
    private val app = SlotSnapshot("Chat", "org.chat", "org.chat.Main", "UserHandle{0}")

    @Test fun undoPreservesAppWebsiteAndFolderIdentity() {
        val website = app.copy(name = "News", packageName = "", activity = "", url = "https://example.org/")
        val folder = SlotSnapshot(folder = FolderGroup("Read", listOf(
            FolderApp("News", "", userString = "UserHandle{0}", url = "https://example.org/"),
            FolderApp("Work", "org.chat", "org.chat.Main", "UserHandle{10}"),
        )))
        for (snapshot in listOf(app, website, folder)) {
            assertEquals(snapshot, snapshot.restoreAfterRemoval(SlotSnapshot()))
        }
    }

    @Test fun undoRejectsAnyNewSlotData() {
        for (current in listOf(app, SlotSnapshot(name = "New label"),
            SlotSnapshot(folder = FolderGroup("New folder", emptyList())), SlotSnapshot(url = "https://new.example/"))) {
            assertNull(app.restoreAfterRemoval(current))
        }
    }
}
