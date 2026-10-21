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
import app.lifeos.core.database.repository.RoomPlanningRepository
import app.lifeos.core.database.repository.RoomProjectRepository
import app.lifeos.core.domain.home.HomeSectionId
import app.lifeos.core.domain.planning.AgendaBuilder
import app.lifeos.feature.home.HomeDependencies
import app.lifeos.feature.home.HomeSectionSource
import app.lifeos.feature.planning.PlanningDependencies
import app.lifeos.notifications.AlarmReminderScheduler
import app.lifeos.notifications.AndroidNotificationAccess
import app.lifeos.notifications.ReminderNotifier
import kotlinx.coroutines.flow.map
import app.lifeos.feature.ledger.LedgerDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manuelle Dependency Injection (ADR-003). Einzige Stelle, die Datenbank, Sicherheit und Features
 * miteinander verdrahtet. Features erhalten nur Domänen-Schnittstellen.
 */
class AppContainer private constructor(
    val context: Context,
    val database: LifeOsDatabase,
    val clock: LifeClock,
    val ids: IdGenerator,
) {
    /** Lebt so lange wie die App – für Aktionen, die einen Bildschirm überdauern (z. B. Rückgängig). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val ledgerRepository = RoomLedgerRepository(database)
    val settingsRepository = RoomSettingsRepository(database)
    val linkRepository = RoomLinkRepository(database)

    val settings = AppSettings(settingsRepository)
    val integrityChecker: IntegrityChecker = RoomIntegrityChecker(database)
    val linkNodes = LinkNodes(linkRepository, ids, clock)
    val ensureLedgerDefaults = EnsureLedgerDefaults(ledgerRepository, settingsRepository, ids, clock)
    val ledger = LedgerDependencies(ledgerRepository, linkRepository, clock, ids, appScope)

    // ── Planung & Projekte ──
    val planningRepository = RoomPlanningRepository(database)
    val projectRepository = RoomProjectRepository(database)
    val notificationAccess = AndroidNotificationAccess(context)
    val reminderScheduler = AlarmReminderScheduler(context, planningRepository, clock)
    val reminderNotifier = ReminderNotifier(context, planningRepository, clock, reminderScheduler)
    val planning = PlanningDependencies(
        repository = planningRepository,
        projects = projectRepository,
        links = linkRepository,
        clock = clock,
        ids = ids,
        scheduler = reminderScheduler,
        notifications = notificationAccess,
        background = appScope,
    )

    val home = HomeDependencies(
        settings = settingsRepository,
        clock = clock,
        finance = { month -> ledger.observeMonth(month) },
        extraSections = listOf(
            object : HomeSectionSource {
                override val id = HomeSectionId.PLANNING
                override val attention = planningRepository.observeTasks().map { tasks -> AgendaBuilder.attention(tasks, clock.today()) }
            },
        ),
    )

    companion object {
        /** Muss außerhalb des Main-Threads aufgerufen werden (Keystore-Zugriff). */
        fun create(context: Context): StartResult {
            val dbFile = context.getDatabasePath(LifeOsDatabase.FILE_NAME)
            return when (val key = DatabaseKeyManager(context).obtainPassphrase(databaseExists = dbFile.exists())) {
                is DatabaseKeyManager.KeyState.Ready -> {
                    val db = LifeOsDatabase.open(context, key.passphrase)
                    key.passphrase.fill(0)
                    StartResult.Ready(AppContainer(context.applicationContext, db, LifeClock.System, IdGenerator.Random))
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
