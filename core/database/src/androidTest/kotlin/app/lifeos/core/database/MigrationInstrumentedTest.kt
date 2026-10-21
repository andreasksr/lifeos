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
        assertEquals(expectedTriggers(db), triggerCount(db))
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

    /** Verkettet 1 → 2 → 3, wie es ein Nutzer mit v0.1.0 beim Update auf 0.3.0 erlebt. */
    @Test
    fun migrate1To3KeepsDataAndPlanningRulesWork() {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO node VALUES ('00000000-0000-4000-8000-000000000001', 'ACCOUNT', 'Haushaltskasse', 0, 0, NULL, 0)")
            execSQL("INSERT INTO account VALUES ('00000000-0000-4000-8000-000000000001', 'CHECKING', 'EUR', 1000, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 3, true, Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
        db.query("SELECT title FROM node WHERE type = 'ACCOUNT'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Haushaltskasse", c.getString(0))
        }
        assertEquals(expectedTriggers(db), triggerCount(db))

        db.execSQL("INSERT INTO node VALUES ('00000000-0000-4000-8000-000000000010', 'PROJECT', 'Samurai', 0, 0, NULL, 0)")
        db.execSQL("INSERT INTO project VALUES ('00000000-0000-4000-8000-000000000010', NULL, NULL, 'ACTIVE', '2026-10-01', NULL, 'TASKS', NULL, NULL, NULL)")
        db.execSQL("INSERT INTO node VALUES ('00000000-0000-4000-8000-000000000011', 'TASK', 'Bremsen', 0, 0, NULL, 0)")
        db.execSQL(
            "INSERT INTO task VALUES ('00000000-0000-4000-8000-000000000011', NULL, 'OPEN', 'HIGH', '2026-10-25', NULL, NULL, 1, NULL, " +
                "'00000000-0000-4000-8000-000000000010', NULL)",
        )
        try {
            db.execSQL("UPDATE task SET due_date = '2026-13-01' WHERE node_id = '00000000-0000-4000-8000-000000000011'")
            fail("Trigger hätte greifen müssen – " + diagnostics(db))
        } catch (e: SQLiteException) {
            assertTrue("${e.message} – ${diagnostics(db)}", e.message.orEmpty().contains("LIFEOS_TASK_DATE"))
        }
        // 30. Februar: auf Android-SQLite 3.39 normalisiert date() ohne Modifikator nicht – Trigger nutzen '+0 days'.
        try {
            db.execSQL("UPDATE task SET due_date = '2026-02-30' WHERE node_id = '00000000-0000-4000-8000-000000000011'")
            fail("Trigger hätte greifen müssen – " + diagnostics(db))
        } catch (e: SQLiteException) {
            assertTrue("${e.message} – ${diagnostics(db)}", e.message.orEmpty().contains("LIFEOS_TASK_DATE"))
        }
        try {
            db.execSQL("UPDATE task SET status = 'MAYBE' WHERE node_id = '00000000-0000-4000-8000-000000000011'")
            fail("Trigger hätte greifen müssen – " + diagnostics(db))
        } catch (e: SQLiteException) {
            assertTrue("${e.message} – ${diagnostics(db)}", e.message.orEmpty().contains("LIFEOS_TASK_STATUS"))
        }
        // Projekt löschen → Aufgabe bleibt, ohne Projekt. (Room schaltet Fremdschlüssel in der App selbst
        // ein; die von MigrationTestHelper geöffnete Verbindung braucht das explizit.)
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM node WHERE id = '00000000-0000-4000-8000-000000000010'")
        db.query("SELECT project_id FROM task").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("project_id muss nach Projektlöschung NULL sein", c.isNull(0))
        }
        db.close()
    }

    /** Vollständige Kette 1 → 2 → 3 → 4 (Update von v0.1.0 auf 0.4.0): Daten bleiben, neue Regeln greifen. */
    @Test
    fun migrate1To4KeepsDataAndLifeRulesWork() {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO node VALUES ('00000000-0000-4000-8000-000000000001', 'ACCOUNT', 'Haushaltskasse', 0, 0, NULL, 0)")
            execSQL("INSERT INTO account VALUES ('00000000-0000-4000-8000-000000000001', 'CHECKING', 'EUR', 1000, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(
            name, 4, true, Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4,
        )
        db.query("SELECT opening_balance_minor FROM account").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1000L, c.getLong(0))
        }
        assertEquals(TriggerInstaller.names.size, triggerCount(db))
        db.execSQL("PRAGMA foreign_keys = ON")

        db.execSQL("INSERT INTO node VALUES ('00000000-0000-4000-8000-000000000020', 'VEHICLE', 'Samurai', 0, 0, NULL, 0)")
        db.execSQL("INSERT INTO vehicle VALUES ('00000000-0000-4000-8000-000000000020', 'Suzuki', 'Samurai', 1993, NULL, NULL, 150000, NULL, NULL, NULL)")
        db.execSQL("INSERT INTO node VALUES ('00000000-0000-4000-8000-000000000021', 'VEHICLE_EVENT', 'HU', 0, 0, NULL, 0)")
        try {
            db.execSQL("INSERT INTO vehicle_event VALUES ('00000000-0000-4000-8000-000000000021', '00000000-0000-4000-8000-000000000020', 'INSPECTION', '2027-02-30', NULL, 0, NULL)")
            fail("Trigger hätte greifen müssen – " + diagnostics(db))
        } catch (e: SQLiteException) {
            assertTrue(e.message.orEmpty().contains("LIFEOS_VEHICLE_EVENT_DATE"))
        }
        db.execSQL("INSERT INTO vehicle_event VALUES ('00000000-0000-4000-8000-000000000021', '00000000-0000-4000-8000-000000000020', 'INSPECTION', '2027-02-28', NULL, 0, NULL)")

        // Lernzeit: nur eine laufende Sitzung
        db.execSQL("INSERT INTO study_session VALUES ('s1', NULL, 1000, NULL, 'TIMER', NULL)")
        try {
            db.execSQL("INSERT INTO study_session VALUES ('s2', NULL, 2000, NULL, 'TIMER', NULL)")
            fail("Trigger hätte greifen müssen")
        } catch (e: SQLiteException) {
            assertTrue(e.message.orEmpty().contains("LIFEOS_SESSION_RUNNING"))
        }
        // Fahrzeug löschen → Ereignis samt Knoten weg
        db.execSQL("DELETE FROM node WHERE id = '00000000-0000-4000-8000-000000000020'")
        db.query("SELECT COUNT(*) FROM node WHERE type IN ('VEHICLE', 'VEHICLE_EVENT')").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    /** Hilft bei Abweichungen zwischen Android-SQLite und Referenz-SQLite (Version, date()-Verhalten). */
    private fun diagnostics(db: androidx.sqlite.db.SupportSQLiteDatabase): String =
        db.query("SELECT sqlite_version(), date('2026-02-30', '+0 days'), date('2026-13-01'), (SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND tbl_name='task')").use { c ->
            c.moveToFirst()
            "sqlite=${c.getString(0)} date(02-30)=${c.getString(1)} date(13-01)=${c.getString(2)} taskTrigger=${c.getInt(3)}"
        }

    private fun triggerCount(db: androidx.sqlite.db.SupportSQLiteDatabase): Int =
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger'").use { c -> c.moveToFirst(); c.getInt(0) }

    private fun expectedTriggers(db: androidx.sqlite.db.SupportSQLiteDatabase): Int {
        val tables = HashSet<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c -> while (c.moveToNext()) tables += c.getString(0) }
        return app.lifeos.core.database.schema.SchemaSql.TRIGGERS.count { TriggerInstaller.targetTable(it) in tables }
    }
}
