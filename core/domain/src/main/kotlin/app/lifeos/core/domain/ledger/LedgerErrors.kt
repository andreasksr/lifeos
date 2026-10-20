package app.lifeos.core.domain.ledger

import app.lifeos.core.model.NodeId

/** Fachliche Verstöße bei Buchungen. Erwartbare Fehler sind Werte, keine Exceptions. */
sealed interface LedgerError {
    data object AmountNotPositive : LedgerError
    data object MissingSplits : LedgerError
    data object SplitsOnTransfer : LedgerError
    data object SplitAmountNotPositive : LedgerError
    data class SplitSumMismatch(val expected: Long, val actual: Long) : LedgerError
    data object TransferNeedsCounterAccount : LedgerError
    data object TransferSameAccount : LedgerError
    data object CounterAccountOnNonTransfer : LedgerError
    data class UnknownAccount(val id: NodeId) : LedgerError
    data class ArchivedAccount(val id: NodeId) : LedgerError
    data class UnknownCategory(val id: NodeId) : LedgerError
    data class CategoryDirectionMismatch(val categoryId: NodeId) : LedgerError
    data object CurrencyMismatch : LedgerError
    data object AmountOutOfRange : LedgerError
    data object TextTooLong : LedgerError
    data object NameRequired : LedgerError
    data object TransactionNotFound : LedgerError
}

sealed interface LedgerResult<out T> {
    data class Success<T>(val value: T) : LedgerResult<T>
    data class Invalid(val errors: List<LedgerError>) : LedgerResult<Nothing> {
        init {
            require(errors.isNotEmpty())
        }
    }
}
