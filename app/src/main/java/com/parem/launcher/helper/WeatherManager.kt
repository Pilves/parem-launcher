package com.parem.launcher.helper

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.parem.launcher.data.paremPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CityResult(
    val name: String,
    val country: String,
    val admin1: String,
    val latitude: Double,
    val longitude: Double
) {
    val displayName: String
        get() = buildString {
            append(name)
            if (admin1.isNotEmpty()) append(", $admin1")
            if (country.isNotEmpty()) append(", $country")
        }
}

object WeatherManager {

    private const val WEATHER_ENABLED = "WEATHER_ENABLED"
    private const val WEATHER_LAT = "WEATHER_LAT"
    private const val WEATHER_LNG = "WEATHER_LNG"
    private const val WEATHER_CACHED_TEMP = "WEATHER_CACHED_TEMP"
    private const val WEATHER_LAST_FETCHED = "WEATHER_LAST_FETCHED"
    // Success-only timestamp: unlike WEATHER_LAST_FETCHED (which a failed
    // fetch rewrites for retry-backoff), this only moves forward when a
    // fetch actually succeeded. It's what cache age/staleness is computed from.
    private const val WEATHER_LAST_SUCCESS = "WEATHER_LAST_SUCCESS_MS"
    private const val WEATHER_CITY_NAME = "WEATHER_CITY_NAME"

    private const val FETCH_INTERVAL_MS = 60 * 60 * 1000L // 1 hour

    fun isEnabled(context: Context): Boolean =
        context.paremPrefs().getBoolean(WEATHER_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.paremPrefs().edit { putBoolean(WEATHER_ENABLED, enabled) }
    }

    fun setLocation(context: Context, lat: String, lng: String) {
        context.paremPrefs().edit {
            putString(WEATHER_LAT, lat)
            putString(WEATHER_LNG, lng)
        }
    }

    fun getLocation(context: Context): Pair<String, String> {
        val p = context.paremPrefs()
        return Pair(
            p.getString(WEATHER_LAT, "") ?: "",
            p.getString(WEATHER_LNG, "") ?: ""
        )
    }

    fun getCityName(context: Context): String =
        context.paremPrefs().getString(WEATHER_CITY_NAME, "") ?: ""

    fun setCityName(context: Context, name: String) {
        context.paremPrefs().edit { putString(WEATHER_CITY_NAME, name) }
    }

    fun getCachedTemp(context: Context): String =
        context.paremPrefs().getString(WEATHER_CACHED_TEMP, "") ?: ""


    /**
     * Staleness of the cached reading, per PAREM-106. A cache with no
     * WEATHER_LAST_SUCCESS yet (pre-upgrade installs) is treated as STALE
     * rather than FRESH or EXPIRED: it self-heals silently on the next
     * successful fetch (throttled to roughly hourly) instead of either
     * flashing an unverified reading as trustworthy or disappearing outright.
     */
    fun getStaleness(context: Context): WeatherStaleness.Level {
        val p = context.paremPrefs()
        if (!p.contains(WEATHER_LAST_SUCCESS)) return WeatherStaleness.classify(null)
        val lastSuccess = p.getLong(WEATHER_LAST_SUCCESS, 0L)
        val age = System.currentTimeMillis() - lastSuccess
        return WeatherStaleness.classify(age)
    }

    suspend fun searchCities(query: String): List<CityResult> = withContext(Dispatchers.IO) {
        if (query.isBlank() || query.length < 2) return@withContext emptyList()

        var connection: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=en&format=json")
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doInput = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext emptyList()
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            if (!json.has("results")) return@withContext emptyList()

            val results = json.getJSONArray("results")
            val cities = mutableListOf<CityResult>()
            for (i in 0 until results.length()) {
                val obj = results.getJSONObject(i)
                cities.add(
                    CityResult(
                        name = obj.optString("name", ""),
                        country = obj.optString("country", ""),
                        admin1 = obj.optString("admin1", ""),
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0)
                    )
                )
            }
            cities
        } catch (e: Exception) {
            Log.e("WeatherManager", "Failed to search cities", e)
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun fetchWeather(context: Context): String? = withContext(Dispatchers.IO) {
        val p = context.paremPrefs()
        val lastFetched = p.getLong(WEATHER_LAST_FETCHED, 0L)
        val now = System.currentTimeMillis()

        if (now - lastFetched < FETCH_INTERVAL_MS) {
            val cached = getCachedTemp(context)
            return@withContext cached.ifEmpty { null }
        }

        val (lat, lng) = getLocation(context)
        if (lat.isBlank() || lng.isBlank()) return@withContext null

        val latD = lat.toDoubleOrNull() ?: return@withContext null
        val lngD = lng.toDoubleOrNull() ?: return@withContext null

        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$latD&longitude=$lngD&current_weather=true")
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doInput = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val currentWeather = json.getJSONObject("current_weather")
            val temperature = currentWeather.getDouble("temperature")
            val formatted = "${temperature.toInt()}\u00B0"

            p.edit {
                putString(WEATHER_CACHED_TEMP, formatted)
                putLong(WEATHER_LAST_FETCHED, now)
                putLong(WEATHER_LAST_SUCCESS, now)
            }

            formatted
        } catch (e: Exception) {
            Log.e("WeatherManager", "Failed to fetch weather", e)
            // Backoff: don't retry for 10 minutes on failure
            p.edit { putLong(WEATHER_LAST_FETCHED, now - FETCH_INTERVAL_MS + 10 * 60 * 1000L) }
            val cached = getCachedTemp(context)
            cached.ifEmpty { null }
        } finally {
            connection?.disconnect()
        }
    }
}
