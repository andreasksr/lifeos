package app.lifeos.core.domain.ledger

import app.lifeos.core.model.Account
import app.lifeos.core.model.Budget
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.TransactionKind
import java.math.BigInteger
import java.time.YearMonth

data class MonthSummary(
    val month: YearMonth,
    val currency: CurrencyCode,
    val incomeMinor: Long,
    val expenseMinor: Long,
    /** Überschuss (positiv) oder Defizit (negativ): Einnahmen − Ausgaben. */
    val netMinor: Long,
    val transactionCount: Int,
)

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

/**
 * Alle Finanzberechnungen. Reine Funktionen über unveränderlichen Daten – jede Zahl in der
 * Oberfläche ist hieraus reproduzierbar (ADR-002: nichts davon wird gespeichert).
 */
object LedgerMath {

    /** Wirkung einer Buchung auf den Saldo eines Kontos in Minor Units. */
    fun effectOn(accountId: NodeId, tx: LedgerTransaction): Long = when (tx.kind) {
        TransactionKind.EXPENSE -> if (tx.accountId == accountId) -tx.amount.minor else 0L
        TransactionKind.INCOME -> if (tx.accountId == accountId) tx.amount.minor else 0L
        TransactionKind.TRANSFER -> when (accountId) {
            tx.accountId -> -tx.amount.minor
            tx.counterAccountId -> tx.amount.minor
            else -> 0L
        }
    }

    /** Aktueller Saldo je Konto = Anfangsbestand + Summe aller Wirkungen. */
    fun balances(accounts: List<Account>, transactions: List<LedgerTransaction>): Map<NodeId, Money> =
        accounts.associate { account ->
            val total = transactions.fold(account.openingBalanceMinor) { acc, tx ->
                Math.addExact(acc, effectOn(account.id, tx))
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

    /** Einnahmen/Ausgaben eines Monats. Umbuchungen zählen nicht. */
    fun monthSummary(
        transactions: List<LedgerTransaction>,
        month: YearMonth,
        currency: CurrencyCode,
    ): MonthSummary {
        var income = 0L
        var expense = 0L
        var count = 0
        transactions.asSequence()
            .filter { YearMonth.from(it.bookingDate) == month && it.amount.currency == currency }
            .forEach { tx ->
                count++
                when (tx.kind) {
                    TransactionKind.INCOME -> income = Math.addExact(income, tx.amount.minor)
                    TransactionKind.EXPENSE -> expense = Math.addExact(expense, tx.amount.minor)
                    TransactionKind.TRANSFER -> Unit
                }
            }
        return MonthSummary(month, currency, income, expense, Math.subtractExact(income, expense), count)
    }

    /** Summe je Kategorie (über Splits) für einen Monat und eine Buchungsart. */
    fun categoryTotals(
        transactions: List<LedgerTransaction>,
        month: YearMonth,
        kind: TransactionKind,
        currency: CurrencyCode,
    ): Map<NodeId, Long> {
        require(kind != TransactionKind.TRANSFER) { "Umbuchungen haben keine Kategorien" }
        val totals = linkedMapOf<NodeId, Long>()
        transactions.asSequence()
            .filter { it.kind == kind && it.amount.currency == currency && YearMonth.from(it.bookingDate) == month }
            .flatMap { it.splits.asSequence() }
            .forEach { split ->
                totals[split.categoryId] = Math.addExact(totals[split.categoryId] ?: 0L, split.amountMinor)
            }
        return totals
    }

    /** Für jede Kategorie das im Monat gültige Budget (jüngstes `validFrom ≤ month`). */
    fun effectiveBudgets(budgets: List<Budget>, month: YearMonth, currency: CurrencyCode): Map<NodeId, Budget> =
        budgets.asSequence()
            .filter { it.currency == currency && !it.validFrom.isAfter(month) }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.maxBy { it.validFrom } }

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
