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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}

/** Löscht alle bekannten Trigger und legt die aktuellen Definitionen an. Idempotent. */
object TriggerInstaller {
    private val NAME = Regex("CREATE TRIGGER IF NOT EXISTS `(\\w+)`")

    val names: List<String> = SchemaSql.TRIGGERS.map { sql ->
        requireNotNull(NAME.find(sql)) { "Trigger ohne Namen: $sql" }.groupValues[1]
    }

    fun reinstall(db: SupportSQLiteDatabase) {
        (names + SchemaSql.RETIRED_TRIGGER_NAMES).forEach { db.execSQL("DROP TRIGGER IF EXISTS `$it`") }
        SchemaSql.TRIGGERS.forEach(db::execSQL)
    }
}
