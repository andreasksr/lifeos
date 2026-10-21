package app.lifeos.core.model

import java.time.LocalDate
import java.time.YearMonth

enum class AccountKind { CASH, CHECKING, SAVINGS, CREDIT_CARD }

data class Account(
    val id: NodeId,
    val name: String,
    val kind: AccountKind,
    val currency: CurrencyCode,
    /** Anfangsbestand in Minor Units – kann negativ sein (z. B. Kreditkarte). */
    val openingBalanceMinor: Long,
    val sortOrder: Int,
    val archived: Boolean = false,
)

enum class CategoryDirection { EXPENSE, INCOME }

data class Category(
    val id: NodeId,
    val name: String,
    val direction: CategoryDirection,
    val parentId: NodeId? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

/**
 * Art einer Buchung. Gespeichert als TEXT – neue Werte nur anhängen.
 *
 * | Art            | Saldo     | Statistik                          | Splits                 |
 * |----------------|-----------|------------------------------------|------------------------|
 * | EXPENSE        | −         | Ausgabe                            | ≥1, Ausgabenkategorien |
 * | INCOME         | +         | Einnahme                           | ≥1, Einnahmekategorien |
 * | TRANSFER       | −/+       | keine                              | keine                  |
 * | REFUND         | +         | **mindert** Ausgaben der Kategorie | ≥1, Ausgabenkategorien |
 * | CORRECTION_IN  | +         | keine (Kontostandskorrektur)       | keine                  |
 * | CORRECTION_OUT | −         | keine (Kontostandskorrektur)       | keine                  |
 */
enum class TransactionKind {
    EXPENSE,
    INCOME,
    TRANSFER,
    REFUND,
    CORRECTION_IN,
    CORRECTION_OUT;

    /** Richtung der Kategorien in den Splits; `null` = Buchung hat keine Splits. */
    val categoryDirection: CategoryDirection?
        get() = when (this) {
            EXPENSE, REFUND -> CategoryDirection.EXPENSE
            INCOME -> CategoryDirection.INCOME
            TRANSFER, CORRECTION_IN, CORRECTION_OUT -> null
        }

    val requiresSplits: Boolean get() = categoryDirection != null

    /** Erhöht den Saldo des (Quell-)Kontos. */
    val isInflow: Boolean get() = this == INCOME || this == REFUND || this == CORRECTION_IN

    val isCorrection: Boolean get() = this == CORRECTION_IN || this == CORRECTION_OUT
}

/** Aufteilung einer Buchung auf eine Kategorie. Betrag immer > 0. */
data class Split(
    val id: NodeId,
    val categoryId: NodeId,
    val amountMinor: Long,
    val memo: String? = null,
)

/**
 * Die eine, einzige finanzielle Buchung (ADR-001/007).
 * [amount] ist immer positiv; die Richtung ergibt sich aus [kind].
 */
data class LedgerTransaction(
    val id: NodeId,
    val kind: TransactionKind,
    val accountId: NodeId,
    val counterAccountId: NodeId?,
    val amount: Money,
    val bookingDate: LocalDate,
    val payee: String?,
    val note: String?,
    val splits: List<Split>,
    val isSample: Boolean = false,
    /**
     * Gesetzt, wenn die Buchung eine Fälligkeit einer wiederkehrenden Zahlung ausführt.
     * Abgeleitet aus `recurring_booking` – wird nicht über diese Klasse geschrieben.
     */
    val recurringId: NodeId? = null,
)

/**
 * Monatsbudget einer Ausgabenkategorie. Gilt ab [validFrom] bis zum nächsten Budget derselben
 * Kategorie mit späterem `validFrom`.
 */
data class Budget(
    val id: NodeId,
    val categoryId: NodeId,
    val validFrom: YearMonth,
    val amountMinor: Long,
    val currency: CurrencyCode,
)

/** Gesamtbudget für alle Ausgaben eines Monats; gilt ab [validFrom] bis zur nächsten Änderung. */
data class TotalBudget(
    val id: NodeId,
    val validFrom: YearMonth,
    val amountMinor: Long,
    val currency: CurrencyCode,
)

/**
 * Wiederkehrende Einnahme oder Ausgabe (Miete, Versicherung, Abo …).
 * Sie ist ein **Plan**: Fälligkeiten werden berechnet, aber nie automatisch gebucht.
 * Erst „Ausführen" erzeugt eine echte Buchung (siehe [RecurringBooking]).
 */
data class RecurringPayment(
    val id: NodeId,
    val name: String,
    val kind: TransactionKind,
    val accountId: NodeId,
    val categoryId: NodeId,
    val amount: Money,
    /** 1 = monatlich, 3 = vierteljährlich, 6 = halbjährlich, 12 = jährlich. */
    val intervalMonths: Int,
    /** Erste Fälligkeit; Folgetermine = anchor + k · intervalMonths (Monatsende wird geklemmt). */
    val anchorDate: LocalDate,
    val endDate: LocalDate? = null,
    val payee: String? = null,
    val note: String? = null,
    val active: Boolean = true,
    /** Zählt als Fixkosten (Fixkostenquote). */
    val isFixedCost: Boolean = true,
) {
    init {
        require(kind == TransactionKind.EXPENSE || kind == TransactionKind.INCOME) {
            "Wiederkehrende Zahlungen sind Einnahmen oder Ausgaben"
        }
    }

    companion object {
        val ALLOWED_INTERVALS = listOf(1, 2, 3, 6, 12)
    }
}

enum class RecurringStatus { BOOKED, SKIPPED }

/** Was mit einer konkreten Fälligkeit passiert ist. Pro (Plan, Fälligkeitsdatum) höchstens einmal. */
data class RecurringBooking(
    val recurringId: NodeId,
    val dueDate: LocalDate,
    val status: RecurringStatus,
    val transactionId: NodeId?,
)
