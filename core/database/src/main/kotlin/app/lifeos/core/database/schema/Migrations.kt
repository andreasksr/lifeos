package app.lifeos.core.database.schema

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Alle Schema-Migrationen.
 *
 * Regeln (verbindlich, siehe CLAUDE.md):
 *  1. Jede Versionserhöhung bekommt eine explizite Migration – niemals destruktiv.
 *  2. Jede Migration endet mit [TriggerInstaller.reinstall] (löscht und erneuert alle Trigger).
 *  3. Jede Migration bekommt einen Test: `tools/schema_test.py::MigrationTest` (SQLite) und
 *     `MigrationInstrumentedTest` (Room `MigrationTestHelper` auf dem Emulator).
 *  4. Das exportierte Schema-JSON (`core/database/schemas/`) wird mit eingecheckt.
 */
object Migrations {

    /** v1 → v2: wiederkehrende Zahlungen, Fälligkeitsvermerke, Gesamtbudget, erweiterte Buchungsarten. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SchemaSql.MIGRATION_1_2.forEach(db::execSQL)
            TriggerInstaller.reinstall(db)
        }
    }

    /** v2 → v3: Aufgaben, Erledigungs-Historie, Termine, Erinnerungen, Projekte, Meilensteine. Rein additiv. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SchemaSql.MIGRATION_2_3.forEach(db::execSQL)
            TriggerInstaller.reinstall(db)
        }
    }

    /** v3 → v4: Fahrzeuge, Inventar, Dokumente, Weiterbildung. Rein additiv – bestehende Daten bleiben unberührt. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SchemaSql.MIGRATION_3_4.forEach(db::execSQL)
            TriggerInstaller.reinstall(db)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}

/**
 * Löscht alle bekannten Trigger und legt die aktuellen Definitionen an. Idempotent.
 *
 * Bei verketteten Migrationen (z. B. 1→2→3) existieren in Zwischenschritten noch nicht alle
 * Tabellen. Trigger auf noch fehlende Tabellen werden dann übersprungen – spätestens die letzte
 * Migration und jedes Öffnen der Datenbank installieren den vollständigen Satz.
 */
object TriggerInstaller {
    private val NAME = Regex("CREATE TRIGGER IF NOT EXISTS `(\\w+)`")
    private val TARGET = Regex("\\bON `(\\w+)`")

    val names: List<String> = SchemaSql.TRIGGERS.map { sql ->
        requireNotNull(NAME.find(sql)) { "Trigger ohne Namen: $sql" }.groupValues[1]
    }

    fun targetTable(sql: String): String = requireNotNull(TARGET.find(sql)) { "Trigger ohne Zieltabelle: $sql" }.groupValues[1]

    fun reinstall(db: SupportSQLiteDatabase) {
        (names + SchemaSql.RETIRED_TRIGGER_NAMES).forEach { db.execSQL("DROP TRIGGER IF EXISTS `$it`") }
        val tables = HashSet<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c -> while (c.moveToNext()) tables += c.getString(0) }
        SchemaSql.TRIGGERS.filter { targetTable(it) in tables }.forEach(db::execSQL)
    }
}
