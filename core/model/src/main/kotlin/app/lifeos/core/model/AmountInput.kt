package app.lifeos.core.model

/**
 * Reiner Zustandsautomat für Betragseingabe per Keypad – Android-frei und testbar.
 * Erlaubt höchstens [maxDecimals] Nachkommastellen und [maxIntegerDigits] Vorkommastellen.
 */
object AmountInput {
    fun digit(text: String, d: Char, maxDecimals: Int = 2, maxIntegerDigits: Int = 9): String {
        require(d in '0'..'9')
        val comma = text.indexOf(',')
        if (comma >= 0) {
            return if (text.length - comma - 1 >= maxDecimals) text else text + d
        }
        if (text == "0") return d.toString()
        return if (text.length >= maxIntegerDigits) text else text + d
    }

    fun comma(text: String, maxDecimals: Int = 2): String = when {
        maxDecimals == 0 || text.contains(',') -> text
        text.isEmpty() -> "0,"
        else -> "$text,"
    }

    fun backspace(text: String): String = text.dropLast(1)
}
