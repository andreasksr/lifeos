package app.lifeos.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.IOException

/**
 * SQLCipher-spezifische Schritte der Datensicherung. Die App kennt dadurch keine Datenbankdetails.
 *
 * - [exportPlain]: Abzug der geöffneten, verschlüsselten Datenbank als unverschlüsselte Datei
 *   (`sqlcipher_export`). Nur für die anschließende Verschlüsselung in der Sicherungsdatei gedacht.
 * - [verifyPlain]: Prüft einen solchen Abzug (Integrität, Schema-Version, Kerntabelle).
 * - [importPlain]: Erzeugt aus einem Abzug eine NEUE Datei, verschlüsselt mit dem Schlüssel dieses Geräts.
 */
object DatabaseSnapshot {

    class Invalid(message: String) : IOException(message)

    fun exportPlain(db: SupportSQLiteDatabase, target: File, schemaVersion: Int) {
        target.delete()
        db.execSQL("ATTACH DATABASE ? AS backup_plain KEY ''", arrayOf<Any>(target.path))
        try {
            db.query("SELECT sqlcipher_export('backup_plain')").use { it.moveToFirst() }
            db.execSQL("PRAGMA backup_plain.user_version = $schemaVersion")
        } finally {
            db.execSQL("DETACH DATABASE backup_plain")
        }
    }

    /** Dokument-Hashes aus der geöffneten Datenbank. */
    fun documentHashes(db: SupportSQLiteDatabase): List<String> {
        val out = mutableListOf<String>()
        db.query("SELECT sha256 FROM document ORDER BY sha256").use { c -> while (c.moveToNext()) out += c.getString(0) }
        return out
    }

    fun verifyPlain(file: File, expectedVersion: Int) {
        System.loadLibrary("sqlcipher")
        val db = SQLiteDatabase.openDatabase(file.path, "", null, SQLiteDatabase.OPEN_READONLY, null, null)
        try {
            db.rawQuery("PRAGMA integrity_check", arrayOf<String>()).use { c ->
                if (!c.moveToFirst() || c.getString(0) != "ok") throw Invalid("Datenbank in der Sicherung beschädigt")
            }
            db.rawQuery("PRAGMA user_version", arrayOf<String>()).use { c ->
                if (!c.moveToFirst() || c.getInt(0) != expectedVersion) throw Invalid("Schema-Version passt nicht zum Inhaltsverzeichnis")
            }
            db.rawQuery("SELECT COUNT(*) FROM node", arrayOf<String>()).use { it.moveToFirst() }
        } finally {
            db.close()
        }
    }

    fun importPlain(plain: File, target: File, passphrase: ByteArray, schemaVersion: Int) {
        System.loadLibrary("sqlcipher")
        val db = SQLiteDatabase.openOrCreateDatabase(target, passphrase, null, null, null)
        try {
            db.execSQL("ATTACH DATABASE ? AS backup_plain KEY ''", arrayOf<Any>(plain.path))
            db.rawQuery("SELECT sqlcipher_export('main', 'backup_plain')", arrayOf<String>()).use { it.moveToFirst() }
            db.execSQL("DETACH DATABASE backup_plain")
            db.execSQL("PRAGMA user_version = $schemaVersion")
        } finally {
            db.close()
        }
    }
}
