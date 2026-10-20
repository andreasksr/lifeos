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

enum class TransactionKind {
    EXPENSE,
    INCOME,

    /** Umbuchung zwischen zwei eigenen Konten – weder Einnahme noch Ausgabe. */
    TRANSFER;

    val categoryDirection: CategoryDirection?
        get() = when (this) {
            EXPENSE -> CategoryDirection.EXPENSE
            INCOME -> CategoryDirection.INCOME
            TRANSFER -> null
        }
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
