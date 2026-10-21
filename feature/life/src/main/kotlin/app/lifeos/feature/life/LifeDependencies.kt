package app.lifeos.feature.life

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.life.AddManualSession
import app.lifeos.core.domain.life.AssetRepository
import app.lifeos.core.domain.life.DeleteDocument
import app.lifeos.core.domain.life.DocumentRepository
import app.lifeos.core.domain.life.DocumentStore
import app.lifeos.core.domain.life.ImportDocument
import app.lifeos.core.domain.life.LearningRepository
import app.lifeos.core.domain.life.SaveAsset
import app.lifeos.core.domain.life.SaveExam
import app.lifeos.core.domain.life.SaveSubject
import app.lifeos.core.domain.life.SaveVehicle
import app.lifeos.core.domain.life.SaveVehicleEvent
import app.lifeos.core.domain.life.SeedExamTargets
import app.lifeos.core.domain.life.StartStudy
import app.lifeos.core.domain.life.StopStudy
import app.lifeos.core.domain.life.VehicleRepository
import app.lifeos.core.domain.links.LinkNodes
import app.lifeos.core.domain.ports.LedgerRepository
import app.lifeos.core.domain.ports.LinkRepository
import app.lifeos.core.domain.projects.UnlinkNodes
import app.lifeos.core.model.Document
import kotlinx.coroutines.CoroutineScope

/**
 * Zugriff auf die verschlüsselten Dokumentdateien. Die App implementiert das (Tresor + FileProvider);
 * das Feature sieht nie Dateipfade oder Schlüssel.
 */
interface DocumentAccess {
    /** Entschlüsselter Inhalt (für die Vorschau im Speicher). */
    suspend fun read(document: Document): ByteArray

    /** Öffnet das Dokument in einer anderen App (temporäre, nur lesbare Freigabe). `false` = keine passende App. */
    suspend fun openExternally(document: Document): Boolean

    /** Größengrenze für Importe in Bytes. */
    val maxBytes: Long
}

/** Alles für „Mein Leben": Fahrzeuge, Inventar, Dokumente, Weiterbildung. */
class LifeDependencies(
    val vehicles: VehicleRepository,
    val assets: AssetRepository,
    val documents: DocumentRepository,
    store: DocumentStore,
    val documentAccess: DocumentAccess,
    val learning: LearningRepository,
    /** Nur lesend: Unterrichtstermine aus der Planung. */
    val planning: app.lifeos.core.domain.planning.PlanningRepository,
    val ledger: LedgerRepository,
    val links: LinkRepository,
    val clock: LifeClock,
    ids: IdGenerator,
    val background: CoroutineScope,
    /** Zeitzone für Lernstatistik (Woche/Monat). */
    val zone: () -> java.time.ZoneId = { java.time.ZoneId.systemDefault() },
) {
    val saveVehicle = SaveVehicle(vehicles, ids, clock)
    val saveVehicleEvent = SaveVehicleEvent(vehicles, ids, clock)
    val saveAsset = SaveAsset(assets, ids, clock)
    val importDocument = ImportDocument(documents, store, ids, clock)
    val deleteDocument = DeleteDocument(documents, store)
    val startStudy = StartStudy(learning, ids, clock)
    val stopStudy = StopStudy(learning, clock)
    val addManualSession = AddManualSession(learning, ids, clock)
    val seedExamTargets = SeedExamTargets(learning, ids, clock)
    val saveExam = SaveExam(learning, clock)
    val saveSubject = SaveSubject(learning, ids, clock)
    val linkNodes = LinkNodes(links, ids, clock)
    val unlink = UnlinkNodes(links)
    val newId: () -> app.lifeos.core.model.NodeId = { ids.newId() }
}
