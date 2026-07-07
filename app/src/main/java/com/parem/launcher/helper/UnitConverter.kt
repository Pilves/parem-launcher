package com.parem.launcher.helper

/**
 * Tiny unit-conversion parser behind the app drawer omnibox, mirroring
 * ExpressionEvaluator's shape: a cheap shape check (looksLikeConversion) gates
 * a full parse + unit lookup (convert) that returns null on any failure —
 * unknown units, mismatched categories, bad numbers — so unrecognized input
 * (app names, plain words) degrades to a normal search instead of a false
 * positive or a crash.
 *
 * Supports "5 km in mi", "100 f to c", "5km in mi", "5 km to miles", "100f c".
 * Categories: length, mass, temperature, volume, speed, data size.
 */
object UnitConverter {

    enum class Category { LENGTH, MASS, TEMPERATURE, VOLUME, SPEED, DATA_SIZE }

    data class Result(val value: Double, val symbol: String) {
        // ExpressionEvaluator.format's %.6f rounds anything below 1e-6 to a flat
        // "0", which would show e.g. "1 b to gb" as exactly 0 — use scientific
        // notation for tiny nonzero results instead.
        fun format(): String {
            val number =
                if (value != 0.0 && kotlin.math.abs(value) < 1e-6) String.format("%.3e", value)
                else ExpressionEvaluator.format(value)
            return "$number $symbol"
        }
    }

    // number, source-unit word, optional "to"/"in" connector, target-unit word.
    // Letters-only unit tokens keep this disjoint from the calculator (digits/
    // operators only) and the dial matcher (digits/spaces only) — a string
    // needs a letter unit on both sides to even reach unit lookup below.
    private val CONVERSION_REGEX = Regex(
        "^([+-]?\\d+(?:[.,]\\d+)?)\\s*([a-zA-Z]+)\\s+(?:(?:to|in)\\s+)?([a-zA-Z]+)$",
        RegexOption.IGNORE_CASE
    )

    private class LinearUnit(val category: Category, val symbol: String, val perBase: Double)

    // Every linear unit's factor is relative to its category's base unit
    // (meter, gram, milliliter, m/s, byte). Temperature is non-linear and
    // handled separately below. Bare "in" works as an inch alias despite also
    // being a connector word: the regex consumes a connector only between two
    // unit tokens, so "5 km in mi" still reads "in" as the connector while
    // "5 in to cm" / "5 m to in" / "5 in cm" resolve it as inches.
    private val linearUnits: Map<String, LinearUnit> = buildMap {
        fun unit(category: Category, symbol: String, perBase: Double, vararg aliases: String) {
            aliases.forEach { put(it, LinearUnit(category, symbol, perBase)) }
        }

        // Length (base: meter)
        unit(Category.LENGTH, "m", 1.0, "m", "meter", "meters", "metre", "metres")
        unit(Category.LENGTH, "km", 1000.0, "km", "kilometer", "kilometers", "kilometre", "kilometres")
        unit(Category.LENGTH, "cm", 0.01, "cm", "centimeter", "centimeters", "centimetre", "centimetres")
        unit(Category.LENGTH, "mm", 0.001, "mm", "millimeter", "millimeters", "millimetre", "millimetres")
        unit(Category.LENGTH, "mi", 1609.344, "mi", "mile", "miles")
        unit(Category.LENGTH, "yd", 0.9144, "yd", "yard", "yards")
        unit(Category.LENGTH, "ft", 0.3048, "ft", "foot", "feet")
        unit(Category.LENGTH, "in", 0.0254, "in", "inch", "inches")

        // Mass (base: gram)
        unit(Category.MASS, "g", 1.0, "g", "gram", "grams", "gramme", "grammes")
        unit(Category.MASS, "kg", 1000.0, "kg", "kilogram", "kilograms", "kilogramme", "kilogrammes")
        unit(Category.MASS, "mg", 0.001, "mg", "milligram", "milligrams")
        unit(Category.MASS, "lb", 453.592, "lb", "lbs", "pound", "pounds")
        unit(Category.MASS, "oz", 28.3495, "oz", "ounce", "ounces")

        // Volume (base: milliliter)
        unit(Category.VOLUME, "ml", 1.0, "ml", "milliliter", "milliliters", "millilitre", "millilitres")
        unit(Category.VOLUME, "l", 1000.0, "l", "liter", "liters", "litre", "litres")
        unit(Category.VOLUME, "gal", 3785.41, "gal", "gallon", "gallons")
        unit(Category.VOLUME, "qt", 946.353, "qt", "quart", "quarts")
        unit(Category.VOLUME, "pt", 473.176, "pt", "pint", "pints")
        unit(Category.VOLUME, "cup", 236.588, "cup", "cups")

        // Speed (base: m/s)
        unit(Category.SPEED, "m/s", 1.0, "ms", "mps")
        unit(Category.SPEED, "km/h", 0.277778, "kmh", "kph", "kmph")
        unit(Category.SPEED, "mph", 0.44704, "mph")
        unit(Category.SPEED, "knot", 0.514444, "knot", "knots")

        // Data size (base: byte, binary/1024 steps)
        unit(Category.DATA_SIZE, "B", 1.0, "b", "byte", "bytes")
        unit(Category.DATA_SIZE, "KB", 1024.0, "kb", "kilobyte", "kilobytes")
        unit(Category.DATA_SIZE, "MB", 1024.0 * 1024, "mb", "megabyte", "megabytes")
        unit(Category.DATA_SIZE, "GB", 1024.0 * 1024 * 1024, "gb", "gigabyte", "gigabytes")
        unit(Category.DATA_SIZE, "TB", 1024.0 * 1024 * 1024 * 1024, "tb", "terabyte", "terabytes")
    }

    // Temperature conversion isn't a simple factor, so it's kept as its own
    // small alias -> canonical-symbol map, checked before the linear units.
    private val temperatureUnits: Map<String, String> = mapOf(
        "c" to "C", "celsius" to "C", "centigrade" to "C",
        "f" to "F", "fahrenheit" to "F",
        "k" to "K", "kelvin" to "K",
    )

    fun looksLikeConversion(input: String): Boolean = CONVERSION_REGEX.matches(input.trim())

    fun convert(input: String): Result? {
        val match = CONVERSION_REGEX.matchEntire(input.trim()) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = match.groupValues[2].lowercase()
        val to = match.groupValues[3].lowercase()

        val fromTemp = temperatureUnits[from]
        val toTemp = temperatureUnits[to]
        if (fromTemp != null || toTemp != null) {
            // One side is a temperature unit and the other isn't (e.g. "5 c to kg") —
            // not a valid conversion, fall through to a normal search.
            if (fromTemp == null || toTemp == null) return null
            return Result(convertTemperature(amount, fromTemp, toTemp), degreeSymbol(toTemp))
        }

        val fromUnit = linearUnits[from] ?: return null
        val toUnit = linearUnits[to] ?: return null
        if (fromUnit.category != toUnit.category) return null
        val baseValue = amount * fromUnit.perBase
        return Result(baseValue / toUnit.perBase, toUnit.symbol)
    }

    private fun degreeSymbol(symbol: String) = if (symbol == "K") "K" else "°$symbol"

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "F" -> (value - 32) * 5 / 9
            "K" -> value - 273.15
            else -> value // "C"
        }
        return when (to) {
            "F" -> celsius * 9 / 5 + 32
            "K" -> celsius + 273.15
            else -> celsius // "C"
        }
    }
}
