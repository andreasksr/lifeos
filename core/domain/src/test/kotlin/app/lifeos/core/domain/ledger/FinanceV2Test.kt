package app.lifeos.core.domain.ledger

import app.lifeos.core.domain.testing.FakeLedgerRepository
import app.lifeos.core.domain.testing.FakeLinkRepository
import app.lifeos.core.domain.testing.FakeSettingsRepository
import app.lifeos.core.domain.testing.FixedClock
import app.lifeos.core.domain.testing.SequentialIds
import app.lifeos.core.domain.testing.id
import app.lifeos.core.model.AccountKind
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeSummary
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Szenarien aus Teil 4 und Teil 15 des Auftrags (Finanzen). */
class FinanceV2Test {

    private lateinit var repo: FakeLedgerRepository
    private lateinit var links: FakeLinkRepository
    private val ids = SequentialIds()
    private val clock = FixedClock()
    private val today = LocalDate.of(2026, 10, 20)
    private val october = YearMonth.of(2026, 10)

    private var giro = id(0)
    private var spar = id(0)
    private var food = id(0)
    private var car = id(0)
    private var salary = id(0)

    private val record get() = RecordTransaction(repo, ids, clock)

    @Before
    fun setUp() = runBlocking {
        repo = FakeLedgerRepository()
        links = FakeLinkRepository(repo.nodes)
        repo.onNodeDeleted = links::cascade
        EnsureLedgerDefaults(repo, FakeSettingsRepository(), ids, clock)()
        giro = repo.accounts().single().id
        spar = (CreateAccount(repo, ids, clock)("Sparkonto", AccountKind.SAVINGS, 0) as LedgerResult.Success).value
        food = repo.categories().first { it.name == "Lebensmittel" }.id
        car = repo.categories().first { it.name == "Fahrzeug" }.id
        salary = repo.categories().first { it.name == "Gehalt" }.id
    }

    private suspend fun book(kind: TransactionKind, amount: Long, category: NodeId?, date: String = "2026-10-10", payee: String? = null): NodeId {
        val draft = if (category != null) {
            TransactionDraft.single(kind, giro, category, amount, LocalDate.parse(date), payee = payee)
        } else {
            TransactionDraft(kind = kind, accountId = giro, amountMinor = amount, bookingDate = LocalDate.parse(date), payee = payee,
                counterAccountId = if (kind == TransactionKind.TRANSFER) spar else null)
        }
        return (record(draft) as LedgerResult.Success).value
    }

    private suspend fun snapshot() = ObserveLedgerMonth(repo, clock)(october).first()

    @Test
    fun `umbuchung erhoeht nicht die gesamtausgaben`() = runBlocking {
        book(TransactionKind.EXPENSE, 10_000, food)
        val before = snapshot().summary.expenseMinor
        book(TransactionKind.TRANSFER, 50_000, null)
        val after = snapshot()
        assertEquals(before, after.summary.expenseMinor)
        assertEquals(10_000L, after.summary.expenseMinor)
        assertEquals(Money.eur(-10_000), after.totalBalance) // Umbuchung ändert Gesamtsaldo nicht
    }

    @Test
    fun `erstattung mindert ausgaben der kategorie`() = runBlocking {
        book(TransactionKind.EXPENSE, 8_000, food)
        book(TransactionKind.REFUND, 1_500, food, payee = "Pfand")
        val s = snapshot()
        assertEquals(6_500L, s.summary.expenseMinor)
        assertEquals(8_000L, s.summary.grossExpenseMinor)
        assertEquals(1_500L, s.summary.refundMinor)
        assertEquals(6_500L, s.expenseLines.single().totalMinor)
        assertEquals(Money.eur(-6_500), s.totalBalance)
    }

    @Test
    fun `erstattung braucht ausgabenkategorie`() = runBlocking {
        val r = record(TransactionDraft.single(TransactionKind.REFUND, giro, salary, 100, today))
        assertTrue(r is LedgerResult.Invalid)
    }

    @Test
    fun `kontostand abgleichen erzeugt korrektur ohne statistik`() = runBlocking {
        book(TransactionKind.EXPENSE, 2_000, food)
        // berechnet: −20,00 €; tatsächlich: 100,00 €
        val r = ReconcileBalance(repo, ids, clock)(giro, 10_000, today)
        assertTrue(r is LedgerResult.Success)
        val s = snapshot()
        assertEquals(Money.eur(10_000), s.balances.first { it.account.id == giro }.balance)
        assertEquals(2_000L, s.summary.expenseMinor)
        assertEquals(0L, s.summary.incomeMinor)
        assertEquals(LedgerResult.Invalid(listOf(LedgerError.NothingToCorrect)), ReconcileBalance(repo, ids, clock)(giro, 10_000, today))
    }

    @Test
    fun `buchung korrigieren aktualisiert alle auswertungen`() = runBlocking {
        SetBudget(repo, ids)(food, october, 10_000, repo.budgets.value)
        SetTotalBudget(repo, ids)(october, 50_000)
        val tx = book(TransactionKind.EXPENSE, 12_000, food)
        assertTrue(snapshot().expenseLines.single().budget!!.isExceeded)
        record(TransactionDraft.single(TransactionKind.EXPENSE, giro, food, 4_000, LocalDate.of(2026, 10, 10), id = tx))
        val s = snapshot()
        assertEquals(4_000L, s.summary.expenseMinor)
        assertFalse(s.expenseLines.single().budget!!.isExceeded)
        assertEquals(46_000L, s.totalBudget!!.remainingMinor)
        assertEquals(Money.eur(-4_000), s.totalBalance)
        assertEquals(4_000L, s.cumulativeExpenses.last().valueMinor)
    }

    @Test
    fun `budgetueberschreitung wird nicht auf 100 prozent begrenzt`() = runBlocking {
        SetTotalBudget(repo, ids)(october, 10_000)
        book(TransactionKind.EXPENSE, 13_550, food)
        val t = snapshot().totalBudget!!
        assertTrue(t.isExceeded)
        assertEquals(1_355L, t.usedPermille)
        assertEquals(-3_550L, t.remainingMinor)
    }

    @Test
    fun `gesamtbudget gilt ab monat bis zur aenderung`() = runBlocking {
        SetTotalBudget(repo, ids)(YearMonth.of(2026, 8), 100_000)
        SetTotalBudget(repo, ids)(YearMonth.of(2026, 11), 80_000)
        assertEquals(100_000L, snapshot().totalBudget!!.limitMinor)
        SetTotalBudget(repo, ids)(YearMonth.of(2026, 8), 90_000) // gleicher Monat ersetzt
        assertEquals(2, repo.totalBudgets.value.size)
        assertEquals(90_000L, snapshot().totalBudget!!.limitMinor)
        RemoveTotalBudget(repo)(YearMonth.of(2026, 8))
        assertNull(snapshot().totalBudget)
    }

    @Test
    fun `wiederkehrende zahlung wird nie automatisch gebucht und nur einmal ausgefuehrt`() = runBlocking {
        val rid = (SaveRecurring(repo, ids, clock)(
            RecurringDraft(name = "Miete", kind = TransactionKind.EXPENSE, accountId = giro, categoryId = food,
                amountMinor = 95_000, intervalMonths = 1, anchorDate = LocalDate.of(2026, 9, 1)),
        ) as LedgerResult.Success).value
        var s = snapshot()
        assertEquals(0L, s.summary.expenseMinor) // Plan ≠ Buchung
        assertTrue(repo.transactions.value.isEmpty())
        assertEquals(
            listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)),
            s.pending.map { it.dueDate },
        )
        assertEquals(95_000L, s.fixedMonthlyPlanMinor)

        val book = BookOccurrence(repo, ids, clock)
        assertTrue(book(rid, LocalDate.of(2026, 10, 1)) is LedgerResult.Success)
        assertEquals(
            LedgerResult.Invalid(listOf(LedgerError.OccurrenceAlreadyHandled)),
            book(rid, LocalDate.of(2026, 10, 1)),
        )
        assertEquals(LedgerResult.Invalid(listOf(LedgerError.OccurrenceNotDue)), book(rid, LocalDate.of(2026, 10, 2)))
        assertTrue(SkipOccurrence(repo)(rid, LocalDate.of(2026, 9, 1)) is LedgerResult.Success)

        s = snapshot()
        assertEquals(95_000L, s.summary.expenseMinor)
        assertEquals(95_000L, s.summary.fixedExpenseMinor)
        assertEquals(0L, s.summary.variableExpenseMinor)
        assertEquals(listOf(LocalDate.of(2026, 11, 1)), s.pending.map { it.dueDate })
        assertEquals("Miete", s.days.single().entries.single().recurringName)

        UnskipOccurrence(repo)(rid, LocalDate.of(2026, 9, 1))
        assertEquals(2, snapshot().pending.size)
    }

    @Test
    fun `prognose trennt kontostand budget und voraussichtlich verfuegbar`() = runBlocking {
        book(TransactionKind.INCOME, 300_000, salary, date = "2026-10-01")
        SaveRecurring(repo, ids, clock)(
            RecurringDraft(name = "Versicherung", kind = TransactionKind.EXPENSE, accountId = giro, categoryId = car,
                amountMinor = 12_000, intervalMonths = 1, anchorDate = LocalDate.of(2026, 10, 25)),
        )
        val f = snapshot().forecast!!
        assertEquals(Money.eur(300_000), f.bookedBalance)
        assertEquals(12_000L, f.openExpenseMinor)
        assertEquals(Money.eur(288_000), f.expectedAtMonthEnd)
        assertEquals(LocalDate.of(2026, 10, 31), f.until)
    }

    @Test
    fun `verlauf ueber sechs monate`() = runBlocking {
        book(TransactionKind.EXPENSE, 1_000, food, date = "2026-06-03")
        book(TransactionKind.EXPENSE, 2_000, food, date = "2026-10-03")
        val h = snapshot().history
        assertEquals(6, h.size)
        assertEquals(YearMonth.of(2026, 5), h.first().month)
        assertEquals(listOf(0L, 1_000L, 0L, 0L, 0L, 2_000L), h.map { it.expenseMinor })
    }

    @Test
    fun `loeschen und rueckgaengig stellt buchung links und planzuordnung wieder her`() = runBlocking {
        val rid = (SaveRecurring(repo, ids, clock)(
            RecurringDraft(name = "Handy", kind = TransactionKind.EXPENSE, accountId = giro, categoryId = food,
                amountMinor = 2_999, intervalMonths = 1, anchorDate = LocalDate.of(2026, 10, 5)),
        ) as LedgerResult.Success).value
        val tx = (BookOccurrence(repo, ids, clock)(rid, LocalDate.of(2026, 10, 5)) as LedgerResult.Success).value
        val project = id(777)
        repo.nodes[project] = NodeSummary(project, NodeType.PROJECT, "Projekt", false, false)
        app.lifeos.core.domain.links.LinkNodes(links, ids, clock)(tx, project, LinkRelation.PART_OF)

        val deleted = (DeleteTransaction(repo, links)(tx) as LedgerResult.Success).value
        assertTrue(repo.transactions.value.isEmpty())
        assertTrue(links.links.value.isEmpty())
        assertEquals(1, snapshot().pending.count { it.dueDate == LocalDate.of(2026, 10, 5) }) // wieder offen

        assertEquals(LedgerResult.Success(tx), RestoreTransaction(repo, links, clock)(deleted))
        assertNotNull(repo.transaction(tx))
        assertEquals(1, links.links.value.size)
        assertEquals(0, snapshot().pending.count { it.dueDate == LocalDate.of(2026, 10, 5) })
    }

    @Test
    fun `plan validierung`() = runBlocking {
        val save = SaveRecurring(repo, ids, clock)
        val base = RecurringDraft(name = "X", kind = TransactionKind.EXPENSE, accountId = giro, categoryId = food,
            amountMinor = 100, intervalMonths = 1, anchorDate = today)
        assertTrue((save(base.copy(intervalMonths = 5)) as LedgerResult.Invalid).errors.contains(LedgerError.InvalidInterval))
        assertTrue((save(base.copy(endDate = today.minusDays(1))) as LedgerResult.Invalid).errors.contains(LedgerError.EndBeforeStart))
        assertTrue((save(base.copy(categoryId = salary)) as LedgerResult.Invalid).errors.contains(LedgerError.CategoryDirectionMismatch(salary)))
        assertTrue((save(base.copy(name = " ")) as LedgerResult.Invalid).errors.contains(LedgerError.NameRequired))
        assertTrue(save(base) is LedgerResult.Success)
    }

    @Test
    fun `konto und kategorie umbenennen und archivieren`() = runBlocking {
        assertTrue(UpdateAccount(repo, clock)(spar, "Tagesgeld", archived = true) is LedgerResult.Success)
        val acc = repo.accounts().first { it.id == spar }
        assertEquals("Tagesgeld", acc.name)
        assertTrue(acc.archived)
        assertTrue(UpdateCategory(repo, clock)(food, "", archived = false) is LedgerResult.Invalid)
        assertTrue(UpdateCategory(repo, clock)(food, "Essen", archived = false) is LedgerResult.Success)
        assertEquals("Essen", repo.categories().first { it.id == food }.name)
    }

    @Test
    fun `kategorievorschlaege nach haeufigkeit und aktualitaet`() = runBlocking {
        book(TransactionKind.EXPENSE, 100, car, date = "2026-10-01")
        book(TransactionKind.EXPENSE, 100, food, date = "2026-10-02")
        book(TransactionKind.EXPENSE, 100, food, date = "2026-10-03")
        val ranked = CategorySuggestions.ranked(repo.categories(), repo.transactions.value, CategoryDirection.EXPENSE, today)
        assertEquals(listOf(food, car), ranked.take(2).map { it.id })
        assertEquals(repo.categories().count { it.direction == CategoryDirection.EXPENSE }, ranked.size)
    }

    @Test
    fun `buchungen filtern und durchsuchen`() = runBlocking {
        book(TransactionKind.EXPENSE, 8_990, car, payee = "ATU Bremsbeläge", date = "2026-10-03")
        book(TransactionKind.EXPENSE, 1_250, food, payee = "Wochenmarkt", date = "2026-10-04")
        book(TransactionKind.INCOME, 300_000, salary, date = "2026-10-01")
        val all = repo.transactions.value
        val cats = repo.categories().associate { it.id to it.name }
        val accs = repo.accounts().associate { it.id to it.name }
        fun q(query: TransactionQuery) = TransactionFilter.apply(all, query, cats, accs).map { it.payee }
        assertEquals(listOf("ATU Bremsbeläge"), q(TransactionQuery(text = "brems")))
        assertEquals(listOf("ATU Bremsbeläge"), q(TransactionQuery(text = "89,90")))
        assertEquals(listOf("Wochenmarkt"), q(TransactionQuery(text = "lebensmittel")))
        assertEquals(2, q(TransactionQuery(kinds = setOf(TransactionKind.EXPENSE))).size)
        assertEquals(listOf("ATU Bremsbeläge"), q(TransactionQuery(categoryId = car)))
        assertEquals(1, q(TransactionQuery(from = LocalDate.of(2026, 10, 4))).size)
        assertTrue(TransactionQuery().isEmpty)
    }

    @Test
    fun `budgetverlauf und soll linie`() {
        val pace = LedgerMath.budgetPace(31_000, october)
        assertEquals(31, pace.size)
        assertEquals(1_000L, pace.first().valueMinor)
        assertEquals(31_000L, pace.last().valueMinor)
    }
}
