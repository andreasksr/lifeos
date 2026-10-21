package app.lifeos.core.domain.map

import app.lifeos.core.domain.testing.id
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.Milestone
import app.lifeos.core.model.NodeLink
import app.lifeos.core.model.NodeSummary
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.Task
import app.lifeos.core.model.VehicleEvent
import app.lifeos.core.model.VehicleEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LifeMapTest {
    private fun n(i: Int, t: NodeType, title: String) = NodeSummary(id(i), t, title, false, false)

    @Test
    fun `samurai projekt zeigt nur direkte verbindungen aus links und festen beziehungen`() {
        val nodes = listOf(
            n(1, NodeType.PROJECT, "Suzuki Samurai"), n(2, NodeType.VEHICLE, "Samurai"), n(3, NodeType.TRANSACTION, "Bremsscheiben"),
            n(4, NodeType.TASK, "Bremsen tauschen"), n(5, NodeType.MILESTONE, "TÜV"), n(6, NodeType.DOCUMENT, "Rechnung"),
            n(7, NodeType.VEHICLE_EVENT, "Reparatur Bremsen"), n(8, NodeType.TRANSACTION, "Fremde Buchung"),
        )
        val links = listOf(
            NodeLink(id(50), id(3), id(1), LinkRelation.PART_OF),
            NodeLink(id(51), id(1), id(2), LinkRelation.RELATES_TO),
            NodeLink(id(52), id(6), id(3), LinkRelation.DOCUMENTS), // zweiter Grad – erscheint NICHT
            NodeLink(id(53), id(3), id(1), LinkRelation.CONCERNS), // doppelt – nur einmal
        )
        val tasks = listOf(Task(id(4), "Bremsen tauschen", projectId = id(1)))
        val ms = listOf(Milestone(id(5), id(1), "TÜV"))
        val ev = listOf(VehicleEvent(id(7), id(2), VehicleEventKind.REPAIR, "Reparatur Bremsen", LocalDate.of(2026, 10, 1)))

        val v = LifeMapBuilder.build(id(1), nodes, links, tasks, ms, ev, emptyList())!!
        assertEquals(listOf(id(2), id(3), id(4), id(5)), v.neighbors.map { it.node.id })
        assertEquals("enthält", v.neighbors.first { it.node.id == id(3) }.meaning)

        val vehicle = LifeMapBuilder.build(id(2), nodes, links, tasks, ms, ev, emptyList())!!
        assertEquals(listOf(id(1), id(7)), vehicle.neighbors.map { it.node.id })
        assertNull(LifeMapBuilder.build(id(99), nodes, links, tasks, ms, ev, emptyList()))
    }
}
