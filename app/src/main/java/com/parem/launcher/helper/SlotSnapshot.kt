package com.parem.launcher.helper

import com.parem.launcher.data.FolderGroup

/** A value snapshot of one slot, for removal and Undo. */
data class SlotSnapshot(
    val name: String = "",
    val packageName: String = "",
    val activity: String = "",
    val user: String = "",
    val url: String = "",
    val folder: FolderGroup? = null,
) {
    /** Never overwrite an edit made since the removal. The caller also invalidates superseded Undo actions. */
    fun restoreAfterRemoval(current: SlotSnapshot): SlotSnapshot? =
        takeIf { current == SlotSnapshot() }
}
