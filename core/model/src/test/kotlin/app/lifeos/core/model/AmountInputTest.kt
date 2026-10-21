package app.lifeos.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {
    private fun type(keys: String): String = keys.fold("") { acc, k ->
        when (k) {
            ',' -> AmountInput.comma(acc)
            '<' -> AmountInput.backspace(acc)
            else -> AmountInput.digit(acc, k)
        }
    }

    @Test
    fun `tastenfolgen ergeben eindeutige betraege`() {
        assertEquals("12,5", type("12,5"))
        assertEquals("12,50", type("12,509"))      // dritte Nachkommastelle wird ignoriert
        assertEquals("0,5", type(",5"))
        assertEquals("7", type("07"))              // führende Null wird ersetzt
        assertEquals("12,", type("12,,"))          // nur ein Komma
        assertEquals("1", type("12<"))
        assertEquals("", type("1<<"))
        assertEquals("123456789", type("1234567890")) // Obergrenze Vorkommastellen
        assertEquals(1250L, (MoneyText.parse(type("12,5")) as MoneyText.ParseResult.Success).minor)
    }
}
