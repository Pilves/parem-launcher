package com.parem.launcher.ui.settings

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.R
import com.parem.launcher.helper.CityResult
import com.parem.launcher.helper.WeatherManager
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bottom sheet for enabling weather and picking a city (debounced Open-Meteo
 * geocoding search). Extracted from SettingsFragment.showWeatherSettingsDialog;
 * behavior unchanged.
 *
 * @param context The activity context.
 * @param lifecycleOwner The fragment's viewLifecycleOwner, used to scope the
 * debounced search coroutine so it dies with the fragment's view.
 * @param onCityChosen Called after a city is selected and weather enabled, so
 * the caller can refresh its "Wellbeing" row texts.
 */
class WeatherSettingsDialog(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onCityChosen: () -> Unit,
) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = layoutInflater.inflate(R.layout.dialog_weather_settings, null)
        val searchInput = view.findViewById<EditText>(R.id.citySearchInput)
        val statusText = view.findViewById<TextView>(R.id.searchStatus)
        val resultsList = view.findViewById<ListView>(R.id.cityResultsList)
        val currentCityLabel = view.findViewById<TextView>(R.id.currentCityLabel)

        // Show current city if configured
        val currentCity = WeatherManager.getCityName(context)
        if (currentCity.isNotEmpty()) {
            currentCityLabel.text = context.getString(R.string.current_city, currentCity)
            currentCityLabel.visibility = View.VISIBLE
        }

        val cities = mutableListOf<CityResult>()
        val textColor = context.getColorFromAttr(R.attr.primaryColor)
        val subtextColor = context.getColorFromAttr(R.attr.primaryColorTrans50)

        val adapter = object : BaseAdapter() {
            override fun getCount() = cities.size
            override fun getItem(position: Int) = cities[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val city = cities[position]
                val container = (convertView as? LinearLayout)
                    ?: LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(8.dpToPx(), 10.dpToPx(), 8.dpToPx(), 10.dpToPx())
                    }
                container.removeAllViews()

                val nameTv = TextView(context).apply {
                    text = city.name
                    textSize = 16f
                    setTextColor(textColor)
                }
                container.addView(nameTv)

                val detailParts = mutableListOf<String>()
                if (city.admin1.isNotEmpty()) detailParts.add(city.admin1)
                if (city.country.isNotEmpty()) detailParts.add(city.country)
                if (detailParts.isNotEmpty()) {
                    val detailTv = TextView(context).apply {
                        text = detailParts.joinToString(", ")
                        textSize = 13f
                        setTextColor(subtextColor)
                    }
                    container.addView(detailTv)
                }

                return container
            }
        }
        resultsList.adapter = adapter

        resultsList.setOnItemClickListener { _, _, position, _ ->
            val city = cities[position]
            WeatherManager.setLocation(context, city.latitude.toString(), city.longitude.toString())
            WeatherManager.setCityName(context, city.displayName)
            WeatherManager.setEnabled(context, true)
            onCityChosen()
            dismiss()
        }

        // Debounced search
        var searchJob: Job? = null
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                val query = s?.toString() ?: ""
                if (query.length < 2) {
                    cities.clear()
                    adapter.notifyDataSetChanged()
                    statusText.visibility = View.GONE
                    return
                }
                statusText.text = context.getString(R.string.searching)
                statusText.visibility = View.VISIBLE
                searchJob = lifecycleOwner.lifecycleScope.launch {
                    delay(300)
                    val results = WeatherManager.searchCities(query)
                    // The dialog can outlive the fragment's view; guard against
                    // touching a dismissed dialog's views.
                    if (!isShowing) return@launch
                    cities.clear()
                    cities.addAll(results)
                    adapter.notifyDataSetChanged()
                    if (results.isEmpty()) {
                        statusText.text = context.getString(R.string.no_results)
                        statusText.visibility = View.VISIBLE
                    } else {
                        statusText.visibility = View.GONE
                    }
                }
            }
        })

        setContentView(view)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
