package app.lifeos.core.model

/**
 * ISO-4217-Währungscode. Bestimmt die Anzahl der Nachkommastellen (Minor Units).
 */
@JvmInline
value class CurrencyCode(val value: String) {
    init {
        require(value.length == 3 && value.all { it in 'A'..'Z' }) {
            "Ungültiger ISO-4217-Code: '$value'"
        }
    }

    /** Anzahl der Nachkommastellen dieser Währung. */
    val minorDigits: Int
        get() = when (value) {
            "JPY", "KRW", "ISK", "CLP", "VND", "UGX", "XAF", "XOF" -> 0
            "BHD", "KWD", "OMR", "JOD", "TND", "LYD", "IQD" -> 3
            else -> 2
        }

    override fun toString(): String = value

    companion object {
        val EUR = CurrencyCode("EUR")
    }
}

/**
 * Exakter Geldbetrag in der kleinsten Einheit der Währung (z. B. Cent).
 *
 * Regeln (ADR-002):
 *  - niemals Fließkomma,
 *  - Operationen nur zwischen gleichen Währungen,
 *  - Überlauf führt zu [ArithmeticException] statt zu stillen Fehlbeträgen.
 */
data class Money(val minor: Long, val currency: CurrencyCode) : Comparable<Money> {

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(minor, other.minor), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(minor, other.minor), currency)
    }

    operator fun unaryMinus(): Money = Money(Math.negateExact(minor), currency)

    val isZero: Boolean get() = minor == 0L
    val isPositive: Boolean get() = minor > 0L
    val isNegative: Boolean get() = minor < 0L

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minor.compareTo(other.minor)
    }

    private fun requireSameCurrency(other: Money) {
        if (currency != other.currency) {
            throw CurrencyMismatchException(currency, other.currency)
        }
    }

    companion object {
        fun zero(currency: CurrencyCode) = Money(0L, currency)
        fun eur(minor: Long) = Money(minor, CurrencyCode.EUR)
    }
}

class CurrencyMismatchException(val left: CurrencyCode, val right: CurrencyCode) :
    IllegalArgumentException("Währungen passen nicht zusammen: $left ≠ $right")

/** Summiert exakt mit Überlaufprüfung. */
fun Iterable<Long>.sumExact(): Long = fold(0L) { acc, v -> Math.addExact(acc, v) }
