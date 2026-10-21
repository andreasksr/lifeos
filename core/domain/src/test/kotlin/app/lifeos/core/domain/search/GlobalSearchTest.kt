package app.lifeos.core.domain.search

import app.lifeos.core.domain.testing.id
import app.lifeos.core.model.Category
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeSummary
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.Split
import app.lifeos.core.model.Task
import app.lifeos.core.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GlobalSearchTest {
    private fun node(n: Int, type: NodeType, title: String) = NodeSummary(id(n), type, title, isSample = false, archived = false)

    @Test
    fun `findet titel, nebenfelder und ordnet nach relevanz`() {
        val nodes = listOf(
            node(1, NodeType.PROJECT, "Samurai Restauration"),
            node(2, NodeType.VEHICLE, "Suzuki Samurai"),
            node(3, NodeType.TRANSACTION, "Ausgabe"),
            node(4, NodeType.TASK, "Ölwechsel"),
            node(5, NodeType.ACCOUNT, "Samurai-Konto"), // Konten haben keine Detailansicht → nicht gelistet
        )
        val tx = LedgerTransaction(id(3), TransactionKind.EXPENSE, id(90), null, Money.eur(8_990), LocalDate.of(2026, 10, 18), "Teilehandel", "Bremsen für Samurai", listOf(Split(id(91), id(92), 8_990)))
        val task = Task(id(4), "Ölwechsel", description = "5W-30 kaufen")
        val cats = listOf(Category(id(92), "Fahrzeug", CategoryDirection.EXPENSE))

        val hits = GlobalSearch.search("samurai", nodes, listOf(tx), listOf(task), emptyList(), cats)
        assertEquals(listOf(id(1), id(2), id(3)), hits.map { it.id })
        assertEquals("Teilehandel", hits.last().title)

        assertEquals(listOf(id(3)), GlobalSearch.search("89,90", nodes, listOf(tx), listOf(task), emptyList(), cats).map { it.id })
        assertEquals(listOf(id(3)), GlobalSearch.search("fahrzeug", nodes, listOf(tx), listOf(task), emptyList(), cats).map { it.id })
        assertEquals(listOf(id(4)), GlobalSearch.search("5w-30", nodes, listOf(tx), listOf(task), emptyList(), cats).map { it.id })
        assertTrue(GlobalSearch.search("s", nodes, listOf(tx), listOf(task), emptyList(), cats).isEmpty())
    }
}
