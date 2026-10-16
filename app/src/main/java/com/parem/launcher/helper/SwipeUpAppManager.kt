package com.parem.launcher.helper

import android.content.Context
import androidx.core.content.edit
import com.parem.launcher.data.paremPrefs

object SwipeUpAppManager {


    private fun nameKey(slot: Int) = "SWIPE_UP_NAME_$slot"
    private fun packageKey(slot: Int) = "SWIPE_UP_PACKAGE_$slot"
    private fun activityKey(slot: Int) = "SWIPE_UP_ACTIVITY_$slot"
    private fun userKey(slot: Int) = "SWIPE_UP_USER_$slot"

    fun hasSwipeUpApp(context: Context, slot: Int): Boolean =
        getSwipeUpAppPackage(context, slot).isNotEmpty()

    fun getSwipeUpAppName(context: Context, slot: Int): String =
        context.paremPrefs().getString(nameKey(slot), "").orEmpty()

    fun getSwipeUpAppPackage(context: Context, slot: Int): String =
        context.paremPrefs().getString(packageKey(slot), "").orEmpty()

    fun getSwipeUpAppActivity(context: Context, slot: Int): String =
        context.paremPrefs().getString(activityKey(slot), "").orEmpty()

    fun getSwipeUpAppUser(context: Context, slot: Int): String =
        context.paremPrefs().getString(userKey(slot), "").orEmpty()

    fun setSwipeUpApp(
        context: Context,
        slot: Int,
        name: String,
        packageName: String,
        activityClassName: String,
        userString: String
    ) {
        context.paremPrefs().edit {
            putString(nameKey(slot), name)
            putString(packageKey(slot), packageName)
            putString(activityKey(slot), activityClassName)
            putString(userKey(slot), userString)
        }
    }
}
