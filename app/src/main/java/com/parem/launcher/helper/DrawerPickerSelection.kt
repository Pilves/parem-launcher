package com.parem.launcher.helper

/** Resolve a saved picker choice only when package, activity and profile identify one row. */
object DrawerPickerSelection {
    data class Identity(val packageName: String, val activity: String?, val user: String?)

    fun uniqueIndex(saved: Identity?, candidates: List<Identity>, personalUser: String): Int? {
        if (saved == null || saved.packageName.isBlank()) return null
        // Legacy blank users launch in the personal profile, not whichever profile remains.
        val user = saved.user?.takeIf { it.isNotBlank() } ?: personalUser
        val matches = candidates.indices.filter { index ->
            val candidate = candidates[index]
            candidate.packageName == saved.packageName &&
                (saved.activity.isNullOrBlank() || candidate.activity == saved.activity) &&
                candidate.user == user
        }
        return matches.singleOrNull()
    }
}
