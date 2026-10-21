package app.lifeos.core.domain.projects

import app.lifeos.core.domain.ledger.EnsureLedgerDefaults
import app.lifeos.core.domain.ledger.LedgerResult
import app.lifeos.core.domain.ledger.LedgerMath
import app.lifeos.core.domain.ledger.RecordTransaction
import app.lifeos.core.domain.ledger.TransactionDraft
import app.lifeos.core.domain.planning.CompleteTask
import app.lifeos.core.domain.planning.PlanningResult
import app.lifeos.core.domain.planning.SaveTask
import app.lifeos.core.domain.planning.TaskDraft
import app.lifeos.core.domain.testing.CountingScheduler
import app.lifeos.core.domain.testing.FakeLedgerRepository
import app.lifeos.core.domain.testing.FakeLinkRepository
import app.lifeos.core.domain.testing.FakePlanningRepository
import app.lifeos.core.domain.testing.FakeProjectRepository
import app.lifeos.core.domain.testing.FakeSettingsRepository
import app.lifeos.core.domain.testing.FixedClock
import app.lifeos.core.domain.testing.SequentialIds
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.ProgressMode
import app.lifeos.core.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Auftrag Teil 6 + Teil 15: Projekt-Finanzverknüpfung ohne Duplikat, Fortschritt nachvollziehbar. */
class ProjectsTest {
    private val ids = SequentialIds()
    private val clock = FixedClock()
    private val today = LocalDate.of(2026, 10, 20)

    @Test
    fun `buchung einem projekt zuordnen - erscheint dort - aenderung wirkt ueberall - kein duplikat`() = runBlocking {
        val ledger = FakeLedgerRepository()
        EnsureLedgerDefaults(ledger, FakeSettingsRepository(), ids, clock)()
        val links = FakeLinkRepository(ledger.nodes)
        ledger.onNodeDeleted = links::cascade
        val projects = FakeProjectRepository(ledger.nodes)
        val pid = (SaveProject(projects, ids, clock)(ProjectDraft(name = "Suzuki Samurai", startDate = today, budgetMinor = 200_000)) as ProjectResult.Success).value

        val giro = ledger.accounts().single().id
        val car = ledger.categories().first { it.name == "Fahrzeug" }.id
        val record = RecordTransaction(ledger, ids, clock)
        val tx = (record(TransactionDraft.single(TransactionKind.EXPENSE, giro, car, 34_900, today, payee = "Bremssattel")) as LedgerResult.Success).value

        val assign = AssignTransactionToProject(links, ledger, projects, ids, clock)
        assertTrue(assign(tx, pid) is ProjectResult.Success)
        assertEquals(ProjectResult.Invalid(listOf(ProjectError.AlreadyLinked)), assign(tx, pid))

        suspend fun projectCosts(): ProjectCosts {
            val txIds = ProjectSnapshotBuilder.linkedTransactionIds(links.observeNeighbors(pid).first())
            return ProjectCostCalculator.compute(txIds.mapNotNull { ledger.transaction(it) }, 200_000)
        }
        assertEquals(34_900L, projectCosts().netMinor)
        assertEquals(1, ledger.transactions.value.size) // kein Duplikat

        // Korrektur der Buchung → Projektkosten und Haushaltskasse ändern sich gemeinsam
        record(TransactionDraft.single(TransactionKind.EXPENSE, giro, car, 29_900, today, payee = "Bremssattel", id = tx))
        assertEquals(29_900L, projectCosts().netMinor)
        assertEquals(29_900L, LedgerMath.monthSummary(ledger.transactions.value, YearMonth.from(today), CurrencyCode.EUR).expenseMinor)
        assertEquals(170_100L, projectCosts().remainingMinor)

        // Erstattung im Projekt mindert Kosten
        val refund = (record(TransactionDraft.single(TransactionKind.REFUND, giro, car, 4_000, today, payee = "Altteilpfand")) as LedgerResult.Success).value
        assign(refund, pid)
        assertEquals(25_900L, projectCosts().netMinor)

        // Zuordnung lösen: Buchung bleibt in der Haushaltskasse
        UnlinkNodes(links)(tx, pid)
        assertEquals(-4_000L, projectCosts().netMinor)
        assertEquals(2, ledger.transactions.value.size)
    }

    @Test
    fun `fortschritt nur aus nachvollziehbaren kriterien`() = runBlocking {
        val planning = FakePlanningRepository()
        val projects = FakeProjectRepository(planning = planning)
        val sched = CountingScheduler()
        val pid = (SaveProject(projects, ids, clock)(ProjectDraft(name = "Werkstatt", startDate = today)) as ProjectResult.Success).value
        val project = projects.project(pid)!!
        assertNull(ProgressCalculator.compute(project, emptyList(), emptyList()).permille) // keine erfundene Zahl

        val save = SaveTask(planning, ids, clock, sched)
        val t1 = (save(TaskDraft(title = "Regal", projectId = pid)) as PlanningResult.Success).value
        save(TaskDraft(title = "Licht", projectId = pid))
        save(TaskDraft(title = "Boden", projectId = pid))
        CompleteTask(planning, clock, sched)(t1)
        val p = ProgressCalculator.compute(project, planning.tasks(), emptyList())
        assertEquals(333, p.permille)
        assertEquals("1 von 3 Aufgaben erledigt", p.basis)

        // Manuell: muss bewusst gesetzt werden
        assertEquals(
            ProjectResult.Invalid(listOf(ProjectError.ManualProgressMissing)),
            SaveProject(projects, ids, clock)(ProjectDraft(id = pid, name = "Werkstatt", startDate = today, progressMode = ProgressMode.MANUAL)),
        )
        SaveProject(projects, ids, clock)(ProjectDraft(id = pid, name = "Werkstatt", startDate = today, progressMode = ProgressMode.MANUAL, manualProgressPermille = 600))
        assertEquals(600, ProgressCalculator.compute(projects.project(pid)!!, planning.tasks(), emptyList()).permille)

        // Meilensteine
        val m1 = (SaveMilestone(projects, ids, clock)(pid, "Rohbau", today) as ProjectResult.Success).value
        SaveMilestone(projects, ids, clock)(pid, "Elektrik", today.plusDays(10))
        SaveMilestone(projects, ids, clock)(pid, "Rohbau", today, id = m1, done = true)
        SaveProject(projects, ids, clock)(ProjectDraft(id = pid, name = "Werkstatt", startDate = today, progressMode = ProgressMode.MILESTONES))
        assertEquals(500, ProgressCalculator.compute(projects.project(pid)!!, planning.tasks(), projects.milestones()).permille)

        // Projekt löschen: Aufgaben bleiben, verlieren nur die Zuordnung
        DeleteProject(projects)(pid)
        assertEquals(3, planning.tasks().size)
        assertTrue(planning.tasks().all { it.projectId == null })
    }

    @Test
    fun `projekt validierung`() = runBlocking {
        val projects = FakeProjectRepository()
        val save = SaveProject(projects, ids, clock)
        assertEquals(ProjectResult.Invalid(listOf(ProjectError.NameRequired)), save(ProjectDraft(name = "", startDate = today)))
        assertEquals(
            ProjectResult.Invalid(listOf(ProjectError.TargetBeforeStart)),
            save(ProjectDraft(name = "X", startDate = today, targetDate = today.minusDays(1))),
        )
        assertEquals(ProjectResult.Invalid(listOf(ProjectError.InvalidBudget)), save(ProjectDraft(name = "X", startDate = today, budgetMinor = 0)))
    }
}
