package app.lifeos.core.database.repository

import app.lifeos.core.database.LifeOsDatabase
import app.lifeos.core.database.entity.AssetEntity
import app.lifeos.core.database.entity.AssetRow
import app.lifeos.core.database.entity.DocumentEntity
import app.lifeos.core.database.entity.DocumentRow
import app.lifeos.core.database.entity.ExamEntity
import app.lifeos.core.database.entity.ExamRow
import app.lifeos.core.database.entity.StudySessionEntity
import app.lifeos.core.database.entity.SubjectEntity
import app.lifeos.core.database.entity.SubjectRow
import app.lifeos.core.database.entity.TopicEntity
import app.lifeos.core.database.entity.TopicRow
import app.lifeos.core.database.entity.VehicleEntity
import app.lifeos.core.database.entity.VehicleEventEntity
import app.lifeos.core.database.entity.VehicleEventRow
import app.lifeos.core.database.entity.VehicleRow
import app.lifeos.core.domain.life.AssetRepository
import app.lifeos.core.domain.life.DocumentRepository
import app.lifeos.core.domain.life.LearningRepository
import app.lifeos.core.domain.life.VehicleRepository
import app.lifeos.core.model.Asset
import app.lifeos.core.model.AssetKind
import app.lifeos.core.model.Document
import app.lifeos.core.model.Exam
import app.lifeos.core.model.ExamPart
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.SessionSource
import app.lifeos.core.model.StudySession
import app.lifeos.core.model.Subject
import app.lifeos.core.model.Topic
import app.lifeos.core.model.TopicStatus
import app.lifeos.core.model.Vehicle
import app.lifeos.core.model.VehicleEvent
import app.lifeos.core.model.VehicleEventKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// ── Mapper ──

internal fun VehicleRow.toDomain() = Vehicle(
    id = NodeId(vehicle.nodeId), name = title, make = vehicle.make, model = vehicle.model,
    buildYear = vehicle.buildYear, plate = vehicle.plate, vin = vehicle.vin, odometerKm = vehicle.odometerKm,
    purchaseDate = vehicle.purchaseDate?.let(LocalDate::parse), purchasePriceMinor = vehicle.purchasePriceMinor,
    note = vehicle.note, isSample = isSample,
)

internal fun Vehicle.toEntity() = VehicleEntity(
    id.value, make, model, buildYear, plate, vin, odometerKm, purchaseDate?.toString(), purchasePriceMinor, note,
)

internal fun VehicleEventRow.toDomain() = VehicleEvent(
    id = NodeId(event.nodeId), vehicleId = NodeId(event.vehicleId), kind = VehicleEventKind.valueOf(event.kind),
    title = title, date = LocalDate.parse(event.eventDate), odometerKm = event.odometerKm, done = event.done, note = event.note,
)

internal fun VehicleEvent.toEntity() = VehicleEventEntity(id.value, vehicleId.value, kind.name, date.toString(), odometerKm, done, note)

internal fun AssetRow.toDomain() = Asset(
    id = NodeId(asset.nodeId), name = title, kind = AssetKind.valueOf(asset.kind), manufacturer = asset.manufacturer,
    model = asset.model, serialNumber = asset.serialNumber, purchaseDate = asset.purchaseDate?.let(LocalDate::parse),
    priceMinor = asset.priceMinor, warrantyUntil = asset.warrantyUntil?.let(LocalDate::parse), location = asset.location,
    note = asset.note, isSample = isSample,
)

internal fun Asset.toEntity() = AssetEntity(
    id.value, kind.name, manufacturer, model, serialNumber, purchaseDate?.toString(), priceMinor, warrantyUntil?.toString(), location, note,
)

internal fun DocumentRow.toDomain() = Document(
    id = NodeId(document.nodeId), title = title, fileName = document.fileName, mimeType = document.mimeType,
    sizeBytes = document.sizeBytes, sha256 = document.sha256, importedAt = Instant.ofEpochMilli(document.importedAt),
    docDate = document.docDate?.let(LocalDate::parse), note = document.note, ocrText = document.ocrText,
)

internal fun Document.toEntity() = DocumentEntity(
    id.value, fileName, mimeType, sizeBytes, sha256, importedAt.toEpochMilli(), docDate?.toString(), note, ocrText,
)

internal fun SubjectRow.toDomain() = Subject(NodeId(subject.nodeId), title, ExamPart.valueOf(subject.part), subject.sortOrder, subject.note)
internal fun Subject.toEntity() = SubjectEntity(id.value, part.name, sortOrder, note)

internal fun TopicRow.toDomain() = Topic(NodeId(topic.nodeId), NodeId(topic.subjectId), title, TopicStatus.valueOf(topic.status), topic.sortOrder)
internal fun Topic.toEntity() = TopicEntity(id.value, subjectId.value, status.name, sortOrder)

internal fun StudySessionEntity.toDomain() = StudySession(
    NodeId(id), subjectId?.let(::NodeId), Instant.ofEpochMilli(startAt), endAt?.let(Instant::ofEpochMilli), SessionSource.valueOf(source), note,
)
internal fun StudySession.toEntity() = StudySessionEntity(id.value, subjectId?.value, start.toEpochMilli(), end?.toEpochMilli(), source.name, note)

internal fun ExamRow.toDomain() = Exam(
    id = NodeId(exam.nodeId), title = title, part = ExamPart.valueOf(exam.part),
    targetMonth = exam.targetMonth?.let(YearMonth::parse), targetLabel = exam.targetLabel,
    confirmedDate = exam.confirmedDate?.let(LocalDate::parse), location = exam.location, note = exam.note,
)
internal fun Exam.toEntity() = ExamEntity(id.value, part.name, targetMonth?.toString(), targetLabel, confirmedDate?.toString(), location, note)

// ── Repositories ──

class RoomVehicleRepository(private val db: LifeOsDatabase) : VehicleRepository {
    private val dao get() = db.lifeDao()
    override fun observeVehicles(): Flow<List<Vehicle>> = dao.observeVehicles().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override fun observeEvents(): Flow<List<VehicleEvent>> = dao.observeVehicleEvents().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override suspend fun vehicle(id: NodeId): Vehicle? = dao.vehicle(id.value)?.toDomain()
    override suspend fun events(): List<VehicleEvent> = dao.vehicleEvents().map { it.toDomain() }
    override suspend fun saveVehicle(vehicle: Vehicle, now: Instant) =
        dao.saveVehicle(nodeEntity(vehicle.id, NodeType.VEHICLE, vehicle.name, now, archived = false, isSample = vehicle.isSample), vehicle.toEntity())

    /** Kaskade: vehicle → vehicle_event (+ Knoten per Trigger) → Erinnerungen, Links. Buchungen bleiben. */
    override suspend fun deleteVehicle(id: NodeId) { db.nodeDao().delete(id.value) }
    override suspend fun saveEvent(event: VehicleEvent, now: Instant) =
        dao.saveVehicleEvent(nodeEntity(event.id, NodeType.VEHICLE_EVENT, event.title, now, archived = false), event.toEntity())
    override suspend fun deleteEvent(id: NodeId) { db.nodeDao().delete(id.value) }
}

class RoomAssetRepository(private val db: LifeOsDatabase) : AssetRepository {
    private val dao get() = db.lifeDao()
    override fun observeAssets(): Flow<List<Asset>> = dao.observeAssets().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override suspend fun asset(id: NodeId): Asset? = dao.asset(id.value)?.toDomain()
    override suspend fun saveAsset(asset: Asset, now: Instant) =
        dao.saveAsset(nodeEntity(asset.id, NodeType.ASSET, asset.name, now, archived = false, isSample = asset.isSample), asset.toEntity())
    override suspend fun deleteAsset(id: NodeId) { db.nodeDao().delete(id.value) }
}

class RoomDocumentRepository(private val db: LifeOsDatabase) : DocumentRepository {
    private val dao get() = db.lifeDao()
    override fun observeDocuments(): Flow<List<Document>> = dao.observeDocuments().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override suspend fun document(id: NodeId): Document? = dao.document(id.value)?.toDomain()
    override suspend fun findBySha(sha256: String): Document? = dao.documentBySha(sha256)?.toDomain()
    override suspend fun saveDocument(document: Document, now: Instant) =
        dao.saveDocument(nodeEntity(document.id, NodeType.DOCUMENT, document.title, now, archived = false), document.toEntity())
    override suspend fun deleteDocument(id: NodeId) { db.nodeDao().delete(id.value) }
    override suspend fun countBySha(sha256: String): Int = dao.countBySha(sha256)

    /** Für die Aufräumprüfung des Dateispeichers: alle referenzierten Datei-Hashes. */
    suspend fun referencedHashes(): Set<String> = dao.allDocumentHashes().toSet()
}

class RoomLearningRepository(private val db: LifeOsDatabase) : LearningRepository {
    private val dao get() = db.lifeDao()
    override fun observeSubjects(): Flow<List<Subject>> = dao.observeSubjects().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override fun observeTopics(): Flow<List<Topic>> = dao.observeTopics().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override fun observeSessions(): Flow<List<StudySession>> = dao.observeSessions().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override fun observeExams(): Flow<List<Exam>> = dao.observeExams().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override suspend fun subjects(): List<Subject> = dao.subjects().map { it.toDomain() }
    override suspend fun sessions(): List<StudySession> = dao.sessions().map { it.toDomain() }
    override suspend fun exams(): List<Exam> = dao.exams().map { it.toDomain() }
    override suspend fun saveSubject(subject: Subject, now: Instant) =
        dao.saveSubject(nodeEntity(subject.id, NodeType.COURSE, subject.name, now, archived = false), subject.toEntity())
    override suspend fun deleteSubject(id: NodeId) { db.nodeDao().delete(id.value) }
    override suspend fun saveTopic(topic: Topic, now: Instant) =
        dao.saveTopic(nodeEntity(topic.id, NodeType.TOPIC, topic.title, now, archived = false), topic.toEntity())
    override suspend fun deleteTopic(id: NodeId) { db.nodeDao().delete(id.value) }
    override suspend fun saveSession(session: StudySession) = dao.saveSession(session.toEntity())
    override suspend fun deleteSession(id: NodeId) { dao.deleteSession(id.value) }
    override suspend fun saveExam(exam: Exam, now: Instant) =
        dao.saveExam(nodeEntity(exam.id, NodeType.EXAM, exam.title, now, archived = false), exam.toEntity())
    override suspend fun deleteExam(id: NodeId) { db.nodeDao().delete(id.value) }
}
