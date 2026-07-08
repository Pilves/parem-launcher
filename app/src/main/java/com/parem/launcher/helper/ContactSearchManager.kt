package com.parem.launcher.helper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Opt-in omnibox contact search (PAREM-104). Owns its own preference key in the
 * shared prefs file, mirroring the other feature managers (WeatherManager etc.).
 *
 * Privacy contract: [loadContacts] is the ONLY place that touches the contacts
 * provider, and it no-ops unless the feature is both enabled AND the runtime
 * permission is granted. READ_CONTACTS is requested only when the user flips the
 * settings toggle on — never here, never at launch/onboarding.
 */
object ContactSearchManager {

    private const val PREFS_NAME = "com.parem.launcher"
    private const val CONTACT_SEARCH_ENABLED = "CONTACT_SEARCH_ENABLED"

    // Bounds memory/scan cost on devices with huge address books.
    private const val MAX_CONTACTS = 2000

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, 0)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(CONTACT_SEARCH_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(CONTACT_SEARCH_ENABLED, enabled).apply()
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * True only when the feature is on and usable right now. The drawer keys its
     * contact loading off this, so a revoked permission degrades to plain app
     * search with no query and no crash.
     */
    fun isActive(context: Context): Boolean =
        isEnabled(context) && hasPermission(context)

    /**
     * Loads name+number pairs (one per contact, first number wins) for in-memory
     * matching. Returns empty — and issues no provider query — unless [isActive].
     */
    fun loadContacts(context: Context): List<ContactMatcher.Contact> {
        if (!isActive(context)) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<ContactMatcher.Contact>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC",
            )?.use { cursor ->
                val lookupIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && out.size < MAX_CONTACTS) {
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    val number = cursor.getString(numberIdx)?.trim().orEmpty()
                    val lookup = cursor.getString(lookupIdx)
                    if (name.isEmpty() || number.isEmpty()) continue
                    // One entry per contact; the provider is sorted, so the first
                    // number we see for a lookup key is the one we keep.
                    val dedupeKey = lookup ?: "$name|$number"
                    if (!seen.add(dedupeKey)) continue
                    out.add(ContactMatcher.Contact(name, number, lookup))
                }
            }
        } catch (e: Exception) {
            // Permission race, provider disabled by an OEM, etc. — fail silent.
            Log.e("ContactSearchManager", "Failed to load contacts", e)
            return emptyList()
        }
        return out
    }
}
