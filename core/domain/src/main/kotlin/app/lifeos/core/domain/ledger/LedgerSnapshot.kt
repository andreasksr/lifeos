package app.lifeos.core.domain.ledger

import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.ports.LedgerRepository
import app.lifeos.core.model.Account
import app.lifeos.core.model.Budget
import app.lifeos.core.model.Category
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.RecurringBooking
import app.lifeos.core.model.RecurringPayment
import app.lifeos.core.model.TotalBudget
import app.lifeos.core.model.TransactionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth

enum class FlowDirection { IN, OUT, MOVE, CORRECTION }

data class AccountBalance(val account: Account, val balance: Money)

data class TimelineEntry(
    val transaction: LedgerTransaction,
    val title: String,
    val categoryNames: List<String>,
    val accountName: String,
    val counterAccountName: String?,
    val direction: FlowDirection,
    /** Name des wiederkehrenden Plans, aus dem die Buchung stammt. */
    val recurringName: String? = null,
)

data class TimelineDay(
    val date: LocalDate,
    val entries: List<TimelineEntry>,
    /** Einnahmen − Netto-Ausgaben dieses Tages (Umbuchungen und Korrekturen neutral). */
    val netMinor: Long,
)

data class CategoryLine(
    val category: Category,
    val totalMinor: Long,
    /** Anteil an allen Ausgaben bzw. Einnahmen des Monats in Promille. */
    val sharePermille: Long,
    val budget: BudgetStatus?,
)

/**
 * Klare Trennung der drei Geld-Begriffe (Auftrag 4.1):
 *  - [bookedBalance]: tatsächlicher Kontostand aus allen gebuchten Buchungen (bis heute inkl. künftig datierter),
 *  - Budget übrig: siehe [LedgerMonthSnapshot.totalBudget],
 *  - [expectedAtMonthEnd]: Kontostand + offene geplante Einnahmen − offene geplante Ausgaben bis Monatsende.
 */
data class Forecast(
    val bookedBalance: Money,
    val openIncomeMinor: Long,
    val openExpenseMinor: Long,
    val expectedAtMonthEnd: Money,
    val until: LocalDate,
)

/** Alles, was die Finanzansichten anzeigen – vollständig aus Rohdaten abgeleitet. */
data class LedgerMonthSnapshot(
    val month: YearMonth,
    val today: LocalDate,
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
    val totalBudgets: List<TotalBudget>,
    val totalBudget: TotalBudgetStatus?,
    val recurring: List<RecurringPayment>,
    /** Überfällige und bis 30 Tage nach heute fällige, noch nicht gebuchte Zahlungen. */
    val pending: List<Occurrence>,
    val forecast: Forecast?,
    /** Monatsübersichten der letzten 6 Monate bis einschließlich [month], älteste zuerst. */
    val history: List<MonthSummary>,
    /** Kumulierte Netto-Ausgaben dieses Monats je Tag. */
    val cumulativeExpenses: List<DayValue>,
    /** Linearer Soll-Verlauf des Gesamtbudgets (falls gesetzt). */
    val budgetPace: List<DayValue>?,
    /** Summe der Monatsäquivalente aktiver Fixkosten-Pläne (Ausgaben). */
    val fixedMonthlyPlanMinor: Long,
    val containsSampleData: Boolean,
) {
    val isCurrentMonth: Boolean get() = YearMonth.from(today) == month
}

object LedgerSnapshotBuilder {

    const val HISTORY_MONTHS = 6
    const val PENDING_HORIZON_DAYS = 30L

    fun build(
        month: YearMonth,
        today: LocalDate,
        accounts: List<Account>,
        categories: List<Category>,
        budgets: List<Budget>,
        allTransactions: List<LedgerTransaction>,
        totalBudgets: List<TotalBudget> = emptyList(),
        recurring: List<RecurringPayment> = emptyList(),
        bookings: List<RecurringBooking> = emptyList(),
    ): LedgerMonthSnapshot {
        val currency = accounts.minByOrNull { it.sortOrder }?.currency ?: CurrencyCode.EUR
        val accountById = accounts.associateBy { it.id }
        val categoryById = categories.associateBy { it.id }
        val recurringById = recurring.associateBy { it.id }
        val fixedIds = recurring.filter { it.isFixedCost }.map { it.id }.toSet()

        val balancesById = LedgerMath.balances(accounts, allTransactions)
        val balances = accounts
            .sortedBy { it.sortOrder }
            .map { AccountBalance(it, balancesById.getValue(it.id)) }
        val totalBalance = LedgerMath.totalBalance(balancesById, currency)

        val monthTx = allTransactions.filter { YearMonth.from(it.bookingDate) == month }
        val summary = LedgerMath.monthSummary(monthTx, month, currency, fixedIds)

        val days = monthTx
            .groupBy { it.bookingDate }
            .toSortedMap(compareByDescending { it })
            .map { (date, list) ->
                val entries = list.map { tx -> entry(tx, accountById, categoryById, recurringById) }
                val net = list.fold(0L) { acc, tx ->
                    when (tx.kind) {
                        TransactionKind.INCOME, TransactionKind.REFUND -> Math.addExact(acc, tx.amount.minor)
                        TransactionKind.EXPENSE -> Math.subtractExact(acc, tx.amount.minor)
                        else -> acc
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
            .filter { (expenseTotals[it.id] ?: 0L) != 0L || budgetStatus.containsKey(it.id) }
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

        val totalBudget = LedgerMath.effectiveTotalBudget(totalBudgets, month, currency)
        val totalStatus = LedgerMath.totalBudgetStatus(totalBudget, summary.expenseMinor)

        val pending = RecurringSchedule.pending(recurring, bookings, today, today.plusDays(PENDING_HORIZON_DAYS))

        val forecast = if (YearMonth.from(today) == month) {
            val monthEnd = month.atEndOfMonth()
            val open = pending.filter { !it.dueDate.isAfter(monthEnd) && it.payment.amount.currency == currency }
            val openIn = open.filter { it.payment.kind == TransactionKind.INCOME }.sumOf { it.payment.amount.minor }
            val openOut = open.filter { it.payment.kind == TransactionKind.EXPENSE }.sumOf { it.payment.amount.minor }
            Forecast(
                bookedBalance = totalBalance,
                openIncomeMinor = openIn,
                openExpenseMinor = openOut,
                expectedAtMonthEnd = Money(Math.subtractExact(Math.addExact(totalBalance.minor, openIn), openOut), currency),
                until = monthEnd,
            )
        } else {
            null
        }

        val history = (HISTORY_MONTHS - 1 downTo 0).map { back ->
            val m = month.minusMonths(back.toLong())
            LedgerMath.monthSummary(allTransactions, m, currency, fixedIds)
        }

        val cumulative = LedgerMath.cumulativeExpenses(
            monthTx, month, currency, until = if (YearMonth.from(today) == month) today else null,
        )
        val pace = totalBudget?.let { LedgerMath.budgetPace(it.amountMinor, month) }

        val fixedPlan = recurring
            .filter { it.active && it.isFixedCost && it.kind == TransactionKind.EXPENSE && it.amount.currency == currency }
            .sumOf { RecurringSchedule.monthlyEquivalentMinor(it) }

        return LedgerMonthSnapshot(
            month = month,
            today = today,
            currency = currency,
            summary = summary,
            balances = balances,
            totalBalance = totalBalance,
            days = days,
            expenseLines = expenseLines,
            incomeLines = incomeLines,
            accounts = accounts.sortedBy { it.sortOrder },
            categories = categories.sortedBy { it.sortOrder },
            budgets = budgets,
            totalBudgets = totalBudgets,
            totalBudget = totalStatus,
            recurring = recurring.sortedBy { it.name.lowercase() },
            pending = pending,
            forecast = forecast,
            history = history,
            cumulativeExpenses = cumulative,
            budgetPace = pace,
            fixedMonthlyPlanMinor = fixedPlan,
            containsSampleData = allTransactions.any { it.isSample },
        )
    }

    fun entry(
        tx: LedgerTransaction,
        accounts: Map<NodeId, Account>,
        categories: Map<NodeId, Category>,
        recurring: Map<NodeId, RecurringPayment> = emptyMap(),
    ): TimelineEntry {
        val categoryNames = tx.splits.mapNotNull { categories[it.categoryId]?.name }
        val accountName = accounts[tx.accountId]?.name ?: "?"
        val counterName = tx.counterAccountId?.let { accounts[it]?.name ?: "?" }
        val title = tx.payee
            ?: when (tx.kind) {
                TransactionKind.TRANSFER -> "Umbuchung"
                TransactionKind.CORRECTION_IN, TransactionKind.CORRECTION_OUT -> "Kontostand-Korrektur"
                TransactionKind.REFUND -> "Erstattung" + (categoryNames.firstOrNull()?.let { " · $it" } ?: "")
                else -> categoryNames.firstOrNull() ?: "Buchung"
            }
        val direction = when (tx.kind) {
            TransactionKind.INCOME, TransactionKind.REFUND -> FlowDirection.IN
            TransactionKind.EXPENSE -> FlowDirection.OUT
            TransactionKind.TRANSFER -> FlowDirection.MOVE
            TransactionKind.CORRECTION_IN, TransactionKind.CORRECTION_OUT -> FlowDirection.CORRECTION
        }
        return TimelineEntry(tx, title, categoryNames, accountName, counterName, direction, tx.recurringId?.let { recurring[it]?.name })
    }
}

class ObserveLedgerMonth(
    private val repository: LedgerRepository,
    private val clock: LifeClock,
) {
    operator fun invoke(month: YearMonth): Flow<LedgerMonthSnapshot> {
        val base = combine(
            repository.observeAccounts(),
            repository.observeCategories(),
            repository.observeBudgets(),
            repository.observeAllTransactions(),
        ) { accounts, categories, budgets, transactions -> Base(accounts, categories, budgets, transactions) }
        val plans = combine(
            repository.observeTotalBudgets(),
            repository.observeRecurring(),
            repository.observeRecurringBookings(),
        ) { total, recurring, bookings -> Plans(total, recurring, bookings) }
        return combine(base, plans) { b, p ->
            LedgerSnapshotBuilder.build(
                month = month,
                today = clock.today(),
                accounts = b.accounts,
                categories = b.categories,
                budgets = b.budgets,
                allTransactions = b.transactions,
                totalBudgets = p.totalBudgets,
                recurring = p.recurring,
                bookings = p.bookings,
            )
        }
    }

    private data class Base(
        val accounts: List<Account>,
        val categories: List<Category>,
        val budgets: List<Budget>,
        val transactions: List<LedgerTransaction>,
    )

    private data class Plans(
        val totalBudgets: List<TotalBudget>,
        val recurring: List<RecurringPayment>,
        val bookings: List<RecurringBooking>,
    )
}
