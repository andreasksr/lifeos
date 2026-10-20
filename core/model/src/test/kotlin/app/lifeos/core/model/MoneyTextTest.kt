package app.lifeos.core.model

import app.lifeos.core.model.MoneyText.ParseError
import app.lifeos.core.model.MoneyText.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTextTest {

    private fun ok(input: String, currency: CurrencyCode = CurrencyCode.EUR) =
        (MoneyText.parse(input, currency) as ParseResult.Success).minor

    private fun err(input: String, currency: CurrencyCode = CurrencyCode.EUR) =
        (MoneyText.parse(input, currency) as ParseResult.Failure).reason

    @Test
    fun `deutsche eingaben`() {
        assertEquals(1250L, ok("12,50"))
        assertEquals(1250L, ok("12,5"))
        assertEquals(1200L, ok("12"))
        assertEquals(1200L, ok("12,"))
        assertEquals(50L, ok(",5"))
        assertEquals(123456L, ok("1.234,56"))
        assertEquals(123456789L, ok("1.234.567,89"))
        assertEquals(8990L, ok(" 89,90 € "))
        assertEquals(8990L, ok("89,90 €"))
    }

    @Test
    fun `punkt als dezimaltrenner nur wenn eindeutig`() {
        assertEquals(1250L, ok("12.50"))
        assertEquals(150L, ok("1.5"))
        // Drei Stellen nach dem Punkt = Tausendertrenner
        assertEquals(123400L, ok("1.234"))
    }

    @Test
    fun `vorzeichen`() {
        assertEquals(-500L, ok("-5"))
        assertEquals(-500L, ok("−5"))
        assertEquals(500L, ok("+5"))
    }

    @Test
    fun `fehlerhafte eingaben werden nie geraten`() {
        assertEquals(ParseError.EMPTY, err(""))
        assertEquals(ParseError.EMPTY, err("  € "))
        assertEquals(ParseError.EMPTY, err("-"))
        assertEquals(ParseError.INVALID_CHARACTER, err("12a"))
        assertEquals(ParseError.INVALID_CHARACTER, err("1e3"))
        assertEquals(ParseError.MULTIPLE_DECIMAL_SEPARATORS, err("1,2,3"))
        assertEquals(ParseError.INVALID_GROUPING, err("1.23,45"))
        assertEquals(ParseError.INVALID_GROUPING, err("12."))
        assertEquals(ParseError.INVALID_GROUPING, err("1,234.5"))
        assertEquals(ParseError.TOO_MANY_DECIMALS, err("1,234"))
        assertEquals(ParseError.OUT_OF_RANGE, err("99999999999999999999"))
    }

    @Test
    fun `waehrungen ohne nachkommastellen`() {
        val jpy = CurrencyCode("JPY")
        assertEquals(1234L, ok("1.234", jpy))
        assertEquals(ParseError.TOO_MANY_DECIMALS, err("12,5", jpy))
    }

    @Test
    fun `formatierung`() {
        assertEquals("0,00 €", MoneyText.format(0))
        assertEquals("0,05 €", MoneyText.format(5))
        assertEquals("1.234,56 €", MoneyText.format(123456))
        assertEquals("−1.234,56 €", MoneyText.format(-123456))
        assertEquals("+12,00 €", MoneyText.format(1200, showPlus = true))
        assertEquals("1.000.000,00", MoneyText.format(100000000, withSymbol = false))
        assertEquals("1.234 ¥", MoneyText.format(1234, CurrencyCode("JPY")))
        // Extremwert darf nicht abstürzen
        assertEquals("−92.233.720.368.547.758,08 €", MoneyText.format(Long.MIN_VALUE))
    }

    @Test
    fun `round trip fuer editierfelder`() {
        listOf(0L, 5L, 99L, 100L, 123456L, -987654321L, 100000000000L).forEach { v ->
            assertEquals(v, ok(MoneyText.toEditText(v)))
            assertEquals(v, ok(MoneyText.format(v)))
        }
    }
}
