package app.lifeos.core.database

import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lifeos.core.database.repository.RoomIntegrityChecker
import app.lifeos.core.database.repository.RoomLedgerRepository
import app.lifeos.core.database.repository.RoomLinkRepository
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

/**
 * Status: geschrieben, in der Entwicklungsumgebung NICHT ausgeführt (kein Android-SDK verfügbar).
 * Ausführen mit: ./gradlew :core:database:connectedAndroidTest
 */
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
        val tables = listOf("node", "link", "account", "category", "ledger_transaction", "transaction_split", "budget", "app_setting")
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
}
