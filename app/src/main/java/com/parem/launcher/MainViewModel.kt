package com.parem.launcher

import android.app.Application
import android.util.Log
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.AppOpenCounter
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.GestureLetterManager
import com.parem.launcher.helper.SingleLiveEvent
import com.parem.launcher.helper.ThemeScheduleManager
import com.parem.launcher.helper.UsageStatsHelper
import com.parem.launcher.helper.WallpaperWorker
import com.parem.launcher.helper.WeatherManager
import com.parem.launcher.helper.formattedTimeSpent
import com.parem.launcher.helper.getAppsList
import com.parem.launcher.helper.hasBeenMinutes
import com.parem.launcher.helper.isParemDefault
import com.parem.launcher.helper.isPackageInstalled
import com.parem.launcher.helper.PackageChangeTracker
import com.parem.launcher.helper.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit


class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs(appContext)
    private val launcherApps by lazy {
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    }

    val firstOpen = MutableLiveData<Boolean>()
    val refreshHome = MutableLiveData<Boolean>()
    val toggleDateTime = MutableLiveData<Unit>()
    val updateSwipeApps = MutableLiveData<Any>()
    val appList = MutableLiveData<List<AppModel>?>()
    val hiddenApps = MutableLiveData<List<AppModel>?>()
    val isParemDefault = MutableLiveData<Boolean>()
    val launcherResetFailed = MutableLiveData<Boolean>()
    val homeAppAlignment = MutableLiveData<Int>()
    val screenTimeValue = MutableLiveData<String>()
    val perAppScreenTime = MutableLiveData<Map<String, Long>>()
    val weatherValue = MutableLiveData<String>()

    var pendingGestureLetter: Char? = null
    private var lastWeatherFetch: Long = 0L

    val showDialog = SingleLiveEvent<String>()
    val resetLauncherLiveData = SingleLiveEvent<Unit?>()

    init {
        // The raw app-list and icon caches are trusted only while this
        // tracker's stamp is unchanged; the activity-scoped ViewModel is the
        // longest-lived owner available, so it holds the registration.
        PackageChangeTracker.register(appContext)
    }

    /**
     * How many home-app slots actually fit the current home layout, measured
     * by HomeFragment's fitting pass (widgets and the clock eat vertical
     * space). Stays 8 until first measured. The settings picker caps the
     * number-of-apps selection with it so a chosen count is never silently
     * hidden behind a widget.
     */
    @Volatile var homeAppsCapacity: Int = 8

    override fun onCleared() {
        PackageChangeTracker.unregister()
        super.onCleared()
    }

    fun selectedApp(appModel: AppModel, flag: Int) {
        when (flag) {
            Constants.FLAG_LAUNCH_APP, Constants.FLAG_HIDDEN_APPS -> {
                if (!FocusModeManager.isAppAllowed(appContext, appModel.appPackage)) {
                    appContext.showToast(appContext.getString(R.string.app_blocked_focus))
                    return
                }
                launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)
            }

            in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8 -> {
                prefs.setHomeAppName(flag, appModel.appLabel)
                prefs.setHomeAppPackage(flag, appModel.appPackage)
                prefs.setHomeAppUser(flag, appModel.user.toString())
                prefs.setHomeAppActivityClassName(flag, appModel.activityClassName)
                refreshHome(false)
            }

            Constants.FLAG_SET_SWIPE_LEFT_APP -> {
                prefs.appNameSwipeLeft = appModel.appLabel
                prefs.appPackageSwipeLeft = appModel.appPackage
                prefs.appUserSwipeLeft = appModel.user.toString()
                prefs.appActivityClassNameSwipeLeft = appModel.activityClassName
                updateSwipeApps()
            }

            Constants.FLAG_SET_SWIPE_RIGHT_APP -> {
                prefs.appNameSwipeRight = appModel.appLabel
                prefs.appPackageSwipeRight = appModel.appPackage
                prefs.appUserSwipeRight = appModel.user.toString()
                prefs.appActivityClassNameSwipeRight = appModel.activityClassName
                updateSwipeApps()
            }

            Constants.FLAG_SET_CLOCK_APP -> {
                prefs.clockAppPackage = appModel.appPackage
                prefs.clockAppUser = appModel.user.toString()
                prefs.clockAppClassName = appModel.activityClassName
            }

            Constants.FLAG_SET_CALENDAR_APP -> {
                prefs.calendarAppPackage = appModel.appPackage
                prefs.calendarAppUser = appModel.user.toString()
                prefs.calendarAppClassName = appModel.activityClassName
            }

            in Constants.FLAG_SET_SWIPE_UP_APP_1..Constants.FLAG_SET_SWIPE_UP_APP_8 -> {
                val slot = flag - Constants.FLAG_SET_SWIPE_UP_APP_1 + 1
                com.parem.launcher.helper.SwipeUpAppManager.setSwipeUpApp(
                    appContext, slot,
                    appModel.appLabel, appModel.appPackage,
                    appModel.activityClassName ?: "", appModel.user.toString()
                )
                refreshHome(false)
            }

            Constants.FLAG_SET_DOUBLE_TAP_APP -> {
                com.parem.launcher.helper.DoubleTapActionManager.setApp(
                    appContext,
                    appModel.appPackage,
                    appModel.activityClassName ?: "",
                    appModel.user.toString()
                )
            }

            Constants.FLAG_SET_GESTURE_LETTER_APP -> {
                pendingGestureLetter?.let { letter ->
                    GestureLetterManager.setMapping(
                        appContext, letter,
                        appModel.appPackage,
                        appModel.activityClassName ?: "",
                        appModel.user.toString()
                    )
                    pendingGestureLetter = null
                }
            }
        }
    }

    fun firstOpen(value: Boolean) {
        firstOpen.postValue(value)
    }

    fun refreshHome(appCountUpdated: Boolean) {
        refreshHome.postValue(appCountUpdated)
    }

    fun toggleDateTime() {
        toggleDateTime.postValue(Unit)
    }

    private fun updateSwipeApps() {
        updateSwipeApps.postValue(Unit)
    }

    private fun launchApp(packageName: String, activityClassName: String?, userHandle: UserHandle) {
        // Fast path: the activity is known for every home slot and drawer row —
        // launch synchronously instead of paying two dispatcher hops per tap
        if (!activityClassName.isNullOrBlank()) {
            if (startApp(ComponentName(packageName, activityClassName), userHandle, packageName)) return
            // Stale component (app updated and renamed its activity) — fall through to resolve
        }
        viewModelScope.launch {
            val component = withContext(Dispatchers.IO) {
                try {
                    val activityInfo = launcherApps.getActivityList(packageName, userHandle)
                    when (activityInfo.size) {
                        0 -> null
                        1 -> ComponentName(packageName, activityInfo[0].name)
                        else -> ComponentName(packageName, activityInfo[activityInfo.size - 1].name)
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to resolve activity for $packageName", e)
                    null
                }
            }
            if (component == null) {
                appContext.showToast(appContext.getString(R.string.app_not_found))
                return@launch
            }
            if (!startApp(component, userHandle, packageName)) {
                appContext.showToast(appContext.getString(R.string.unable_to_open_app))
            }
        }
    }

    /** Returns true if the app was started. */
    private fun startApp(component: ComponentName, userHandle: UserHandle, packageName: String): Boolean {
        return try {
            launcherApps.startMainActivity(component, userHandle, null, null)
            AppOpenCounter.increment(appContext, packageName)
            true
        } catch (e: SecurityException) {
            try {
                launcherApps.startMainActivity(component, android.os.Process.myUserHandle(), null, null)
                AppOpenCounter.increment(appContext, packageName)
                true
            } catch (e: Exception) {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getAppList(includeHiddenApps: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            appList.postValue(getAppsList(appContext, prefs, includeRegularApps = true, includeHiddenApps))
        }
    }

    fun getHiddenApps() {
        viewModelScope.launch(Dispatchers.IO) {
            hiddenApps.postValue(getAppsList(appContext, prefs, includeRegularApps = false, includeHiddenApps = true))
        }
    }

    fun isParemDefault() {
        viewModelScope.launch(Dispatchers.IO) {
            // resolveActivity is a binder IPC; it ran on the main thread on
            // every home resume before PAREM-117
            isParemDefault.postValue(isParemDefault(appContext))
        }
    }

    fun setWallpaperWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val uploadWorkRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(8, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                Constants.WALLPAPER_WORKER_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                uploadWorkRequest
            )
    }

    fun cancelWallpaperWorker() {
        WorkManager.getInstance(appContext).cancelUniqueWork(Constants.WALLPAPER_WORKER_NAME)
        prefs.dailyWallpaperUrl = ""
        prefs.dailyWallpaper = false
    }

    fun getWeather() {
        if (!WeatherManager.isEnabled(appContext)) return
        val now = System.currentTimeMillis()
        if (now - lastWeatherFetch < 30 * 60 * 1000L) return  // 30 min throttle
        lastWeatherFetch = now
        viewModelScope.launch(Dispatchers.IO) {
            val temp = WeatherManager.fetchWeather(appContext)
            if (temp != null) {
                weatherValue.postValue(temp)
            } else {
                val cached = WeatherManager.getDisplayString(appContext)
                if (cached.isNotEmpty()) weatherValue.postValue(cached)
            }
        }
    }

    fun startThemeScheduleWorker() {
        ThemeScheduleManager.startWorker(appContext)
    }

    fun cancelThemeScheduleWorker() {
        ThemeScheduleManager.cancelWorker(appContext)
    }

    fun updateHomeAlignment(gravity: Int) {
        prefs.homeAlignment = gravity
        homeAppAlignment.value = gravity
    }

    fun getTodaysScreenTime() {
        if (prefs.screenTimeLastUpdated.hasBeenMinutes(1).not()) return

        viewModelScope.launch(Dispatchers.IO) {
            val endTime = System.currentTimeMillis()
            // Total = sum of the shared per-app map, so the home screen, drawer
            // and limit checks all reuse one cached event-log scan (PAREM-115).
            val timeSpent = UsageStatsHelper.getPerAppUsageToday(appContext).values.sum()
            val viewTimeSpent = appContext.formattedTimeSpent(timeSpent)
            screenTimeValue.postValue(viewTimeSpent)
            prefs.screenTimeLastUpdated = endTime
        }
    }

    fun getPerAppScreenTime() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return

        viewModelScope.launch(Dispatchers.IO) {
            // The helper's 60s TTL throttles this path (it used to re-scan on
            // every drawer open).
            perAppScreenTime.postValue(UsageStatsHelper.getPerAppUsageToday(appContext))
        }
    }

    fun setDefaultClockApp() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Constants.CLOCK_APP_PACKAGES.firstOrNull { appContext.isPackageInstalled(it) }?.let { packageName ->
                    appContext.packageManager.getLaunchIntentForPackage(packageName)?.component?.className?.let {
                        prefs.clockAppPackage = packageName
                        prefs.clockAppClassName = it
                        prefs.clockAppUser = android.os.Process.myUserHandle().toString()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to set default clock app", e)
            }
        }
    }
}