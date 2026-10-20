package app.lifeos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `addition und subtraktion sind exakt`() {
        // Klassischer Fließkommafehler: 0.1 + 0.2 != 0.3 – in Minor Units unmöglich.
        assertEquals(Money.eur(30), Money.eur(10) + Money.eur(20))
        assertEquals(Money.eur(-5), Money.eur(10) - Money.eur(15))
        assertEquals(Money.eur(-7), -Money.eur(7))
    }

    @Test
    fun `verschiedene waehrungen werden nie still verrechnet`() {
        val usd = Money(100, CurrencyCode("USD"))
        assertThrows(CurrencyMismatchException::class.java) { Money.eur(100) + usd }
        assertThrows(CurrencyMismatchException::class.java) { Money.eur(100).compareTo(usd) }
    }

    @Test
    fun `ueberlauf wird erkannt statt still verfaelscht`() {
        assertThrows(ArithmeticException::class.java) { Money.eur(Long.MAX_VALUE) + Money.eur(1) }
        assertThrows(ArithmeticException::class.java) { -Money.eur(Long.MIN_VALUE) }
        assertThrows(ArithmeticException::class.java) { listOf(Long.MAX_VALUE, 1L).sumExact() }
    }

    @Test
    fun `waehrungscode wird validiert`() {
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode("eur") }
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode("EURO") }
        assertEquals(2, CurrencyCode.EUR.minorDigits)
        assertEquals(0, CurrencyCode("JPY").minorDigits)
        assertEquals(3, CurrencyCode("KWD").minorDigits)
    }

    @Test
    fun `vergleich und vorzeichen`() {
        assertTrue(Money.eur(1) > Money.eur(0))
        assertTrue(Money.eur(-1).isNegative)
        assertTrue(Money.zero(CurrencyCode.EUR).isZero)
    }

    @Test
    fun `node id akzeptiert nur kanonische uuids`() {
        val id = NodeId.random()
        assertEquals(id, NodeId(id.value))
        assertThrows(IllegalArgumentException::class.java) { NodeId("123") }
        assertThrows(IllegalArgumentException::class.java) { NodeId("6F9619FF-8B86-D011-B42D-00C04FC964FF") }
    }
}
