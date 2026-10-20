package app.lifeos.core.domain.ledger

import app.lifeos.core.domain.testing.FakeLedgerRepository
import app.lifeos.core.domain.testing.FakeSettingsRepository
import app.lifeos.core.domain.testing.FixedClock
import app.lifeos.core.domain.testing.SequentialIds
import app.lifeos.core.domain.testing.id
import app.lifeos.core.model.AccountKind
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerUseCasesTest {

    private lateinit var repo: FakeLedgerRepository
    private lateinit var settings: FakeSettingsRepository
    private val ids = SequentialIds()
    private val clock = FixedClock()

    private var account: NodeId = id(0)
    private var food: NodeId = id(0)
    private var salary: NodeId = id(0)

    @Before
    fun setUp() = runBlocking {
        repo = FakeLedgerRepository()
        settings = FakeSettingsRepository()
        EnsureLedgerDefaults(repo, settings, ids, clock)()
        account = repo.accounts().single().id
        food = repo.categories().first { it.name == "Lebensmittel" }.id
        salary = repo.categories().first { it.name == "Gehalt" }.id
    }

    @Test
    fun `standardwerte werden genau einmal angelegt`() = runBlocking {
        assertEquals(1, repo.accounts().size)
        assertEquals(
            EnsureLedgerDefaults.DEFAULT_EXPENSE.size + EnsureLedgerDefaults.DEFAULT_INCOME.size,
            repo.categories().size,
        )
        assertTrue(repo.transactions.value.isEmpty()) // keine erfundenen Buchungen
        assertFalse(EnsureLedgerDefaults(repo, settings, ids, clock)())
        assertEquals(1, repo.accounts().size)
    }

    @Test
    fun `buchung anlegen bereinigt texte und speichert genau eine buchung`() = runBlocking {
        val record = RecordTransaction(repo, ids, clock)
        val result = record(
            TransactionDraft.single(
                TransactionKind.EXPENSE, account, food, 4_299, LocalDate.of(2026, 10, 18),
                payee = "  Wochenmarkt ", note = "   ",
            ),
        )
        val txId = (result as LedgerResult.Success).value
        val stored = repo.transaction(txId)!!
        assertEquals("Wochenmarkt", stored.payee)
        assertNull(stored.note)
        assertEquals(Money.eur(4_299), stored.amount)
        assertEquals(1, repo.transactions.value.size)
    }

    @Test
    fun `ungueltige buchung wird nicht gespeichert`() = runBlocking {
        val result = RecordTransaction(repo, ids, clock)(
            TransactionDraft.single(TransactionKind.EXPENSE, account, salary, 100, LocalDate.of(2026, 10, 1)),
        )
        assertTrue(result is LedgerResult.Invalid)
        assertTrue(repo.transactions.value.isEmpty())
    }

    @Test
    fun `buchung bearbeiten ersetzt statt zu duplizieren`() = runBlocking {
        val record = RecordTransaction(repo, ids, clock)
        val txId = (record(TransactionDraft.single(TransactionKind.EXPENSE, account, food, 1_000, LocalDate.of(2026, 10, 1))) as LedgerResult.Success).value
        val edited = record(TransactionDraft.single(TransactionKind.EXPENSE, account, food, 1_500, LocalDate.of(2026, 10, 2), id = txId))
        assertEquals(LedgerResult.Success(txId), edited)
        assertEquals(1, repo.transactions.value.size)
        assertEquals(1_500L, repo.transaction(txId)!!.amount.minor)
        assertEquals(1_500L, repo.transaction(txId)!!.splits.single().amountMinor)
    }

    @Test
    fun `bearbeiten einer unbekannten buchung schlaegt fehl`() = runBlocking {
        val result = RecordTransaction(repo, ids, clock)(
            TransactionDraft.single(TransactionKind.EXPENSE, account, food, 1_000, LocalDate.of(2026, 10, 1), id = id(4242)),
        )
        assertEquals(LedgerResult.Invalid(listOf(LedgerError.TransactionNotFound)), result)
    }

    @Test
    fun `loeschen`() = runBlocking {
        val record = RecordTransaction(repo, ids, clock)
        val txId = (record(TransactionDraft.single(TransactionKind.EXPENSE, account, food, 1_000, LocalDate.of(2026, 10, 1))) as LedgerResult.Success).value
        assertEquals(LedgerResult.Success(Unit), DeleteTransaction(repo)(txId))
        assertTrue(repo.transactions.value.isEmpty())
        assertTrue(DeleteTransaction(repo)(txId) is LedgerResult.Invalid)
    }

    @Test
    fun `budget setzen und fuer denselben monat ersetzen`() = runBlocking {
        val setBudget = SetBudget(repo, ids)
        val month = YearMonth.of(2026, 10)
        val first = (setBudget(food, month, 40_000, repo.budgets.value) as LedgerResult.Success).value
        val second = (setBudget(food, month, 35_000, repo.budgets.value) as LedgerResult.Success).value
        assertEquals(first, second)
        assertEquals(35_000L, repo.budgets.value.single().amountMinor)
        assertTrue(setBudget(salary, month, 100, repo.budgets.value) is LedgerResult.Invalid)
        assertTrue(setBudget(food, month, 0, repo.budgets.value) is LedgerResult.Invalid)
    }

    @Test
    fun `konto und kategorie anlegen`() = runBlocking {
        assertTrue(CreateAccount(repo, ids, clock)("  ", AccountKind.CASH, 0) is LedgerResult.Invalid)
        val accId = (CreateAccount(repo, ids, clock)("Bargeld", AccountKind.CASH, 5_000) as LedgerResult.Success).value
        val acc = repo.accounts().first { it.id == accId }
        assertEquals(1, acc.sortOrder)
        assertEquals(5_000L, acc.openingBalanceMinor)
        val catId = (CreateCategory(repo, ids, clock)("Haustier", CategoryDirection.EXPENSE) as LedgerResult.Success).value
        assertEquals("Haustier", repo.categories().first { it.id == catId }.name)
    }

    @Test
    fun `monatsansicht reagiert auf neue buchungen`() = runBlocking {
        val observe = ObserveLedgerMonth(repo)
        val month = YearMonth.of(2026, 10)
        assertEquals(0L, observe(month).first().summary.expenseMinor)
        RecordTransaction(repo, ids, clock)(
            TransactionDraft.single(TransactionKind.INCOME, account, salary, 250_000, LocalDate.of(2026, 10, 1)),
        )
        RecordTransaction(repo, ids, clock)(
            TransactionDraft.single(TransactionKind.EXPENSE, account, food, 7_550, LocalDate.of(2026, 10, 1), payee = "Markt"),
        )
        val snap = observe(month).first()
        assertEquals(250_000L, snap.summary.incomeMinor)
        assertEquals(7_550L, snap.summary.expenseMinor)
        assertEquals(Money.eur(242_450), snap.totalBalance)
        assertEquals(1, snap.days.size)
        assertEquals(242_450L, snap.days.single().netMinor)
        assertEquals("Markt", snap.days.single().entries.first { it.direction == FlowDirection.OUT }.title)
        assertEquals(1000L, snap.expenseLines.single().sharePermille)
        assertFalse(snap.containsSampleData)
    }
}
