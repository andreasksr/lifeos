package app.lifeos.core.domain.ledger

import app.lifeos.core.model.Account
import app.lifeos.core.model.Budget
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.TotalBudget
import app.lifeos.core.model.TransactionKind
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

data class MonthSummary(
    val month: YearMonth,
    val currency: CurrencyCode,
    val incomeMinor: Long,
    /** Netto-Ausgaben: Ausgaben − Erstattungen. Grundlage für Budgets und Statistik. */
    val expenseMinor: Long,
    val grossExpenseMinor: Long,
    val refundMinor: Long,
    /** Überschuss (positiv) oder Defizit (negativ): Einnahmen − Netto-Ausgaben. */
    val netMinor: Long,
    /** Netto-Ausgaben, die aus Fixkosten-Plänen gebucht wurden. */
    val fixedExpenseMinor: Long,
    val transactionCount: Int,
) {
    val variableExpenseMinor: Long get() = expenseMinor - fixedExpenseMinor
}

data class BudgetStatus(
    val categoryId: NodeId,
    val limitMinor: Long,
    val spentMinor: Long,
    val remainingMinor: Long,
    /** Auslastung in Promille, abgerundet (1000 = 100 %). Ganzzahlig – keine Rundungsdrift. */
    val usedPermille: Long,
) {
    val isExceeded: Boolean get() = spentMinor > limitMinor
}

data class TotalBudgetStatus(
    val limitMinor: Long,
    val spentMinor: Long,
    val remainingMinor: Long,
    val usedPermille: Long,
) {
    val isExceeded: Boolean get() = spentMinor > limitMinor
}

/** Ein Punkt einer Verlaufskurve, exakt in Minor Units. */
data class DayValue(val date: LocalDate, val valueMinor: Long)

/**
 * Alle Finanzberechnungen. Reine Funktionen über unveränderlichen Daten – jede Zahl in der
 * Oberfläche ist hieraus reproduzierbar (ADR-002: nichts davon wird gespeichert).
 */
object LedgerMath {

    /** Wirkung einer Buchung auf den Saldo eines Kontos in Minor Units. */
    fun effectOn(accountId: NodeId, tx: LedgerTransaction): Long = when (tx.kind) {
        TransactionKind.EXPENSE, TransactionKind.CORRECTION_OUT ->
            if (tx.accountId == accountId) -tx.amount.minor else 0L
        TransactionKind.INCOME, TransactionKind.REFUND, TransactionKind.CORRECTION_IN ->
            if (tx.accountId == accountId) tx.amount.minor else 0L
        TransactionKind.TRANSFER -> when (accountId) {
            tx.accountId -> -tx.amount.minor
            tx.counterAccountId -> tx.amount.minor
            else -> 0L
        }
    }

    /** Saldo je Konto = Anfangsbestand + Summe aller Wirkungen bis einschließlich [until] (null = alle). */
    fun balances(
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
        until: LocalDate? = null,
    ): Map<NodeId, Money> =
        accounts.associate { account ->
            val total = transactions.fold(account.openingBalanceMinor) { acc, tx ->
                if (until != null && tx.bookingDate.isAfter(until)) acc
                else Math.addExact(acc, effectOn(account.id, tx))
            }
            account.id to Money(total, account.currency)
        }

    /**
     * Summe aller Salden einer Währung. Archivierte Konten zählen mit, solange sie Geld halten –
     * Archivieren versteckt ein Konto, es lässt kein Geld verschwinden.
     */
    fun totalBalance(balances: Map<NodeId, Money>, currency: CurrencyCode): Money =
        balances.values.filter { it.currency == currency }
            .fold(Money.zero(currency)) { acc, m -> acc + m }

    /**
     * Einnahmen/Ausgaben eines Monats. Umbuchungen und Korrekturen zählen nicht;
     * Erstattungen mindern die Ausgaben.
     * @param fixedRecurringIds Pläne, die als Fixkosten gelten.
     */
    fun monthSummary(
        transactions: List<LedgerTransaction>,
        month: YearMonth,
        currency: CurrencyCode,
        fixedRecurringIds: Set<NodeId> = emptySet(),
    ): MonthSummary {
        var income = 0L
        var expense = 0L
        var refund = 0L
        var fixed = 0L
        var count = 0
        transactions.asSequence()
            .filter { YearMonth.from(it.bookingDate) == month && it.amount.currency == currency }
            .forEach { tx ->
                count++
                when (tx.kind) {
                    TransactionKind.INCOME -> income = Math.addExact(income, tx.amount.minor)
                    TransactionKind.EXPENSE -> {
                        expense = Math.addExact(expense, tx.amount.minor)
                        if (tx.recurringId?.let { it in fixedRecurringIds } == true) {
                            fixed = Math.addExact(fixed, tx.amount.minor)
                        }
                    }
                    TransactionKind.REFUND -> refund = Math.addExact(refund, tx.amount.minor)
                    TransactionKind.TRANSFER, TransactionKind.CORRECTION_IN, TransactionKind.CORRECTION_OUT -> Unit
                }
            }
        val net = Math.subtractExact(expense, refund)
        return MonthSummary(
            month = month,
            currency = currency,
            incomeMinor = income,
            expenseMinor = net,
            grossExpenseMinor = expense,
            refundMinor = refund,
            netMinor = Math.subtractExact(income, net),
            fixedExpenseMinor = fixed,
            transactionCount = count,
        )
    }

    /**
     * Netto-Summe je Kategorie (über Splits) für einen Monat.
     * [kind] = EXPENSE: Ausgaben-Splits positiv, Erstattungs-Splits negativ. [kind] = INCOME: Einnahmen.
     */
    fun categoryTotals(
        transactions: List<LedgerTransaction>,
        month: YearMonth,
        kind: TransactionKind,
        currency: CurrencyCode,
    ): Map<NodeId, Long> {
        require(kind == TransactionKind.EXPENSE || kind == TransactionKind.INCOME) {
            "Kategoriesummen gibt es nur für Ausgaben und Einnahmen"
        }
        val totals = linkedMapOf<NodeId, Long>()
        transactions.asSequence()
            .filter { it.amount.currency == currency && YearMonth.from(it.bookingDate) == month }
            .forEach { tx ->
                val sign = when {
                    tx.kind == kind -> 1L
                    kind == TransactionKind.EXPENSE && tx.kind == TransactionKind.REFUND -> -1L
                    else -> 0L
                }
                if (sign != 0L) tx.splits.forEach { split ->
                    totals[split.categoryId] =
                        Math.addExact(totals[split.categoryId] ?: 0L, sign * split.amountMinor)
                }
            }
        return totals
    }

    /** Für jede Kategorie das im Monat gültige Budget (jüngstes `validFrom ≤ month`). */
    fun effectiveBudgets(budgets: List<Budget>, month: YearMonth, currency: CurrencyCode): Map<NodeId, Budget> =
        budgets.asSequence()
            .filter { it.currency == currency && !it.validFrom.isAfter(month) }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.maxBy { it.validFrom } }

    fun effectiveTotalBudget(budgets: List<TotalBudget>, month: YearMonth, currency: CurrencyCode): TotalBudget? =
        budgets.filter { it.currency == currency && !it.validFrom.isAfter(month) }.maxByOrNull { it.validFrom }

    fun budgetStatus(
        budgets: List<Budget>,
        expenseTotals: Map<NodeId, Long>,
        month: YearMonth,
        currency: CurrencyCode,
    ): List<BudgetStatus> = effectiveBudgets(budgets, month, currency).values
        .map { budget ->
            val spent = expenseTotals[budget.categoryId] ?: 0L
            BudgetStatus(
                categoryId = budget.categoryId,
                limitMinor = budget.amountMinor,
                spentMinor = spent,
                remainingMinor = Math.subtractExact(budget.amountMinor, spent),
                usedPermille = permille(spent, budget.amountMinor),
            )
        }
        .sortedByDescending { it.usedPermille }

    fun totalBudgetStatus(budget: TotalBudget?, netExpenseMinor: Long): TotalBudgetStatus? = budget?.let {
        TotalBudgetStatus(
            limitMinor = it.amountMinor,
            spentMinor = netExpenseMinor,
            remainingMinor = Math.subtractExact(it.amountMinor, netExpenseMinor),
            usedPermille = permille(netExpenseMinor, it.amountMinor),
        )
    }

    /**
     * Kumulierte Netto-Ausgaben je Tag eines Monats (1. … letzter Tag bzw. bis [until]).
     * Grundlage der Kurve „Budgetentwicklung".
     */
    fun cumulativeExpenses(
        transactions: List<LedgerTransaction>,
        month: YearMonth,
        currency: CurrencyCode,
        until: LocalDate? = null,
    ): List<DayValue> {
        val perDay = LongArray(month.lengthOfMonth())
        transactions.forEach { tx ->
            if (tx.amount.currency != currency || YearMonth.from(tx.bookingDate) != month) return@forEach
            val i = tx.bookingDate.dayOfMonth - 1
            when (tx.kind) {
                TransactionKind.EXPENSE -> perDay[i] = Math.addExact(perDay[i], tx.amount.minor)
                TransactionKind.REFUND -> perDay[i] = Math.subtractExact(perDay[i], tx.amount.minor)
                else -> Unit
            }
        }
        val last = if (until != null && YearMonth.from(until) == month) until.dayOfMonth else month.lengthOfMonth()
        var running = 0L
        return (1..last).map { day ->
            running = Math.addExact(running, perDay[day - 1])
            DayValue(month.atDay(day), running)
        }
    }

    /** Linearer „Soll-Verlauf" eines Budgets: Anteil des Budgets bis Tag d (abgerundet, exakt). */
    fun budgetPace(limitMinor: Long, month: YearMonth): List<DayValue> {
        val days = month.lengthOfMonth().toLong()
        return (1..month.lengthOfMonth()).map { d ->
            DayValue(month.atDay(d), BigInteger.valueOf(limitMinor).multiply(BigInteger.valueOf(d.toLong())).divide(BigInteger.valueOf(days)).toLong())
        }
    }

    /** Anteil `part / whole` in Promille, abgerundet; `whole ≤ 0` ergibt 0. */
    fun permille(part: Long, whole: Long): Long {
        if (whole <= 0L || part <= 0L) return 0L
        // part * 1000 kann bei sehr großen Beträgen überlaufen → exakt über BigInteger.
        val result = BigInteger.valueOf(part)
            .multiply(THOUSAND)
            .divide(BigInteger.valueOf(whole))
        return if (result.bitLength() < 64) result.toLong() else Long.MAX_VALUE
    }

    private val THOUSAND: BigInteger = BigInteger.valueOf(1000L)
}
