package app.lifeos.core.domain.ledger

import app.lifeos.core.domain.ports.LedgerRepository
import app.lifeos.core.model.Account
import app.lifeos.core.model.Budget
import app.lifeos.core.model.Category
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.TransactionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth

enum class FlowDirection { IN, OUT, MOVE }

data class AccountBalance(val account: Account, val balance: Money)

data class TimelineEntry(
    val transaction: LedgerTransaction,
    val title: String,
    val categoryNames: List<String>,
    val accountName: String,
    val counterAccountName: String?,
    val direction: FlowDirection,
)

data class TimelineDay(
    val date: LocalDate,
    val entries: List<TimelineEntry>,
    /** Einnahmen − Ausgaben dieses Tages (Umbuchungen neutral). */
    val netMinor: Long,
)

data class CategoryLine(
    val category: Category,
    val totalMinor: Long,
    /** Anteil an allen Ausgaben bzw. Einnahmen des Monats in Promille. */
    val sharePermille: Long,
    val budget: BudgetStatus?,
)

/** Alles, was die Monatsansicht der Haushaltskasse anzeigt – vollständig aus Rohdaten abgeleitet. */
data class LedgerMonthSnapshot(
    val month: YearMonth,
    val currency: CurrencyCode,
    val summary: MonthSummary,
    val balances: List<AccountBalance>,
    val totalBalance: Money,
    val days: List<TimelineDay>,
    val expenseLines: List<CategoryLine>,
    val incomeLines: List<CategoryLine>,
    val accounts: List<Account>,
    val categories: List<Category>,
    val budgets: List<Budget>,
    val containsSampleData: Boolean,
)

object LedgerSnapshotBuilder {

    fun build(
        month: YearMonth,
        accounts: List<Account>,
        categories: List<Category>,
        budgets: List<Budget>,
        allTransactions: List<LedgerTransaction>,
    ): LedgerMonthSnapshot {
        val currency = accounts.minByOrNull { it.sortOrder }?.currency ?: CurrencyCode.EUR
        val accountById = accounts.associateBy { it.id }
        val categoryById = categories.associateBy { it.id }

        val balancesById = LedgerMath.balances(accounts, allTransactions)
        val balances = accounts
            .sortedBy { it.sortOrder }
            .map { AccountBalance(it, balancesById.getValue(it.id)) }

        val monthTx = allTransactions.filter { YearMonth.from(it.bookingDate) == month }
        val summary = LedgerMath.monthSummary(monthTx, month, currency)

        val days = monthTx
            .groupBy { it.bookingDate }
            .toSortedMap(compareByDescending { it })
            .map { (date, list) ->
                val entries = list.map { tx -> entry(tx, accountById, categoryById) }
                val net = list.fold(0L) { acc, tx ->
                    when (tx.kind) {
                        TransactionKind.INCOME -> Math.addExact(acc, tx.amount.minor)
                        TransactionKind.EXPENSE -> Math.subtractExact(acc, tx.amount.minor)
                        TransactionKind.TRANSFER -> acc
                    }
                }
                TimelineDay(date, entries, net)
            }

        val expenseTotals = LedgerMath.categoryTotals(monthTx, month, TransactionKind.EXPENSE, currency)
        val incomeTotals = LedgerMath.categoryTotals(monthTx, month, TransactionKind.INCOME, currency)
        val budgetStatus = LedgerMath.budgetStatus(budgets, expenseTotals, month, currency)
            .associateBy { it.categoryId }

        val expenseLines = categories
            .filter { it.direction == CategoryDirection.EXPENSE }
            .filter { (expenseTotals[it.id] ?: 0L) > 0L || budgetStatus.containsKey(it.id) }
            .map { c ->
                val total = expenseTotals[c.id] ?: 0L
                CategoryLine(c, total, LedgerMath.permille(total, summary.expenseMinor), budgetStatus[c.id])
            }
            .sortedWith(compareByDescending<CategoryLine> { it.totalMinor }.thenBy { it.category.sortOrder })

        val incomeLines = categories
            .filter { it.direction == CategoryDirection.INCOME && (incomeTotals[it.id] ?: 0L) > 0L }
            .map { c ->
                val total = incomeTotals.getValue(c.id)
                CategoryLine(c, total, LedgerMath.permille(total, summary.incomeMinor), null)
            }
            .sortedByDescending { it.totalMinor }

        return LedgerMonthSnapshot(
            month = month,
            currency = currency,
            summary = summary,
            balances = balances,
            totalBalance = LedgerMath.totalBalance(balancesById, currency),
            days = days,
            expenseLines = expenseLines,
            incomeLines = incomeLines,
            accounts = accounts.sortedBy { it.sortOrder },
            categories = categories.sortedBy { it.sortOrder },
            budgets = budgets,
            containsSampleData = allTransactions.any { it.isSample },
        )
    }

    private fun entry(
        tx: LedgerTransaction,
        accounts: Map<NodeId, Account>,
        categories: Map<NodeId, Category>,
    ): TimelineEntry {
        val categoryNames = tx.splits.mapNotNull { categories[it.categoryId]?.name }
        val accountName = accounts[tx.accountId]?.name ?: "?"
        val counterName = tx.counterAccountId?.let { accounts[it]?.name ?: "?" }
        val title = tx.payee
            ?: when (tx.kind) {
                TransactionKind.TRANSFER -> "Umbuchung"
                else -> categoryNames.firstOrNull() ?: "Buchung"
            }
        val direction = when (tx.kind) {
            TransactionKind.INCOME -> FlowDirection.IN
            TransactionKind.EXPENSE -> FlowDirection.OUT
            TransactionKind.TRANSFER -> FlowDirection.MOVE
        }
        return TimelineEntry(tx, title, categoryNames, accountName, counterName, direction)
    }
}

class ObserveLedgerMonth(private val repository: LedgerRepository) {
    operator fun invoke(month: YearMonth): Flow<LedgerMonthSnapshot> = combine(
        repository.observeAccounts(),
        repository.observeCategories(),
        repository.observeBudgets(),
        repository.observeAllTransactions(),
    ) { accounts, categories, budgets, transactions ->
        LedgerSnapshotBuilder.build(month, accounts, categories, budgets, transactions)
    }
}
