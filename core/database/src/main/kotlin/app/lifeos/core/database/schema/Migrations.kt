package app.lifeos.core.database.schema

import androidx.room.migration.Migration

/**
 * Alle Schema-Migrationen.
 *
 * Regeln (verbindlich, siehe CLAUDE.md):
 *  1. Jede Versionserhöhung bekommt eine explizite Migration – niemals destruktiv.
 *  2. Jede Migration endet mit `SchemaSql.TRIGGERS.forEach(db::execSQL)`.
 *  3. Jede Migration bekommt einen `MigrationTestHelper`-Test mit echten Altdaten.
 *  4. Das exportierte Schema-JSON (`core/database/schemas/`) wird mit eingecheckt.
 *
 * Beispiel für v1 → v2:
 * ```
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE ledger_transaction ADD COLUMN receipt_id TEXT")
 *         SchemaSql.TRIGGERS.forEach(db::execSQL)
 *     }
 * }
 * ```
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
