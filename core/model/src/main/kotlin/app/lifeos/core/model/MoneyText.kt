package app.lifeos.core.model

/**
 * Exaktes Parsen und Formatieren von Geldbeträgen im deutschen Format.
 *
 * Bewusst ohne `java.text.NumberFormat`: dessen Verhalten hängt von Gerät, Locale und
 * ICU-Version ab. Diese Implementierung ist deterministisch und vollständig getestet.
 */
object MoneyText {

    /** U+2212 MINUS SIGN – typografisch korrektes Minus. */
    const val MINUS = '−'

    /** U+202F NARROW NO-BREAK SPACE zwischen Betrag und Symbol. */
    const val NNBSP = ' '

    sealed interface ParseResult {
        data class Success(val minor: Long) : ParseResult
        data class Failure(val reason: ParseError) : ParseResult
    }

    enum class ParseError {
        EMPTY,
        INVALID_CHARACTER,
        MULTIPLE_DECIMAL_SEPARATORS,
        INVALID_GROUPING,
        TOO_MANY_DECIMALS,
        OUT_OF_RANGE,
    }

    /**
     * Wandelt Nutzereingaben wie `12,5`, `1.234,56`, `-3`, `1234.56` in Minor Units um.
     *
     * Regeln:
     *  - Enthält die Eingabe ein Komma, ist es das Dezimaltrennzeichen und Punkte sind Tausendertrenner.
     *  - Ohne Komma gilt ein einzelner Punkt mit höchstens [CurrencyCode.minorDigits] Nachkommastellen
     *    als Dezimalpunkt; sonst sind Punkte Tausendertrenner (`1.234` = 1234).
     *  - Mehr Nachkommastellen als die Währung erlaubt sind ein Fehler – es wird nie still gerundet.
     */
    fun parse(input: String, currency: CurrencyCode = CurrencyCode.EUR): ParseResult {
        var s = input.trim()
            .replace("€", "")
            .replace(" ", "")
            .replace(" ", "")
            .replace(NNBSP.toString(), "")
        if (s.isEmpty()) return ParseResult.Failure(ParseError.EMPTY)

        var negative = false
        when (s.first()) {
            '-', MINUS -> { negative = true; s = s.substring(1) }
            '+' -> s = s.substring(1)
        }
        if (s.isEmpty()) return ParseResult.Failure(ParseError.EMPTY)
        if (s.any { !it.isDigitAscii() && it != '.' && it != ',' }) {
            return ParseResult.Failure(ParseError.INVALID_CHARACTER)
        }

        val digits = currency.minorDigits
        val integerPart: String
        val fractionPart: String

        val commaCount = s.count { it == ',' }
        val dotCount = s.count { it == '.' }
        when {
            commaCount > 1 -> return ParseResult.Failure(ParseError.MULTIPLE_DECIMAL_SEPARATORS)
            commaCount == 1 -> {
                val idx = s.indexOf(',')
                val rawInt = s.substring(0, idx)
                fractionPart = s.substring(idx + 1)
                if (fractionPart.contains('.')) return ParseResult.Failure(ParseError.INVALID_GROUPING)
                integerPart = ungroup(rawInt) ?: return ParseResult.Failure(ParseError.INVALID_GROUPING)
            }
            dotCount == 1 && s.substringAfter('.').length in 1..digits -> {
                integerPart = s.substringBefore('.')
                fractionPart = s.substringAfter('.')
            }
            dotCount >= 1 -> {
                integerPart = ungroup(s) ?: return ParseResult.Failure(ParseError.INVALID_GROUPING)
                fractionPart = ""
            }
            else -> {
                integerPart = s
                fractionPart = ""
            }
        }

        if (integerPart.isEmpty() && fractionPart.isEmpty()) return ParseResult.Failure(ParseError.EMPTY)
        if (fractionPart.length > digits) return ParseResult.Failure(ParseError.TOO_MANY_DECIMALS)

        return try {
            val intValue = if (integerPart.isEmpty()) 0L else integerPart.toLongStrict()
            val fracValue = if (fractionPart.isEmpty()) 0L else fractionPart.padEnd(digits, '0').toLongStrict()
            val minor = Math.addExact(Math.multiplyExact(intValue, pow10(digits)), fracValue)
            ParseResult.Success(if (negative) Math.negateExact(minor) else minor)
        } catch (_: ArithmeticException) {
            ParseResult.Failure(ParseError.OUT_OF_RANGE)
        } catch (_: NumberFormatException) {
            ParseResult.Failure(ParseError.OUT_OF_RANGE)
        }
    }

    /**
     * Formatiert z. B. `123456` (EUR) als `1.234,56 €`.
     * @param showPlus  `+` bei positiven Beträgen voranstellen (Zeitleiste, Geldflüsse).
     * @param withSymbol Währungssymbol anhängen.
     */
    fun format(
        minor: Long,
        currency: CurrencyCode = CurrencyCode.EUR,
        showPlus: Boolean = false,
        withSymbol: Boolean = true,
    ): String {
        val digits = currency.minorDigits
        val negative = minor < 0
        // Long.MIN_VALUE kann nicht negiert werden – über String arbeiten.
        val absText = if (negative) minor.toString().substring(1) else minor.toString()
        val padded = absText.padStart(digits + 1, '0')
        val intText = padded.substring(0, padded.length - digits)
        val fracText = padded.substring(padded.length - digits)

        val grouped = buildString {
            intText.forEachIndexed { i, c ->
                if (i > 0 && (intText.length - i) % 3 == 0) append('.')
                append(c)
            }
        }
        val sign = when {
            negative -> MINUS.toString()
            showPlus && minor > 0 -> "+"
            else -> ""
        }
        val number = if (digits > 0) "$sign$grouped,$fracText" else "$sign$grouped"
        return if (withSymbol) "$number$NNBSP${symbol(currency)}" else number
    }

    fun format(money: Money, showPlus: Boolean = false): String =
        format(money.minor, money.currency, showPlus)

    /** Eingabetext für Editierfelder, ohne Tausenderpunkte und Symbol: `1234,5` → `1234,50`. */
    fun toEditText(minor: Long, currency: CurrencyCode = CurrencyCode.EUR): String =
        format(minor, currency, withSymbol = false).replace(".", "").replace(MINUS, '-')

    fun symbol(currency: CurrencyCode): String = when (currency.value) {
        "EUR" -> "€"
        "USD" -> "$"
        "GBP" -> "£"
        "CHF" -> "CHF"
        "JPY" -> "¥"
        else -> currency.value
    }

    private fun ungroup(raw: String): String? {
        if (!raw.contains('.')) return raw
        val groups = raw.split('.')
        if (groups.first().isEmpty() || groups.first().length > 3) return null
        if (groups.drop(1).any { it.length != 3 }) return null
        return groups.joinToString("")
    }

    private fun Char.isDigitAscii() = this in '0'..'9'

    private fun String.toLongStrict(): Long {
        if (isEmpty() || any { !it.isDigitAscii() }) throw NumberFormatException(this)
        return toLong()
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r = Math.multiplyExact(r, 10L) }
        return r
    }
}
