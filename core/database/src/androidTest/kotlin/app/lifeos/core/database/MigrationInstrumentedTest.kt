package app.lifeos.core.database

import android.database.sqlite.SQLiteException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lifeos.core.database.schema.Migrations
import app.lifeos.core.database.schema.TriggerInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * „Ein bereits gespeicherter Datensatz bleibt nach einem App-Update erhalten" (Auftrag Teil 15):
 * Echte v1-Datenbank (aus dem eingecheckten 1.json) mit Daten → Room-Migration → Room validiert das
 * Ergebnis gegen 2.json → Daten unverändert, neue Regeln aktiv.
 */
@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {

    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LifeOsDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsAllDataAndActivatesNewRules() {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO node VALUES ('00000000-0000-4000-8000-000000000001', 'ACCOUNT', 'Haushaltskasse', 0, 0, NULL, 0)")
            execSQL("INSERT INTO account VALUES ('00000000-0000-4000-8000-000000000001', 'CHECKING', 'EUR', 1000, 0)")
            execSQL("INSERT INTO node VALUES ('00000000-0000-4000-8000-000000000002', 'CATEGORY', 'Lebensmittel', 0, 0, NULL, 0)")
            execSQL("INSERT INTO category VALUES ('00000000-0000-4000-8000-000000000002', NULL, 'EXPENSE', 0)")
            execSQL("INSERT INTO node VALUES ('00000000-0000-4000-8000-000000000003', 'TRANSACTION', 'Wochenmarkt', 0, 0, NULL, 0)")
            execSQL(
                "INSERT INTO ledger_transaction VALUES ('00000000-0000-4000-8000-000000000003', 'EXPENSE', " +
                    "'00000000-0000-4000-8000-000000000001', NULL, 4299, 'EUR', '2026-10-18', 'Wochenmarkt', NULL)",
            )
            execSQL(
                "INSERT INTO transaction_split VALUES ('00000000-0000-4000-8000-000000000004', " +
                    "'00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000002', 4299, NULL, 0)",
            )
            execSQL("INSERT INTO app_setting VALUES ('ui.motion.enabled', '0')")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 2, true, Migrations.MIGRATION_1_2)

        db.query("SELECT amount_minor, payee, booking_date FROM ledger_transaction").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4299L, c.getLong(0))
            assertEquals("Wochenmarkt", c.getString(1))
            assertEquals("2026-10-18", c.getString(2))
        }
        db.query("SELECT value FROM app_setting WHERE `key` = 'ui.motion.enabled'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("0", c.getString(0))
        }
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger'").use { c ->
            c.moveToFirst()
            assertEquals(TriggerInstaller.names.size, c.getInt(0))
        }
        // Neue Regel (v2): Erstattung mit Einnahmekategorie wird abgewiesen
        try {
            db.execSQL("INSERT INTO node VALUES ('00000000-0000-4000-8000-000000000005', 'TRANSACTION', 'x', 0, 0, NULL, 0)")
            db.execSQL(
                "INSERT INTO ledger_transaction VALUES ('00000000-0000-4000-8000-000000000005', 'REFUND', " +
                    "'00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001', 5, 'EUR', '2026-10-19', NULL, NULL)",
            )
            fail("Trigger hätte greifen müssen")
        } catch (e: SQLiteException) {
            assertTrue(e.message.orEmpty().contains("LIFEOS_TX_COUNTER_ACCOUNT"))
        }
        db.close()
    }
}
