package com.parem.launcher.helper

import com.parem.launcher.helper.CurrencyConverter.Rates
import org.junit.Assert.*
import org.junit.Test

class CurrencyConverterTest {

    // eurofxref-daily.xml as served by the ECB on 2026-10-03.
    private val ecbXml = """
<?xml version="1.0" encoding="UTF-8"?>
<gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01" xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref">
	<gesmes:subject>Reference rates</gesmes:subject>
	<gesmes:Sender>
		<gesmes:name>European Central Bank</gesmes:name>
	</gesmes:Sender>
	<Cube>
		<Cube time='2026-10-02'>
			<Cube currency='USD' rate='1.1225'/>
			<Cube currency='JPY' rate='176.99'/>
			<Cube currency='CZK' rate='24.470'/>
			<Cube currency='DKK' rate='7.4736'/>
			<Cube currency='GBP' rate='0.85033'/>
			<Cube currency='HUF' rate='369.18'/>
			<Cube currency='PLN' rate='4.3775'/>
			<Cube currency='RON' rate='5.3488'/>
			<Cube currency='SEK' rate='11.2900'/>
			<Cube currency='CHF' rate='0.9279'/>
			<Cube currency='ISK' rate='137.00'/>
			<Cube currency='NOK' rate='10.8315'/>
			<Cube currency='TRY' rate='55.1650'/>
			<Cube currency='AUD' rate='1.6176'/>
			<Cube currency='BRL' rate='5.8610'/>
			<Cube currency='CAD' rate='1.5984'/>
			<Cube currency='CNY' rate='7.5259'/>
			<Cube currency='HKD' rate='8.8084'/>
			<Cube currency='IDR' rate='20149.32'/>
			<Cube currency='ILS' rate='3.4408'/>
			<Cube currency='INR' rate='108.1245'/>
			<Cube currency='KRW' rate='1513.44'/>
			<Cube currency='MXN' rate='20.5806'/>
			<Cube currency='MYR' rate='4.5849'/>
			<Cube currency='NZD' rate='2.0002'/>
			<Cube currency='PHP' rate='70.265'/>
			<Cube currency='SGD' rate='1.4366'/>
			<Cube currency='THB' rate='37.710'/>
			<Cube currency='ZAR' rate='18.7839'/>
		</Cube>
	</Cube>
</gesmes:Envelope>
"""

    private val rates = Rates("2026-10-02", mapOf("USD" to 1.1225, "GBP" to 0.85033, "JPY" to 176.99))

    // --- shape ---

    @Test
    fun looksLikeCurrency_parses() {
        for (q in listOf("10 eur in usd", "10eur usd", "1,5 GBP to jpy", "10 EuR To UsD", " 10 eur usd ")) {
            assertTrue("query '$q'", CurrencyConverter.looksLikeCurrency(q))
        }
    }

    @Test
    fun looksLikeCurrency_rejects() {
        for (q in listOf("10 abc in usd", "10 km in usd", "eur in usd", "10 eur", "chrome", "spotify",
            "5 in to cm", "100 f c", "10 euro in usd", "-10 eur in usd")) {
            assertFalse("query '$q'", CurrencyConverter.looksLikeCurrency(q))
        }
    }

    // --- conversion ---

    @Test
    fun convert_eurToUsd() {
        val result = CurrencyConverter.convert("100 eur in usd", rates)!!
        assertEquals(112.25, result.value, 1e-9)
        assertEquals("112.25 USD", result.format())
        assertEquals("2026-10-02", result.date)
    }

    @Test
    fun convert_usdToEur() {
        assertEquals(10.0, CurrencyConverter.convert("11.225 usd to eur", rates)!!.value, 1e-9)
    }

    @Test
    fun convert_crossRate() {
        val result = CurrencyConverter.convert("1,5 GBP to jpy", rates)!!
        assertEquals(1.5 / 0.85033 * 176.99, result.value, 1e-9)
        assertEquals("312 JPY", result.format())
    }

    @Test
    fun convert_eurToEur() {
        assertEquals("10.00 EUR", CurrencyConverter.convert("10 eur eur", rates)!!.format())
    }

    @Test
    fun convert_missingRateOrNotCurrency_isNull() {
        assertNull(CurrencyConverter.convert("10 eur in chf", rates))
        assertNull(CurrencyConverter.convert("10 km in mi", rates))
    }

    // --- ECB parsing ---

    @Test
    fun parseEcbXml_realFile() {
        val parsed = CurrencyConverter.parseEcbXml(ecbXml)!!
        assertEquals("2026-10-02", parsed.date)
        assertEquals(29, parsed.perEur.size)
        assertEquals(1.1225, parsed.perEur["USD"]!!, 0.0)
        assertEquals(20149.32, parsed.perEur["IDR"]!!, 0.0)
        // Every published code is recognised by the shape check.
        assertEquals(CurrencyConverter.KNOWN_CODES - "EUR", parsed.perEur.keys)
    }

    @Test
    fun parseEcbXml_garbage_isNull() {
        assertNull(CurrencyConverter.parseEcbXml(""))
        assertNull(CurrencyConverter.parseEcbXml("<html>503 Service Unavailable</html>"))
        assertNull(CurrencyConverter.parseEcbXml("<Cube time='2026-10-02'></Cube>"))
        assertNull(CurrencyConverter.parseEcbXml("<Cube currency='USD' rate='1.1'/>"))
    }

    // --- cache round-trip ---

    @Test
    fun serialize_roundTrip() {
        val parsed = CurrencyConverter.parseEcbXml(ecbXml)!!
        assertEquals(parsed, Rates.deserialize(parsed.serialize()))
    }

    @Test
    fun deserialize_bad_isNull() {
        assertNull(Rates.deserialize(null))
        assertNull(Rates.deserialize(""))
        assertNull(Rates.deserialize("USD=1.1"))
        assertNull(Rates.deserialize("2026-10-02|"))
    }
}
