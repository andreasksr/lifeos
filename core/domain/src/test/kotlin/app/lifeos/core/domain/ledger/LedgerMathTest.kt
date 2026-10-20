package app.lifeos.core.domain.ledger

import app.lifeos.core.domain.testing.id
import app.lifeos.core.model.Account
import app.lifeos.core.model.AccountKind
import app.lifeos.core.model.Budget
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.Split
import app.lifeos.core.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerMathTest {

    private val giro = Account(id(1), "Giro", AccountKind.CHECKING, CurrencyCode.EUR, 100_000, 0)
    private val savings = Account(id(2), "Spar", AccountKind.SAVINGS, CurrencyCode.EUR, 0, 1)
    private val food = id(10)
    private val car = id(11)
    private val salary = id(12)
    private var seq = 500

    private fun tx(
        kind: TransactionKind,
        amount: Long,
        date: String,
        account: NodeId = giro.id,
        counter: NodeId? = null,
        splits: List<Pair<NodeId, Long>> = emptyList(),
    ) = LedgerTransaction(
        id(seq++), kind, account, counter, Money.eur(amount), LocalDate.parse(date), null, null,
        splits.map { (c, a) -> Split(id(seq++), c, a) },
    )

    private val october = listOf(
        tx(TransactionKind.INCOME, 320_000, "2026-10-01", splits = listOf(salary to 320_000)),
        tx(TransactionKind.EXPENSE, 8_990, "2026-10-03", splits = listOf(car to 8_990)),
        tx(TransactionKind.EXPENSE, 12_345, "2026-10-04", splits = listOf(food to 10_000, car to 2_345)),
        tx(TransactionKind.TRANSFER, 50_000, "2026-10-05", counter = savings.id),
        tx(TransactionKind.EXPENSE, 4_000, "2026-09-30", splits = listOf(food to 4_000)),
    )

    @Test
    fun `salden aus anfangsbestand und allen buchungen`() {
        val balances = LedgerMath.balances(listOf(giro, savings), october)
        // 1000,00 + 3200,00 − 89,90 − 123,45 − 500,00 − 40,00 = 3446,65
        assertEquals(Money.eur(344_665), balances[giro.id])
        assertEquals(Money.eur(50_000), balances[savings.id])
        // Umbuchung ändert die Gesamtsumme nicht
        assertEquals(Money.eur(394_665), LedgerMath.totalBalance(balances, CurrencyCode.EUR))
    }

    @Test
    fun `monatsuebersicht ignoriert umbuchungen und andere monate`() {
        val s = LedgerMath.monthSummary(october, YearMonth.of(2026, 10), CurrencyCode.EUR)
        assertEquals(320_000, s.incomeMinor)
        assertEquals(21_335, s.expenseMinor)
        assertEquals(298_665, s.netMinor)
        assertEquals(4, s.transactionCount)
    }

    @Test
    fun `kategoriesummen ueber splits`() {
        val totals = LedgerMath.categoryTotals(october, YearMonth.of(2026, 10), TransactionKind.EXPENSE, CurrencyCode.EUR)
        assertEquals(mapOf(car to 11_335L, food to 10_000L), totals)
        // Summe der Kategorien == Summe der Ausgaben: keine Doppelzählung
        assertEquals(21_335L, totals.values.sum())
    }

    @Test
    fun `gueltiges budget ist das juengste bis zum monat`() {
        val budgets = listOf(
            Budget(id(900), food, YearMonth.of(2026, 1), 30_000, CurrencyCode.EUR),
            Budget(id(901), food, YearMonth.of(2026, 9), 25_000, CurrencyCode.EUR),
            Budget(id(902), food, YearMonth.of(2026, 12), 40_000, CurrencyCode.EUR),
            Budget(id(903), car, YearMonth.of(2026, 10), 10_000, CurrencyCode.EUR),
        )
        val effective = LedgerMath.effectiveBudgets(budgets, YearMonth.of(2026, 10), CurrencyCode.EUR)
        assertEquals(25_000L, effective.getValue(food).amountMinor)

        val totals = LedgerMath.categoryTotals(october, YearMonth.of(2026, 10), TransactionKind.EXPENSE, CurrencyCode.EUR)
        val status = LedgerMath.budgetStatus(budgets, totals, YearMonth.of(2026, 10), CurrencyCode.EUR)
        val carStatus = status.first { it.categoryId == car }
        assertEquals(1_133L, carStatus.usedPermille)
        assertEquals(-1_335L, carStatus.remainingMinor)
        assertTrue(carStatus.isExceeded)
        assertEquals(car, status.first().categoryId) // sortiert nach Auslastung
    }

    @Test
    fun `promille ist ganzzahlig und robust`() {
        assertEquals(333L, LedgerMath.permille(1, 3))
        assertEquals(1000L, LedgerMath.permille(5, 5))
        assertEquals(0L, LedgerMath.permille(5, 0))
        assertEquals(0L, LedgerMath.permille(-5, 10))
        assertEquals(1000L, LedgerMath.permille(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, LedgerMath.permille(Long.MAX_VALUE, 1))
    }

    @Test
    fun `wirkung einer umbuchung auf beide konten`() {
        val t = tx(TransactionKind.TRANSFER, 700, "2026-10-01", counter = savings.id)
        assertEquals(-700L, LedgerMath.effectOn(giro.id, t))
        assertEquals(700L, LedgerMath.effectOn(savings.id, t))
        assertEquals(0L, LedgerMath.effectOn(id(77), t))
    }
}
