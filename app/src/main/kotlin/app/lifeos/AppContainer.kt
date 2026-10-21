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
import app.lifeos.feature.projects.ProjectsDependencies
import app.lifeos.feature.projects.projectAttention
import app.lifeos.core.model.TaskStatus
import app.lifeos.notifications.AlarmReminderScheduler
import app.lifeos.notifications.AndroidNotificationAccess
import app.lifeos.notifications.ReminderNotifier
import kotlinx.coroutines.flow.map
import app.lifeos.core.database.repository.RoomAssetRepository
import app.lifeos.core.database.repository.RoomDocumentRepository
import app.lifeos.core.database.repository.RoomLearningRepository
import app.lifeos.core.database.repository.RoomVehicleRepository
import app.lifeos.core.security.vault.FileVault
import app.lifeos.documents.AndroidDocumentAccess
import app.lifeos.documents.VaultDocumentStore
import app.lifeos.documents.vaultDir
import app.lifeos.feature.life.LifeDependencies
import app.lifeos.backup.BackupManager
import app.lifeos.security.AppLock
import app.lifeos.feature.life.hub.lifeAttention
import app.lifeos.feature.ledger.LedgerDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce

/**
 * Manuelle Dependency Injection (ADR-003). Einzige Stelle, die Datenbank, Sicherheit und Features
 * miteinander verdrahtet. Features erhalten nur Domänen-Schnittstellen.
 */
class AppContainer private constructor(
    val context: Context,
    val database: LifeOsDatabase,
    val clock: LifeClock,
    val ids: IdGenerator,
    documentKey: ByteArray,
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

    val projects = ProjectsDependencies(
        projects = projectRepository,
        planning = planningRepository,
        ledger = ledgerRepository,
        links = linkRepository,
        clock = clock,
        ids = ids,
        toggleTask = { task -> if (task.status == TaskStatus.DONE) planning.reopenTask(task.id) else planning.completeTask(task.id) },
        background = appScope,
    )

    // ── Mein Leben: Fahrzeuge, Inventar, Dokumente, Weiterbildung ──
    val vault = FileVault(vaultDir(context), documentKey).also { documentKey.fill(0) }
    val documentStore = VaultDocumentStore(vault)
    val documentAccess = AndroidDocumentAccess(context, vault)
    val vehicleRepository = RoomVehicleRepository(database)
    val assetRepository = RoomAssetRepository(database)
    val documentRepository = RoomDocumentRepository(database)
    val learningRepository = RoomLearningRepository(database)
    val life = LifeDependencies(
        vehicles = vehicleRepository,
        assets = assetRepository,
        documents = documentRepository,
        store = documentStore,
        documentAccess = documentAccess,
        learning = learningRepository,
        planning = planningRepository,
        ledger = ledgerRepository,
        links = linkRepository,
        clock = clock,
        ids = ids,
        background = appScope,
    )

    val appLock = AppLock()

    /** Hält das Startbildschirm-Widget aktuell: bei jeder Änderung an Aufgaben, Terminen oder der App-Sperre. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun startWidgetSync() {
        appScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.flow.combine(planningRepository.observeTasks(), planningRepository.observeEvents(), settings.appLockEnabled) { _, _, _ -> Unit }
                .debounce(800)
                .collect { app.lifeos.widget.LifeOsWidget.refresh(context) }
        }
    }

    /** Schreibt eine passwortgeschützte Sicherung (Datenbank + Dokumente) nach [out]. Blockierend – im Hintergrund aufrufen. */
    fun exportBackup(out: java.io.OutputStream, passphrase: CharArray): BackupManager.Summary {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        return BackupManager(backupWorkDir(context)).export(database.openHelper.writableDatabase, LifeOsDatabase.VERSION, vault, out, passphrase, version)
    }

    /** Entfernt Tresor-Dateien ohne Datensatz (z. B. nach abgebrochenem Import) und alte Freigabe-Kopien. */
    suspend fun cleanUpFiles() {
        AndroidDocumentAccess.clearShared(context)
        val referenced = documentRepository.referencedHashes()
        vault.retainOnly(referenced)
    }

    val home = HomeDependencies(
        settings = settingsRepository,
        clock = clock,
        finance = { month -> ledger.observeMonth(month) },
        extraSections = listOf(
            object : HomeSectionSource {
                override val id = HomeSectionId.PLANNING
                override val attention = planningRepository.observeTasks().map { tasks -> AgendaBuilder.attention(tasks, clock.today()) }
            },
            object : HomeSectionSource {
                override val id = HomeSectionId.PROJECTS
                override val attention = projectAttention(projects) { clock.today() }
            },
            object : HomeSectionSource {
                override val id = HomeSectionId.LEARNING
                override val attention = lifeAttention(life)
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
                    val documentKey = FileVault.deriveKey(key.passphrase)
                    key.passphrase.fill(0)
                    StartResult.Ready(AppContainer(context.applicationContext, db, LifeClock.System, IdGenerator.Random, documentKey))
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
            vaultDir(context).deleteRecursively()
            context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("lifeos.db.key") }?.forEach { it.delete() }
        }
    }
}

fun backupWorkDir(context: Context) = java.io.File(context.noBackupFilesDir, "backup-work")

sealed interface StartResult {
    data class Ready(val container: AppContainer) : StartResult
    data class KeyLost(val reason: String) : StartResult
}
