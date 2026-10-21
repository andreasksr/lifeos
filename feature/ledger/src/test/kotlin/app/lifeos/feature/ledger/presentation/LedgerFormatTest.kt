package app.lifeos.feature.ledger.presentation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerFormatTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test
    fun `monat und tag`() {
        assertEquals("Oktober 2026", LedgerFormat.monthTitle(YearMonth.of(2026, 10)))
        assertEquals("März 2027", LedgerFormat.monthTitle(YearMonth.of(2027, 3)))
        assertEquals("Heute", LedgerFormat.dayTitle(today, today))
        assertEquals("Gestern", LedgerFormat.dayTitle(today.minusDays(1), today))
        assertEquals("Mi, 14. Okt.", LedgerFormat.dayTitle(LocalDate.of(2026, 10, 14), today))
        assertEquals("Do, 31. Dez. 2026", LedgerFormat.dayTitle(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 5)))
        assertEquals("05.03.2026", LedgerFormat.numericDate(LocalDate.of(2026, 3, 5)))
    }

    @Test
    fun `faelligkeit und begruessung`() {
        assertEquals("heute fällig", LedgerFormat.relativeDue(today, today))
        assertEquals("in 3 Tagen fällig", LedgerFormat.relativeDue(today.plusDays(3), today))
        assertEquals("seit 2 Tagen überfällig", LedgerFormat.relativeDue(today.minusDays(2), today))
        assertEquals("Guten Morgen", LedgerFormat.greeting(7))
        assertEquals("Gute Nacht", LedgerFormat.greeting(2))
        assertEquals("21. Oktober 2026", LedgerFormat.longDate(LocalDate.of(2026, 10, 21)))
        assertEquals("Mär", LedgerFormat.monthAxis(YearMonth.of(2026, 3)))
    }

    @Test
    fun `prozent ist abgerundet`() {
        assertEquals("113 %", LedgerFormat.percent(1_133))
        assertEquals("0 %", LedgerFormat.percent(9))
    }
}
