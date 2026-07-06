package com.parem.launcher.helper

import org.junit.Assert.*
import org.junit.Test

class UnitConverterTest {

    // --- length ---

    @Test
    fun convert_km_to_mi() {
        val result = UnitConverter.convert("5 km in mi")
        assertNotNull(result)
        assertEquals(3.106856, result!!.value, 0.0001)
        assertEquals("mi", result.symbol)
    }

    @Test
    fun convert_km_to_mi_noSpaceBeforeUnit() {
        val result = UnitConverter.convert("5km in mi")
        assertNotNull(result)
        assertEquals(3.106856, result!!.value, 0.0001)
    }

    @Test
    fun convert_km_to_miles_wordForm() {
        val result = UnitConverter.convert("5 km to miles")
        assertNotNull(result)
        assertEquals(3.106856, result!!.value, 0.0001)
    }

    @Test
    fun convert_feet_to_meters() {
        val result = UnitConverter.convert("10 ft to m")
        assertNotNull(result)
        assertEquals(3.048, result!!.value, 0.0001)
    }

    @Test
    fun convert_inches_to_centimeters() {
        val result = UnitConverter.convert("2 inches to cm")
        assertNotNull(result)
        assertEquals(5.08, result!!.value, 0.0001)
    }

    // --- mass ---

    @Test
    fun convert_kg_to_lb() {
        val result = UnitConverter.convert("5 kg to lb")
        assertNotNull(result)
        assertEquals(11.0231, result!!.value, 0.001)
        assertEquals("lb", result.symbol)
    }

    @Test
    fun convert_ounces_to_grams() {
        val result = UnitConverter.convert("8 oz to g")
        assertNotNull(result)
        assertEquals(226.796, result!!.value, 0.01)
    }

    // --- temperature ---

    @Test
    fun convert_fahrenheit_to_celsius() {
        val result = UnitConverter.convert("100 f to c")
        assertNotNull(result)
        assertEquals(37.7778, result!!.value, 0.001)
        assertEquals("°C", result.symbol)
    }

    @Test
    fun convert_fahrenheit_to_celsius_noConnector() {
        val result = UnitConverter.convert("100f c")
        assertNotNull(result)
        assertEquals(37.7778, result!!.value, 0.001)
    }

    @Test
    fun convert_celsius_to_fahrenheit() {
        val result = UnitConverter.convert("0 c to f")
        assertNotNull(result)
        assertEquals(32.0, result!!.value, 0.0001)
    }

    @Test
    fun convert_celsius_to_kelvin() {
        val result = UnitConverter.convert("0 celsius to kelvin")
        assertNotNull(result)
        assertEquals(273.15, result!!.value, 0.0001)
        assertEquals("K", result.symbol)
    }

    @Test
    fun convert_negativeFahrenheit_to_celsius() {
        val result = UnitConverter.convert("-40 f to c")
        assertNotNull(result)
        assertEquals(-40.0, result!!.value, 0.0001)
    }

    // --- volume ---

    @Test
    fun convert_liters_to_gallons() {
        val result = UnitConverter.convert("5 l to gal")
        assertNotNull(result)
        assertEquals(1.32086, result!!.value, 0.001)
        assertEquals("gal", result.symbol)
    }

    @Test
    fun convert_cups_to_milliliters() {
        val result = UnitConverter.convert("2 cups to ml")
        assertNotNull(result)
        assertEquals(473.176, result!!.value, 0.01)
    }

    // --- speed ---

    @Test
    fun convert_kmh_to_mph() {
        val result = UnitConverter.convert("100 kmh to mph")
        assertNotNull(result)
        assertEquals(62.1371, result!!.value, 0.01)
        assertEquals("mph", result.symbol)
    }

    @Test
    fun convert_mph_to_kmh_wordConnector() {
        val result = UnitConverter.convert("60 mph in kmh")
        assertNotNull(result)
        assertEquals(96.5606, result!!.value, 0.01)
    }

    // --- data size ---

    @Test
    fun convert_mb_to_kb() {
        val result = UnitConverter.convert("5 mb to kb")
        assertNotNull(result)
        assertEquals(5120.0, result!!.value, 0.0001)
        assertEquals("KB", result.symbol)
    }

    @Test
    fun convert_gb_to_mb() {
        val result = UnitConverter.convert("2 gb to mb")
        assertNotNull(result)
        assertEquals(2048.0, result!!.value, 0.0001)
    }

    // --- commutativity / decimal separator ---

    @Test
    fun convert_commaDecimalSeparator() {
        val result = UnitConverter.convert("5,5 km to mi")
        assertNotNull(result)
        assertEquals(3.417542, result!!.value, 0.001)
    }

    // --- failure cases: recognized units, wrong pairing ---

    @Test
    fun convert_mismatchedCategories_returnsNull() {
        assertNull(UnitConverter.convert("5 km to kg"))
    }

    @Test
    fun convert_temperatureMixedWithNonTemperature_returnsNull() {
        assertNull(UnitConverter.convert("5 c to kg"))
    }

    @Test
    fun convert_unknownUnits_returnsNull() {
        assertNull(UnitConverter.convert("5 dogs to cats"))
    }

    // --- non-conversion strings must not parse (false-positive guard) ---

    @Test
    fun looksLikeConversion_appNameIsRejected() {
        assertFalse(UnitConverter.looksLikeConversion("instagram"))
        assertNull(UnitConverter.convert("instagram"))
    }

    @Test
    fun looksLikeConversion_numberWordPhraseIsRejected() {
        // Only one word after the number — no target unit to convert to.
        assertFalse(UnitConverter.looksLikeConversion("5 guys"))
        assertNull(UnitConverter.convert("5 guys"))
    }

    @Test
    fun looksLikeConversion_plainWordIsRejected() {
        assertFalse(UnitConverter.looksLikeConversion("whatsapp"))
        assertNull(UnitConverter.convert("whatsapp"))
    }

    @Test
    fun looksLikeConversion_bareNumberIsRejected() {
        assertFalse(UnitConverter.looksLikeConversion("100"))
        assertNull(UnitConverter.convert("100"))
    }

    @Test
    fun looksLikeConversion_phraseWithoutUnits_returnsNull() {
        // Shape matches (number, word, word) but neither word is a known unit.
        assertNull(UnitConverter.convert("5 guys walking"))
    }

    @Test
    fun looksLikeConversion_noDigitIsRejected() {
        assertFalse(UnitConverter.looksLikeConversion("call mom"))
        assertNull(UnitConverter.convert("call mom"))
    }

    @Test
    fun looksLikeConversion_digitGlobIsRejected() {
        assertFalse(UnitConverter.looksLikeConversion("7zip"))
        assertNull(UnitConverter.convert("7zip"))
    }
}
