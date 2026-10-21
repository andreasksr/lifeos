package app.lifeos

import android.app.Application
import app.lifeos.backup.BackupManager
import app.lifeos.core.database.LifeOsDatabase
import app.lifeos.core.security.DatabaseKeyManager
import app.lifeos.core.security.vault.FileVault
import app.lifeos.documents.vaultDir
import app.lifeos.notifications.ReminderNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LifeOsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.createChannels(this)
    }

    private val lock = Mutex()
    private var started: StartResult.Ready? = null

    /** Öffnet Datenbank und Container einmalig im Hintergrund; danach sofort verfügbar. */
    suspend fun start(): StartResult = lock.withLock {
        started?.let { return@withLock it }
        val result = withContext(Dispatchers.IO) {
            val r = try {
                AppContainer.create(this@LifeOsApplication).also { if (it is StartResult.Ready) it.container.ensureLedgerDefaults() }
            } catch (e: Exception) {
                // Nach einer Wiederherstellung: lässt sich der neue Stand nicht öffnen, alten Stand zurückholen.
                if (!rollbackRestore()) throw e
                AppContainer.create(this@LifeOsApplication).also { if (it is StartResult.Ready) it.container.ensureLedgerDefaults() }
            }
            if (r is StartResult.Ready) {
                backupManager().discardPreRestore(getDatabasePath(LifeOsDatabase.FILE_NAME), vaultDir(this@LifeOsApplication))
                // Alarme nach App-Start/Update sicher neu planen (z. B. nach Datenwiederherstellung).
                runCatching { r.container.reminderScheduler.reschedule() }
                runCatching { r.container.cleanUpFiles() }
            }
            r
        }
        if (result is StartResult.Ready) started = result
        result
    }

    private fun backupManager() = BackupManager(backupWorkDir(this))

    private fun rollbackRestore(): Boolean {
        val db = getDatabasePath(LifeOsDatabase.FILE_NAME)
        val pre = java.io.File(db.parentFile, db.name + BackupManager.PRE)
        if (!pre.exists()) return false
        started?.container?.database?.close()
        return backupManager().rollback(db, vaultDir(this))
    }

    /**
     * Spielt eine Sicherung ein: erst vollständig prüfen (falsches Passwort, beschädigte Datei → Abbruch ohne
     * Änderung), dann Datenbank schließen und austauschen. Danach muss die App neu gestartet werden.
     */
    suspend fun restoreBackup(open: () -> java.io.InputStream, passphrase: CharArray): BackupManager.Summary = lock.withLock {
        withContext(Dispatchers.IO) {
            val manager = backupManager()
            val staged = open().use { manager.stage(it, passphrase, LifeOsDatabase.VERSION) }
            // Die laufende Oberfläche hält die Datenbank noch offen. Statt sie unter ihr wegzuschließen, wird
            // der WAL-Inhalt in die Hauptdatei geschrieben; danach werden die Dateien ausgetauscht und die App
            // sofort neu gestartet (offene Verbindungen zeigen bis dahin auf den alten, gesicherten Stand).
            started?.container?.database?.openHelper?.writableDatabase?.query("PRAGMA wal_checkpoint(TRUNCATE)")?.use { it.moveToFirst() }
            started = null
            val dbFile = getDatabasePath(LifeOsDatabase.FILE_NAME)
            val key = DatabaseKeyManager(this@LifeOsApplication).obtainPassphrase(databaseExists = dbFile.exists())
            if (key !is DatabaseKeyManager.KeyState.Ready) throw java.io.IOException("Geräteschlüssel nicht verfügbar")
            val docKey = FileVault.deriveKey(key.passphrase)
            try {
                manager.install(staged, dbFile, key.passphrase, vaultDir(this@LifeOsApplication), docKey)
            } finally {
                key.passphrase.fill(0)
                docKey.fill(0)
            }
        }
    }

    suspend fun wipeAndRestart(): StartResult {
        lock.withLock {
            started?.container?.database?.close()
            started = null
            withContext(Dispatchers.IO) { AppContainer.wipeLocalData(this@LifeOsApplication) }
        }
        return start()
    }
}
