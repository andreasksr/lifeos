package app.lifeos.core.database

import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lifeos.core.database.repository.RoomIntegrityChecker
import app.lifeos.core.database.repository.RoomLedgerRepository
import app.lifeos.core.database.repository.RoomLinkRepository
import app.lifeos.core.database.repository.RoomPlanningRepository
import app.lifeos.core.database.repository.RoomProjectRepository
import app.lifeos.core.domain.planning.TaskCompletion
import app.lifeos.core.model.CalendarEvent
import app.lifeos.core.model.Milestone
import app.lifeos.core.model.Priority
import app.lifeos.core.model.ProgressMode
import app.lifeos.core.model.Project
import app.lifeos.core.model.Reminder
import app.lifeos.core.model.Repeat
import app.lifeos.core.model.RepeatUnit
import app.lifeos.core.model.Task
import app.lifeos.core.database.schema.SchemaSql
import app.lifeos.core.model.Account
import app.lifeos.core.model.AccountKind
import app.lifeos.core.model.Category
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeLink
import app.lifeos.core.model.Split
import app.lifeos.core.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Läuft in der CI auf dem Emulator (API 34): ./gradlew :core:database:connectedAndroidTest */
@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {

    private lateinit var db: LifeOsDatabase
    private lateinit var ledger: RoomLedgerRepository
    private val now = Instant.parse("2026-10-20T10:00:00Z")

    private val giro = Account(NodeId.random(), "Giro", AccountKind.CHECKING, CurrencyCode.EUR, 0, 0)
    private val food = Category(NodeId.random(), "Lebensmittel", CategoryDirection.EXPENSE)

    @Before
    fun setUp() = runTest {
        db = LifeOsDatabase.inMemory(ApplicationProvider.getApplicationContext())
        ledger = RoomLedgerRepository(db)
        ledger.saveAccount(giro, now)
        ledger.saveCategory(food, now)
    }

    @After
    fun tearDown() = db.close()

    private fun expense(amount: Long) = LedgerTransaction(
        NodeId.random(), TransactionKind.EXPENSE, giro.id, null, Money.eur(amount),
        LocalDate.of(2026, 10, 20), "Markt", null, listOf(Split(NodeId.random(), food.id, amount)),
    )

    @Test
    fun roomSchemaMatchesReferenceSql() {
        // Vergleicht Spalten jeder Tabelle mit der Referenz-DDL aus SchemaSql.
        val sqlite = db.openHelper.readableDatabase
        val tables = listOf("node", "link", "account", "category", "ledger_transaction", "transaction_split", "budget", "app_setting",
            "recurring_payment", "recurring_booking", "total_budget", "project", "milestone", "task", "task_completion", "calendar_event", "reminder")
        tables.forEach { table ->
            val reference = SchemaSql.REFERENCE_TABLES.single { it.contains("`$table` (") }
            sqlite.query("PRAGMA table_info(`$table`)").use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(c.getColumnIndexOrThrow("name"))
                    assertTrue("$table.$name fehlt in Referenz-DDL", reference.contains("`$name`"))
                }
            }
        }
        val triggers = sqlite.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger'").use {
            it.moveToFirst(); it.getInt(0)
        }
        assertEquals(SchemaSql.TRIGGERS.size, triggers)
    }

    @Test
    fun saveEditDeleteRoundTrip() = runTest {
        val tx = expense(4_299)
        ledger.saveTransaction(tx, now)
        assertEquals(tx, ledger.transaction(tx.id))

        val edited = tx.copy(amount = Money.eur(5_000), splits = listOf(Split(NodeId.random(), food.id, 5_000)))
        ledger.saveTransaction(edited, now)
        assertEquals(edited, ledger.transaction(tx.id))
        assertEquals(1, ledger.observeAllTransactions().first().size)

        ledger.deleteTransaction(tx.id)
        assertNull(ledger.transaction(tx.id))
        assertTrue(RoomIntegrityChecker(db).run().isHealthy)
    }

    @Test
    fun triggerRejectsInvalidBookingEvenWhenDomainIsBypassed() = runTest {
        try {
            ledger.saveTransaction(expense(1).copy(amount = Money.eur(0), splits = emptyList()), now)
            fail("Trigger LIFEOS_TX_AMOUNT hätte greifen müssen")
        } catch (e: SQLiteException) {
            assertTrue(e.message!!.contains("LIFEOS_TX_AMOUNT"))
        }
        assertTrue(ledger.observeAllTransactions().first().isEmpty())
    }

    @Test
    fun linkedTransactionCascadesLinksOnDelete() = runTest {
        val tx = expense(8_990)
        ledger.saveTransaction(tx, now)
        val links = RoomLinkRepository(db)
        links.link(NodeLink(NodeId.random(), tx.id, giro.id, LinkRelation.RELATES_TO), now)
        assertEquals(1, links.observeLinksOf(giro.id).first().size)
        ledger.deleteTransaction(tx.id)
        assertEquals(0, links.observeLinksOf(giro.id).first().size)
    }

    @Test
    fun planningAndProjectsRoundTripAndCascade() = runTest {
        val planning = RoomPlanningRepository(db)
        val projects = RoomProjectRepository(db)
        val links = RoomLinkRepository(db)
        val project = Project(NodeId.random(), "Suzuki Samurai", startDate = LocalDate.of(2026, 10, 1), budgetMinor = 200_000, progressMode = ProgressMode.MILESTONES)
        projects.saveProject(project, now)
        val milestone = Milestone(NodeId.random(), project.id, "TÜV", LocalDate.of(2027, 2, 1))
        projects.saveMilestone(milestone, now)
        val task = Task(
            NodeId.random(), "Bremsen", priority = Priority.HIGH, dueDate = LocalDate.of(2026, 10, 25), dueTime = LocalTime.of(9, 30),
            repeat = Repeat(RepeatUnit.MONTH, 3), repeatAnchor = LocalDate.of(2026, 10, 25), projectId = project.id,
        )
        planning.saveTask(task, now)
        val event = CalendarEvent(NodeId.random(), "Unterricht", LocalDateTime.of(2026, 10, 20, 18, 0), LocalDateTime.of(2026, 10, 20, 21, 0), repeat = Repeat(RepeatUnit.WEEK))
        planning.saveEvent(event, now)
        val reminder = Reminder(NodeId.random(), event.id, 60)
        planning.saveReminder(reminder)
        planning.saveReminder(reminder.copy(snoozedUntil = now))
        planning.recordCompletion(TaskCompletion(task.id, now, task.dueDate))

        assertEquals(task, planning.task(task.id))
        assertEquals(event, planning.event(event.id))
        assertEquals(listOf(reminder.copy(snoozedUntil = now)), planning.reminders())
        assertEquals(project, projects.project(project.id))
        assertEquals(listOf(milestone), projects.milestones())
        assertEquals(1, planning.observeCompletions().first().size)

        // Buchung dem Projekt zuordnen, dann Projekt löschen: Buchung + Aufgabe bleiben, Rest räumt sich auf
        val tx = expense(34_900)
        ledger.saveTransaction(tx, now)
        links.link(NodeLink(NodeId.random(), tx.id, project.id, LinkRelation.PART_OF), now)
        projects.deleteProject(project.id)
        assertNull(planning.task(task.id)!!.projectId)
        assertTrue(projects.milestones().isEmpty())
        assertEquals(tx, ledger.transaction(tx.id))
        assertTrue(links.observeLinksOf(tx.id).first().isEmpty())
        assertTrue(RoomIntegrityChecker(db).run().isHealthy)

        // Termin löschen → Erinnerung weg; Aufgabe löschen → Historie weg
        planning.deleteEvent(event.id)
        assertTrue(planning.reminders().isEmpty())
        planning.deleteTask(task.id)
        assertTrue(planning.observeCompletions().first().isEmpty())
    }

    @Test
    fun taskTriggerRejectsRepeatWithoutDueDate() = runTest {
        try {
            RoomPlanningRepository(db).saveTask(Task(NodeId.random(), "x", repeat = Repeat(RepeatUnit.WEEK)), now)
            fail("Trigger hätte greifen müssen")
        } catch (e: SQLiteException) {
            assertTrue(e.message.orEmpty().contains("LIFEOS_TASK_REPEAT_DUE"))
        }
    }
}
