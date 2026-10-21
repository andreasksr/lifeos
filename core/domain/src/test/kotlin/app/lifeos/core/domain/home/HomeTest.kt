package app.lifeos.core.domain.home

import app.lifeos.core.domain.ledger.EnsureLedgerDefaults
import app.lifeos.core.domain.ledger.LedgerResult
import app.lifeos.core.domain.ledger.ObserveLedgerMonth
import app.lifeos.core.domain.ledger.RecordTransaction
import app.lifeos.core.domain.ledger.RecurringDraft
import app.lifeos.core.domain.ledger.SaveRecurring
import app.lifeos.core.domain.ledger.SetTotalBudget
import app.lifeos.core.domain.ledger.TransactionDraft
import app.lifeos.core.domain.testing.FakeLedgerRepository
import app.lifeos.core.domain.testing.FakeSettingsRepository
import app.lifeos.core.domain.testing.FixedClock
import app.lifeos.core.domain.testing.SequentialIds
import app.lifeos.core.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HomeTest {

    @Test
    fun `layout kodieren dekodieren und neue abschnitte anhaengen`() {
        val l = HomeLayout().moveUp(HomeSectionId.FINANCE).toggle(HomeSectionId.LEARNING).copy(density = HomeDensity.MINIMAL)
        val decoded = HomeLayout.decode(HomeLayout.encode(l))
        assertEquals(l, decoded)
        // Älterer gespeicherter Stand ohne neue Abschnitte: Rest wird angehängt, Unbekanntes ignoriert
        val old = HomeLayout.decode("ORDER=FINANCE,TODAY,GHOST;HIDDEN=;DENSITY=WHATEVER")
        assertEquals(HomeSectionId.FINANCE, old.order.first())
        assertEquals(HomeSectionId.entries.size, old.order.size)
        assertEquals(HomeDensity.STANDARD, old.density)
        assertEquals(HomeLayout(), HomeLayout.decode(null))
    }

    @Test
    fun `nur verfuegbare und sichtbare abschnitte`() {
        val l = HomeLayout().toggle(HomeSectionId.ATTENTION)
        assertEquals(
            listOf(HomeSectionId.TODAY, HomeSectionId.FINANCE),
            l.visible(setOf(HomeSectionId.TODAY, HomeSectionId.FINANCE, HomeSectionId.ATTENTION)),
        )
        assertEquals(l, l.moveUp(HomeSectionId.TODAY)) // erstes Element bleibt
    }

    @Test
    fun `hinweise nur aus echten daten`() = runBlocking {
        val repo = FakeLedgerRepository()
        val ids = SequentialIds()
        val clock = FixedClock()
        EnsureLedgerDefaults(repo, FakeSettingsRepository(), ids, clock)()
        val observe = ObserveLedgerMonth(repo, clock)
        // Leere Daten → keine Hinweise
        assertTrue(AttentionBuilder.fromFinance(observe(YearMonth.of(2026, 10)).first()).isEmpty())

        val giro = repo.accounts().single().id
        val food = repo.categories().first { it.name == "Lebensmittel" }.id
        SetTotalBudget(repo, ids)(YearMonth.of(2026, 10), 1_000)
        RecordTransaction(repo, ids, clock)(TransactionDraft.single(TransactionKind.EXPENSE, giro, food, 5_000, LocalDate.of(2026, 10, 3)))
        SaveRecurring(repo, ids, clock)(
            RecurringDraft(name = "Miete", kind = TransactionKind.EXPENSE, accountId = giro, categoryId = food,
                amountMinor = 90_000, intervalMonths = 1, anchorDate = LocalDate.of(2026, 10, 1)),
        ) as LedgerResult.Success
        val items = AttentionBuilder.fromFinance(observe(YearMonth.of(2026, 10)).first())
        assertEquals(AttentionSeverity.ALERT, items.first().severity)
        assertTrue(items.any { it.title == "Monatsbudget überschritten" })
        assertTrue(items.any { it.title == "Miete" && it.detail.startsWith("seit 19 Tagen überfällig") })
    }
}
