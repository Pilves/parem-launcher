package com.parem.launcher.helper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetch + cache for CurrencyConverter's ECB rates. Fetched lazily by the drawer
 * when a currency query is typed, at most once per 24h, never per keystroke.
 * The request is the same fixed URL for everyone, so it carries nothing about
 * what the user converts.
 */
object CurrencyRates {

    private const val PREFS_NAME = "com.parem.launcher"
    private const val CURRENCY_RATES = "CURRENCY_RATES"
    private const val CURRENCY_LAST_SUCCESS_MS = "CURRENCY_LAST_SUCCESS_MS"
    // Written on every attempt, so a failed fetch backs off like WeatherManager's.
    private const val CURRENCY_LAST_ATTEMPT_MS = "CURRENCY_LAST_ATTEMPT_MS"

    private const val ECB_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
    private const val FETCH_INTERVAL_MS = 24 * 60 * 60 * 1000L
    private const val RETRY_BACKOFF_MS = 10 * 60 * 1000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, 0)

    fun cached(context: Context): CurrencyConverter.Rates? =
        CurrencyConverter.Rates.deserialize(prefs(context).getString(CURRENCY_RATES, null))

    /** Downloads fresh rates if the cache is 24h old and no attempt failed in the last 10 min. */
    suspend fun fetchIfDue(context: Context): CurrencyConverter.Rates? = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        // `in 0 until` also treats a clock set backwards as due instead of stuck.
        val sinceSuccess = now - p.getLong(CURRENCY_LAST_SUCCESS_MS, 0L)
        val sinceAttempt = now - p.getLong(CURRENCY_LAST_ATTEMPT_MS, 0L)
        if (sinceSuccess in 0 until FETCH_INTERVAL_MS || sinceAttempt in 0 until RETRY_BACKOFF_MS)
            return@withContext cached(context)

        p.edit().putLong(CURRENCY_LAST_ATTEMPT_MS, now).apply()
        var connection: HttpURLConnection? = null
        try {
            connection = URL(ECB_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext cached(context)
            val xml = connection.inputStream.bufferedReader().use { it.readText() }
            // A format change parses to null and keeps the old cache.
            val rates = CurrencyConverter.parseEcbXml(xml) ?: return@withContext cached(context)
            p.edit()
                .putString(CURRENCY_RATES, rates.serialize())
                .putLong(CURRENCY_LAST_SUCCESS_MS, now)
                .apply()
            rates
        } catch (e: Exception) {
            Log.e("CurrencyRates", "Failed to fetch ECB rates", e)
            cached(context)
        } finally {
            connection?.disconnect()
        }
    }
}
