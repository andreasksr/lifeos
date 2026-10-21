package app.lifeos.feature.ledger.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.ledger.LedgerSnapshotBuilder
import app.lifeos.core.domain.ledger.TimelineDay
import app.lifeos.core.domain.ledger.TransactionFilter
import app.lifeos.core.domain.ledger.TransactionQuery
import app.lifeos.core.model.Account
import app.lifeos.core.model.Category
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.ledger.LedgerDependencies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

/** Zeitraum der Liste: ein Monat oder alles (bei aktiver Suche sinnvoll). */
sealed interface Period {
    data class Month(val month: YearMonth) : Period
    data object All : Period
}

data class TransactionsUiState(
    val period: Period,
    val query: TransactionQuery,
    val days: List<TimelineDay>,
    val resultCount: Int,
    val totalCount: Int,
    val accounts: List<Account>,
    val categories: List<Category>,
    val today: java.time.LocalDate,
)

class TransactionsViewModel(private val deps: LedgerDependencies, initialMonth: YearMonth?) : ViewModel() {

    private val period = MutableStateFlow<Period>(initialMonth?.let { Period.Month(it) } ?: Period.Month(YearMonth.from(deps.clock.today())))
    private val query = MutableStateFlow(TransactionQuery())

    val state: StateFlow<TransactionsUiState?> = combine(
        deps.repository.observeAllTransactions(),
        deps.repository.observeAccounts(),
        deps.repository.observeCategories(),
        deps.repository.observeRecurring(),
        combine(period, query) { p, q -> p to q },
    ) { all, accounts, categories, recurring, pq ->
        val (p, q) = pq
        val scoped = when (p) {
            is Period.Month -> all.filter { YearMonth.from(it.bookingDate) == p.month }
            Period.All -> all
        }
        val filtered = TransactionFilter.apply(
            scoped, q,
            categories.associate { it.id to it.name },
            accounts.associate { it.id to it.name },
        )
        val accountById = accounts.associateBy { it.id }
        val categoryById = categories.associateBy { it.id }
        val recurringById = recurring.associateBy { it.id }
        val days = filtered.groupBy { it.bookingDate }
            .toSortedMap(compareByDescending { it })
            .map { (date, list) ->
                val entries = list.map { LedgerSnapshotBuilder.entry(it, accountById, categoryById, recurringById) }
                val net = list.fold(0L) { acc, tx ->
                    when (tx.kind) {
                        TransactionKind.INCOME, TransactionKind.REFUND -> acc + tx.amount.minor
                        TransactionKind.EXPENSE -> acc - tx.amount.minor
                        else -> acc
                    }
                }
                TimelineDay(date, entries, net)
            }
        TransactionsUiState(p, q, days, filtered.size, scoped.size, accounts, categories, deps.clock.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setText(text: String) = query.update { it.copy(text = text.take(80)) }

    fun toggleKind(kind: TransactionKind) = query.update {
        val kinds = if (kind in it.kinds) it.kinds - kind else it.kinds + kind
        it.copy(kinds = kinds)
    }

    fun setCategory(id: NodeId?) = query.update { it.copy(categoryId = id) }
    fun setAccount(id: NodeId?) = query.update { it.copy(accountId = id) }
    fun clearFilters() { query.value = TransactionQuery(text = query.value.text) }

    fun previousMonth() = period.update { if (it is Period.Month) Period.Month(it.month.minusMonths(1)) else it }
    fun nextMonth() = period.update { if (it is Period.Month) Period.Month(it.month.plusMonths(1)) else it }
    fun showAll() { period.value = Period.All }
    fun showMonth() { period.value = Period.Month(YearMonth.from(deps.clock.today())) }
}
