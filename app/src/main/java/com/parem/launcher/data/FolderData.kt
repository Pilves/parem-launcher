package com.parem.launcher.data

data class FolderApp(
    val appName: String,
    val packageName: String,
    val activityClassName: String = "",
    val userString: String,
    // Non-empty for a website entry; packageName is then ""
    val url: String = ""
)

data class FolderGroup(
    val name: String,
    val apps: List<FolderApp>
)
