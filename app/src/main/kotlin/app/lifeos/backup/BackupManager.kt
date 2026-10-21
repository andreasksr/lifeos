package app.lifeos.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import app.lifeos.core.security.vault.BackupCipher
import app.lifeos.core.security.vault.FileVault
import app.lifeos.core.database.DatabaseSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Geräteunabhängige, passwortgeschützte Datensicherung (Auftrag Teil 12.3).
 *
 * Inhalt (ZIP, vollständig mit [BackupCipher] verschlüsselt):
 * - `manifest.json`  Format, Schema-Version, Zeitpunkt, Liste der Dokument-Hashes
 * - `lifeos.sqlite`  unverschlüsselter Abzug der Datenbank (per `sqlcipher_export`) – liegt nur
 *                    innerhalb der verschlüsselten Sicherung im Klartext vor
 * - `documents/<sha256>`  Dokumentdateien im Klartext (ebenfalls nur innerhalb der Sicherung)
 *
 * Wiederherstellung: Die Datenbank wird mit dem Schlüssel DIESES Geräts neu verschlüsselt, Dokumente
 * werden mit dem Dokumentschlüssel dieses Geräts neu in den Tresor geschrieben. Ältere Schema-Versionen
 * werden beim nächsten Öffnen von Room migriert. Der bisherige Datenbestand bleibt als
 * `*.pre-restore` erhalten, bis die wiederhergestellte Datenbank erfolgreich geöffnet wurde.
 */
class BackupManager(private val workDir: File) {

    data class Summary(val schemaVersion: Int, val documents: Int, val createdAt: Instant)

    class InvalidBackup(message: String) : IOException(message)

    // ───────────────────────────── Export ─────────────────────────────

    /**
     * Schreibt eine Sicherung nach [out] (wird geschlossen). [db] ist die geöffnete App-Datenbank.
     * Temporäre Klartext-Dateien werden in jedem Fall gelöscht.
     */
    fun export(db: SupportSQLiteDatabase, schemaVersion: Int, vault: FileVault, out: OutputStream, passphrase: CharArray, appVersion: String): Summary {
        require(BackupCipher.isAcceptablePassphrase(passphrase)) { "Passwort zu kurz" }
        workDir.mkdirs()
        val plain = File(workDir, "export.sqlite")
        plain.delete()
        try {
            DatabaseSnapshot.exportPlain(db, plain, schemaVersion)
            val hashes = DatabaseSnapshot.documentHashes(db)
            val createdAt = Instant.now()
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("app", "LifeOS")
                .put("appVersion", appVersion)
                .put("schemaVersion", schemaVersion)
                .put("createdAt", createdAt.toString())
                .put("documents", JSONArray(hashes))

            BackupCipher.encrypt(out, passphrase).use { sealed ->
                ZipOutputStream(sealed).use { zip ->
                    zip.putNextEntry(ZipEntry(MANIFEST))
                    zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry(DATABASE))
                    plain.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    hashes.forEach { sha ->
                        zip.putNextEntry(ZipEntry(DOCS + sha))
                        zip.write(vault.read(sha))
                        zip.closeEntry()
                    }
                }
            }
            return Summary(schemaVersion, hashes.size, createdAt)
        } finally {
            plain.delete()
        }
    }

    // ───────────────────────────── Prüfen & Entpacken ─────────────────────────────

    /** Entschlüsselt und prüft eine Sicherung in einen Zwischenordner. Verändert noch keine App-Daten. */
    fun stage(input: InputStream, passphrase: CharArray, maxSchemaVersion: Int): Staged {
        val dir = File(workDir, "restore-staging")
        dir.deleteRecursively()
        File(dir, "documents").mkdirs()
        var manifest: JSONObject? = null
        var hasDb = false
        try {
            ZipInputStream(BackupCipher.decrypt(input, passphrase)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    val target = when {
                        name == MANIFEST -> null
                        name == DATABASE -> File(dir, DATABASE).also { hasDb = true }
                        name.startsWith(DOCS) && HASH.matches(name.removePrefix(DOCS)) -> File(dir, name)
                        else -> throw InvalidBackup("Unerwarteter Inhalt in der Sicherung")
                    }
                    if (target == null) {
                        manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    } else {
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val m = manifest ?: throw InvalidBackup("Sicherung ohne Inhaltsverzeichnis")
            if (m.optInt("format") != FORMAT) throw InvalidBackup("Sicherungsformat wird nicht unterstützt")
            val version = m.optInt("schemaVersion")
            if (version < 1 || version > maxSchemaVersion) throw InvalidBackup("Sicherung stammt aus einer neueren LifeOS-Version – bitte zuerst die App aktualisieren")
            if (!hasDb) throw InvalidBackup("Sicherung ohne Datenbank")
            val docs = m.optJSONArray("documents") ?: JSONArray()
            val hashes = (0 until docs.length()).map { docs.getString(it) }
            hashes.forEach { sha ->
                val f = File(dir, DOCS + sha)
                if (!f.exists() || FileVault.sha256Hex(f.readBytes()) != sha) throw InvalidBackup("Dokument in der Sicherung beschädigt")
            }
            DatabaseSnapshot.verifyPlain(File(dir, DATABASE), version)
            return Staged(dir, version, hashes, Instant.parse(m.optString("createdAt", Instant.EPOCH.toString())))
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    class Staged(val dir: File, val schemaVersion: Int, val documents: List<String>, val createdAt: Instant)

    // ───────────────────────────── Einspielen ─────────────────────────────

    /**
     * Ersetzt Datenbank und Dokumenttresor durch den geprüften Stand. Die App-Datenbank MUSS geschlossen sein.
     * Bei einem Fehler wird der vorherige Zustand wiederhergestellt.
     */
    fun install(staged: Staged, dbFile: File, dbPassphrase: ByteArray, vaultDir: File, documentKey: ByteArray): Summary {
        val newDb = File(dbFile.parentFile, dbFile.name + ".restoring")
        val newVault = File(vaultDir.parentFile, vaultDir.name + ".restoring")
        deleteDb(newDb)
        newVault.deleteRecursively()
        try {
            // 1. Neue, mit dem Schlüssel dieses Geräts verschlüsselte Datenbank aus dem Klartext-Abzug
            DatabaseSnapshot.importPlain(File(staged.dir, DATABASE), newDb, dbPassphrase, staged.schemaVersion)
            // 2. Dokumente mit dem Schlüssel dieses Geräts neu in einen Tresor schreiben
            val vault = FileVault(newVault, documentKey, maxBytes = Long.MAX_VALUE)
            staged.documents.forEach { sha -> File(staged.dir, DOCS + sha).inputStream().use { vault.store(it) } }
        } catch (e: Exception) {
            deleteDb(newDb)
            newVault.deleteRecursively()
            throw e
        } finally {
            staged.dir.deleteRecursively()
        }

        // 3. Austausch – alter Stand bleibt als *.pre-restore erhalten
        val oldDb = File(dbFile.parentFile, dbFile.name + PRE)
        val oldVault = File(vaultDir.parentFile, vaultDir.name + PRE)
        deleteDb(oldDb)
        oldVault.deleteRecursively()
        moveDb(dbFile, oldDb)
        if (vaultDir.exists() && !vaultDir.renameTo(oldVault)) {
            moveDb(oldDb, dbFile)
            throw IOException("Dokumentspeicher konnte nicht ersetzt werden")
        }
        if (!newDb.renameTo(dbFile) || !newVault.renameTo(vaultDir)) {
            // Rückbau
            deleteDb(dbFile)
            vaultDir.deleteRecursively()
            moveDb(oldDb, dbFile)
            oldVault.renameTo(vaultDir)
            throw IOException("Wiederherstellung konnte nicht abgeschlossen werden – vorheriger Stand ist unverändert")
        }
        return Summary(staged.schemaVersion, staged.documents.size, staged.createdAt)
    }

    /** Nach erfolgreichem Start mit wiederhergestellten Daten: Sicherheitskopien entfernen. */
    fun discardPreRestore(dbFile: File, vaultDir: File) {
        deleteDb(File(dbFile.parentFile, dbFile.name + PRE))
        File(vaultDir.parentFile, vaultDir.name + PRE).deleteRecursively()
    }

    /** Holt den Stand vor der letzten Wiederherstellung zurück (falls der neue nicht startet). */
    fun rollback(dbFile: File, vaultDir: File): Boolean {
        val oldDb = File(dbFile.parentFile, dbFile.name + PRE)
        val oldVault = File(vaultDir.parentFile, vaultDir.name + PRE)
        if (!oldDb.exists()) return false
        deleteDb(dbFile)
        moveDb(oldDb, dbFile)
        if (oldVault.exists()) {
            vaultDir.deleteRecursively()
            oldVault.renameTo(vaultDir)
        }
        return true
    }

    private fun sidecars(f: File) = listOf(f, File(f.path + "-wal"), File(f.path + "-shm"), File(f.path + "-journal"))
    private fun deleteDb(f: File) = sidecars(f).forEach { it.delete() }
    private fun moveDb(from: File, to: File) {
        sidecars(from).zip(sidecars(to)).forEach { (a, b) -> if (a.exists()) a.renameTo(b) }
    }

    companion object {
        const val FORMAT = 1
        const val MANIFEST = "manifest.json"
        const val DATABASE = "lifeos.sqlite"
        const val DOCS = "documents/"
        const val PRE = ".pre-restore"
        const val FILE_EXTENSION = "lifeosbackup"
        private val HASH = Regex("[0-9a-f]{64}")
    }
}
