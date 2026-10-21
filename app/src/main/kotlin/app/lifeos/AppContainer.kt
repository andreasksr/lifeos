package app.lifeos

import android.content.Context
import app.lifeos.core.database.LifeOsDatabase
import app.lifeos.core.database.repository.RoomIntegrityChecker
import app.lifeos.core.database.repository.RoomLedgerRepository
import app.lifeos.core.database.repository.RoomLinkRepository
import app.lifeos.core.database.repository.RoomSettingsRepository
import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.ledger.EnsureLedgerDefaults
import app.lifeos.core.domain.links.LinkNodes
import app.lifeos.core.domain.ports.IntegrityChecker
import app.lifeos.core.domain.settings.AppSettings
import app.lifeos.core.security.DatabaseKeyManager
import app.lifeos.feature.ledger.LedgerDependencies

/**
 * Manuelle Dependency Injection (ADR-003). Einzige Stelle, die Datenbank, Sicherheit und Features
 * miteinander verdrahtet. Features erhalten nur Domänen-Schnittstellen.
 */
class AppContainer private constructor(
    val database: LifeOsDatabase,
    val clock: LifeClock,
    val ids: IdGenerator,
) {
    private val ledgerRepository = RoomLedgerRepository(database)
    private val settingsRepository = RoomSettingsRepository(database)

    val settings = AppSettings(settingsRepository)
    val integrityChecker: IntegrityChecker = RoomIntegrityChecker(database)
    private val linkRepository = RoomLinkRepository(database)
    val linkNodes = LinkNodes(linkRepository, ids, clock)
    val ensureLedgerDefaults = EnsureLedgerDefaults(ledgerRepository, settingsRepository, ids, clock)
    val ledger = LedgerDependencies(ledgerRepository, linkRepository, clock, ids)

    companion object {
        /** Muss außerhalb des Main-Threads aufgerufen werden (Keystore-Zugriff). */
        fun create(context: Context): StartResult {
            val dbFile = context.getDatabasePath(LifeOsDatabase.FILE_NAME)
            return when (val key = DatabaseKeyManager(context).obtainPassphrase(databaseExists = dbFile.exists())) {
                is DatabaseKeyManager.KeyState.Ready -> {
                    val db = LifeOsDatabase.open(context, key.passphrase)
                    key.passphrase.fill(0)
                    StartResult.Ready(AppContainer(db, LifeClock.System, IdGenerator.Random))
                }
                is DatabaseKeyManager.KeyState.Unrecoverable -> StartResult.KeyLost(key.reason)
            }
        }

        /**
         * Löscht Datenbank und Schlüsseldatei unwiderruflich. Nur nach doppelter Bestätigung im
         * Wiederherstellungsbildschirm aufrufen.
         */
        fun wipeLocalData(context: Context) {
            context.deleteDatabase(LifeOsDatabase.FILE_NAME)
            context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("lifeos.db.key") }?.forEach { it.delete() }
        }
    }
}

sealed interface StartResult {
    data class Ready(val container: AppContainer) : StartResult
    data class KeyLost(val reason: String) : StartResult
}
