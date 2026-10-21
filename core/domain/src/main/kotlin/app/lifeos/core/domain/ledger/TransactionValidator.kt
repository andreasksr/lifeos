package app.lifeos.core.domain.ledger

import app.lifeos.core.model.Account
import app.lifeos.core.model.Category
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.TransactionKind

/**
 * Prüft alle fachlichen Invarianten einer Buchung, bevor sie gespeichert wird.
 * Die Datenbank-Trigger erzwingen einen Teil davon zusätzlich (Verteidigung in der Tiefe).
 */
object TransactionValidator {

    const val MAX_TEXT_LENGTH = 500

    /** Obergrenze je Buchung: 1 Mrd. Einheiten – schützt Summen sicher vor Long-Überlauf. */
    const val MAX_AMOUNT_MINOR = 100_000_000_000L

    fun validate(
        tx: LedgerTransaction,
        accounts: Map<NodeId, Account>,
        categories: Map<NodeId, Category>,
    ): List<LedgerError> {
        val errors = mutableListOf<LedgerError>()

        if (tx.amount.minor <= 0) errors += LedgerError.AmountNotPositive
        if (tx.amount.minor > MAX_AMOUNT_MINOR) errors += LedgerError.AmountOutOfRange
        if ((tx.payee?.length ?: 0) > MAX_TEXT_LENGTH || (tx.note?.length ?: 0) > MAX_TEXT_LENGTH) {
            errors += LedgerError.TextTooLong
        }

        val account = accounts[tx.accountId]
        when {
            account == null -> errors += LedgerError.UnknownAccount(tx.accountId)
            account.archived -> errors += LedgerError.ArchivedAccount(tx.accountId)
            account.currency != tx.amount.currency -> errors += LedgerError.CurrencyMismatch
        }

        when (tx.kind) {
            TransactionKind.TRANSFER -> {
                val counterId = tx.counterAccountId
                if (counterId == null) {
                    errors += LedgerError.TransferNeedsCounterAccount
                } else if (counterId == tx.accountId) {
                    errors += LedgerError.TransferSameAccount
                } else {
                    val counter = accounts[counterId]
                    when {
                        counter == null -> errors += LedgerError.UnknownAccount(counterId)
                        counter.archived -> errors += LedgerError.ArchivedAccount(counterId)
                        counter.currency != tx.amount.currency -> errors += LedgerError.CurrencyMismatch
                    }
                }
                if (tx.splits.isNotEmpty()) errors += LedgerError.SplitsOnTransfer
            }

            TransactionKind.CORRECTION_IN, TransactionKind.CORRECTION_OUT -> {
                if (tx.counterAccountId != null) errors += LedgerError.CounterAccountOnNonTransfer
                if (tx.splits.isNotEmpty()) errors += LedgerError.SplitsOnTransfer
            }

            TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.REFUND -> {
                if (tx.counterAccountId != null) errors += LedgerError.CounterAccountOnNonTransfer
                if (tx.splits.isEmpty()) {
                    errors += LedgerError.MissingSplits
                } else {
                    if (tx.splits.any { it.amountMinor <= 0 }) errors += LedgerError.SplitAmountNotPositive
                    val sum = try {
                        tx.splits.fold(0L) { acc, s -> Math.addExact(acc, s.amountMinor) }
                    } catch (_: ArithmeticException) {
                        null
                    }
                    if (sum == null) {
                        errors += LedgerError.AmountOutOfRange
                    } else if (sum != tx.amount.minor) {
                        errors += LedgerError.SplitSumMismatch(tx.amount.minor, sum)
                    }
                    val expectedDirection = tx.kind.categoryDirection
                    tx.splits.forEach { split ->
                        val category = categories[split.categoryId]
                        when {
                            category == null -> errors += LedgerError.UnknownCategory(split.categoryId)
                            category.direction != expectedDirection ->
                                errors += LedgerError.CategoryDirectionMismatch(split.categoryId)
                        }
                    }
                }
            }
        }
        return errors.distinct()
    }
}
