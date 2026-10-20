package app.lifeos.core.domain

import app.lifeos.core.domain.ledger.EnsureLedgerDefaults
import app.lifeos.core.domain.ledger.LedgerMath
import app.lifeos.core.domain.ledger.LedgerResult
import app.lifeos.core.domain.ledger.RecordTransaction
import app.lifeos.core.domain.ledger.TransactionDraft
import app.lifeos.core.domain.links.LinkNodes
import app.lifeos.core.domain.links.LinkResult
import app.lifeos.core.domain.testing.FakeLedgerRepository
import app.lifeos.core.domain.testing.FakeLinkRepository
import app.lifeos.core.domain.testing.FakeSettingsRepository
import app.lifeos.core.domain.testing.FixedClock
import app.lifeos.core.domain.testing.SequentialIds
import app.lifeos.core.domain.testing.id
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.NodeSummary
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Abnahmekriterium aus dem Entwicklungsauftrag, Abschnitt 2:
 * Ein Ersatzteilkauf ist in Haushaltskasse, Fahrzeughistorie und Reparaturprojekt sichtbar –
 * es existiert aber genau eine finanzielle Buchung.
 */
class SparePartScenarioTest {

    @Test
    fun `ein ersatzteilkauf - drei sichten - eine buchung`() = runBlocking {
        val ids = SequentialIds()
        val clock = FixedClock()
        val ledger = FakeLedgerRepository()
        EnsureLedgerDefaults(ledger, FakeSettingsRepository(), ids, clock)()

        // Fahrzeug und Projekt sind ab Phase 4 echte Module; hier als Nodes repräsentiert.
        val vehicle = id(1)
        val project = id(2)
        ledger.nodes[vehicle] = NodeSummary(vehicle, NodeType.VEHICLE, "Golf VII", false, false)
        ledger.nodes[project] = NodeSummary(project, NodeType.PROJECT, "Bremsen erneuern", false, false)
        val links = FakeLinkRepository(ledger.nodes)

        val account = ledger.accounts().single().id
        val carCategory = ledger.categories().first { it.name == "Fahrzeug" }.id
        val txId = (RecordTransaction(ledger, ids, clock)(
            TransactionDraft.single(
                TransactionKind.EXPENSE, account, carCategory, 8_990,
                LocalDate.of(2026, 10, 12), payee = "Bremsbeläge",
            ),
        ) as LedgerResult.Success).value

        val link = LinkNodes(links, ids, clock)
        assertEquals(LinkResult.Linked::class, link(txId, vehicle, LinkRelation.CONCERNS)::class)
        assertEquals(LinkResult.Linked::class, link(txId, project, LinkRelation.PART_OF)::class)
        assertEquals(LinkResult.AlreadyLinked, link(txId, vehicle, LinkRelation.CONCERNS))
        assertEquals(LinkResult.SelfLink, link(txId, txId, LinkRelation.RELATES_TO))
        assertEquals(LinkResult.MissingNode(id(9)), link(txId, id(9), LinkRelation.RELATES_TO))

        // Sicht Haushaltskasse
        val all = ledger.transactions.value
        assertEquals(1, all.size)
        assertEquals(8_990L, LedgerMath.monthSummary(all, YearMonth.of(2026, 10), CurrencyCode.EUR).expenseMinor)

        // Sicht Fahrzeug und Projekt: Kosten = Summe der verknüpften Buchungen – dieselbe Buchung
        fun costOf(node: app.lifeos.core.model.NodeId) = runBlocking {
            links.observeLinksOf(node).first()
                .mapNotNull { l -> ledger.transaction(if (l.to == node) l.from else l.to) }
                .sumOf { it.amount.minor }
        }
        assertEquals(8_990L, costOf(vehicle))
        assertEquals(8_990L, costOf(project))
        assertEquals(txId, links.observeLinksOf(vehicle).first().single().from)
        assertEquals(txId, links.observeLinksOf(project).first().single().from)

        // Gesamtsaldo wurde genau einmal belastet
        val balances = LedgerMath.balances(ledger.accounts(), all)
        assertEquals(-8_990L, balances.getValue(account).minor)
    }
}
