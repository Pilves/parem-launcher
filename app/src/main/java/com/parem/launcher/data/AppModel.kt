package com.parem.launcher.data

import android.os.UserHandle
import java.text.CollationKey

data class AppModel(
    val appLabel: String,
    val key: CollationKey?,
    val appPackage: String,
    val activityClassName: String?,
    val isNew: Boolean = false,
    val user: UserHandle,
    // Non-null for a website shortcut; appPackage is then ""
    val url: String? = null,
    // App in the Private Space profile (API 35+), or the drawer's private-space header row
    val isPrivate: Boolean = false,
    // Non-null for an app-shortcut drawer row; the other fields are its parent app's
    val shortcutId: String? = null,
    val shortcutLabel: String? = null,
) : Comparable<AppModel> {
    override fun compareTo(other: AppModel): Int = when {
        key != null && other.key != null -> key.compareTo(other.key)
        else -> appLabel.compareTo(other.appLabel, true)
    }
}