package app.lifeos.core.domain.projects

import app.lifeos.core.domain.testing.id
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.Milestone
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeLink
import app.lifeos.core.model.ProgressMode
import app.lifeos.core.model.Project
import app.lifeos.core.model.ProjectStatus
import app.lifeos.core.model.Split
import app.lifeos.core.model.Task
import app.lifeos.core.model.TaskStatus
import app.lifeos.core.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ProjectOverviewTest {
    private val d = LocalDate.of(2026, 10, 1)
    private val samurai = Project(id(1), "Suzuki Samurai", startDate = d, budgetMinor = 100_000)
    private val garten = Project(id(2), "Gartenhaus", startDate = d, status = ProjectStatus.DONE, progressMode = ProgressMode.MILESTONES)
    private val idee = Project(id(3), "Werkbank", startDate = d, status = ProjectStatus.PLANNED, progressMode = ProgressMode.MANUAL, manualProgressPermille = 250)

    private fun tx(n: Int, kind: TransactionKind, amount: Long) = LedgerTransaction(
        id(n), kind, id(90), null, Money.eur(amount), d, "x", null, listOf(Split(id(n + 500), id(91), amount)),
    )

    @Test
    fun `kosten nur aus verknuepften buchungen erstattungen mindern`() {
        val txs = listOf(tx(10, TransactionKind.EXPENSE, 8_990), tx(11, TransactionKind.REFUND, 1_000), tx(12, TransactionKind.EXPENSE, 50_000))
        val links = listOf(
            NodeLink(id(20), id(10), samurai.id, LinkRelation.PART_OF),
            NodeLink(id(21), samurai.id, id(11), LinkRelation.PART_OF), // umgekehrte Richtung zählt ebenfalls
            NodeLink(id(22), id(12), id(70), LinkRelation.CONCERNS), // betrifft ein Fahrzeug, nicht das Projekt
        )
        val cards = ProjectOverview.build(listOf(garten, idee, samurai), emptyList(), emptyList(), links, txs)
        assertEquals(listOf("Suzuki Samurai", "Werkbank", "Gartenhaus"), cards.map { it.project.name })
        val s = cards.first()
        assertEquals(7_990L, s.costs.netMinor)
        assertEquals(92_010L, s.costs.remainingMinor)
        assertEquals(79L, s.costs.usedPermille)
        assertEquals(0L, cards.last().costs.netMinor)
    }

    @Test
    fun `fortschritt naechste aufgabe und meilenstein`() {
        val tasks = listOf(
            Task(id(30), "Bremsen", projectId = samurai.id, dueDate = d.plusDays(5)),
            Task(id(31), "Teile bestellen", projectId = samurai.id, dueDate = d.plusDays(2)),
            Task(id(32), "Ausbauen", projectId = samurai.id, status = TaskStatus.DONE),
            Task(id(33), "Fremd", projectId = garten.id),
        )
        val ms = listOf(
            Milestone(id(40), garten.id, "Fundament", doneAt = Instant.EPOCH),
            Milestone(id(41), garten.id, "Dach", dueDate = d.plusDays(30)),
        )
        val cards = ProjectOverview.build(listOf(samurai, garten, idee), tasks, ms, emptyList(), emptyList()).associateBy { it.project.id }
        val s = cards.getValue(samurai.id)
        assertEquals(333, s.progress.permille)
        assertEquals("Teile bestellen", s.nextTask?.title)
        assertEquals(2, s.openTasks)
        assertEquals(1, s.doneTasks)
        assertEquals(500, cards.getValue(garten.id).progress.permille)
        assertEquals("Dach", cards.getValue(garten.id).nextMilestone?.title)
        assertEquals(250, cards.getValue(idee.id).progress.permille)
        assertNull(cards.getValue(idee.id).nextTask)
    }
}
