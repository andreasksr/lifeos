package app.lifeos.core.domain.ledger

import app.lifeos.core.domain.testing.id
import app.lifeos.core.model.Account
import app.lifeos.core.model.AccountKind
import app.lifeos.core.model.Category
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.Money
import app.lifeos.core.model.Split
import app.lifeos.core.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransactionValidatorTest {

    private val giro = Account(id(1), "Giro", AccountKind.CHECKING, CurrencyCode.EUR, 0, 0)
    private val savings = Account(id(2), "Spar", AccountKind.SAVINGS, CurrencyCode.EUR, 0, 1)
    private val archived = Account(id(3), "Alt", AccountKind.CASH, CurrencyCode.EUR, 0, 2, archived = true)
    private val usd = Account(id(4), "USD", AccountKind.CHECKING, CurrencyCode("USD"), 0, 3)
    private val food = Category(id(10), "Lebensmittel", CategoryDirection.EXPENSE)
    private val car = Category(id(11), "Fahrzeug", CategoryDirection.EXPENSE)
    private val salary = Category(id(12), "Gehalt", CategoryDirection.INCOME)

    private val accounts = listOf(giro, savings, archived, usd).associateBy { it.id }
    private val categories = listOf(food, car, salary).associateBy { it.id }

    private fun expense(amount: Long, vararg splits: Pair<Category, Long>) = LedgerTransaction(
        id = id(100), kind = TransactionKind.EXPENSE, accountId = giro.id, counterAccountId = null,
        amount = Money.eur(amount), bookingDate = LocalDate.of(2026, 10, 1), payee = null, note = null,
        splits = splits.mapIndexed { i, (c, a) -> Split(id(200 + i), c.id, a) },
    )

    private fun validate(tx: LedgerTransaction) = TransactionValidator.validate(tx, accounts, categories)

    @Test
    fun `gueltige ausgabe mit einer und mehreren kategorien`() {
        assertEquals(emptyList<LedgerError>(), validate(expense(8990, food to 8990)))
        assertEquals(emptyList<LedgerError>(), validate(expense(10000, food to 3333, car to 6667)))
    }

    @Test
    fun `betrag muss positiv sein`() {
        assertTrue(LedgerError.AmountNotPositive in validate(expense(0, food to 0)))
        assertTrue(LedgerError.AmountNotPositive in validate(expense(-5, food to 5)))
    }

    @Test
    fun `summe der aufteilungen muss exakt dem betrag entsprechen`() {
        assertEquals(
            listOf(LedgerError.SplitSumMismatch(10000, 9999)),
            validate(expense(10000, food to 3333, car to 6666)),
        )
    }

    @Test
    fun `ausgabe braucht mindestens eine aufteilung`() {
        assertEquals(listOf(LedgerError.MissingSplits), validate(expense(100)))
    }

    @Test
    fun `kategorie muss zur richtung passen`() {
        assertEquals(
            listOf(LedgerError.CategoryDirectionMismatch(salary.id)),
            validate(expense(100, salary to 100)),
        )
    }

    @Test
    fun `unbekannte und archivierte konten`() {
        assertTrue(validate(expense(100, food to 100).copy(accountId = id(99))).contains(LedgerError.UnknownAccount(id(99))))
        assertTrue(validate(expense(100, food to 100).copy(accountId = archived.id)).contains(LedgerError.ArchivedAccount(archived.id)))
    }

    @Test
    fun `buchungswaehrung muss kontowaehrung sein`() {
        assertTrue(LedgerError.CurrencyMismatch in validate(expense(100, food to 100).copy(accountId = usd.id)))
    }

    @Test
    fun `umbuchung regeln`() {
        val transfer = LedgerTransaction(
            id(101), TransactionKind.TRANSFER, giro.id, savings.id, Money.eur(5000),
            LocalDate.of(2026, 10, 2), null, null, emptyList(),
        )
        assertEquals(emptyList<LedgerError>(), validate(transfer))
        assertEquals(listOf(LedgerError.TransferNeedsCounterAccount), validate(transfer.copy(counterAccountId = null)))
        assertEquals(listOf(LedgerError.TransferSameAccount), validate(transfer.copy(counterAccountId = giro.id)))
        assertEquals(
            listOf(LedgerError.SplitsOnTransfer),
            validate(transfer.copy(splits = listOf(Split(id(300), food.id, 5000)))),
        )
        assertEquals(listOf(LedgerError.CurrencyMismatch), validate(transfer.copy(counterAccountId = usd.id)))
    }

    @Test
    fun `gegenkonto nur bei umbuchung`() {
        assertEquals(
            listOf(LedgerError.CounterAccountOnNonTransfer),
            validate(expense(100, food to 100).copy(counterAccountId = savings.id)),
        )
    }

    @Test
    fun `obergrenze und ueberlauf in splits`() {
        assertTrue(LedgerError.AmountOutOfRange in validate(expense(TransactionValidator.MAX_AMOUNT_MINOR + 1, food to TransactionValidator.MAX_AMOUNT_MINOR + 1)))
        assertTrue(LedgerError.AmountOutOfRange in validate(expense(100, food to Long.MAX_VALUE, car to 1)))
    }
}
