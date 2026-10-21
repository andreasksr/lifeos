package app.lifeos.core.domain.testing

import app.lifeos.core.domain.life.AssetRepository
import app.lifeos.core.domain.life.DocumentRepository
import app.lifeos.core.domain.life.DocumentStore
import app.lifeos.core.domain.life.LearningRepository
import app.lifeos.core.domain.life.VehicleRepository
import app.lifeos.core.model.Asset
import app.lifeos.core.model.Document
import app.lifeos.core.model.Exam
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.StudySession
import app.lifeos.core.model.Subject
import app.lifeos.core.model.Topic
import app.lifeos.core.model.Vehicle
import app.lifeos.core.model.VehicleEvent
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant

class FakeVehicleRepository : VehicleRepository {
    val vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val events = MutableStateFlow<List<VehicleEvent>>(emptyList())
    override fun observeVehicles() = vehicles
    override fun observeEvents() = events
    override suspend fun vehicle(id: NodeId) = vehicles.value.firstOrNull { it.id == id }
    override suspend fun events() = events.value
    override suspend fun saveVehicle(vehicle: Vehicle, now: Instant) {
        vehicles.value = vehicles.value.filterNot { it.id == vehicle.id } + vehicle
    }
    override suspend fun deleteVehicle(id: NodeId) {
        vehicles.value = vehicles.value.filterNot { it.id == id }
        events.value = events.value.filterNot { it.vehicleId == id }
    }
    override suspend fun saveEvent(event: VehicleEvent, now: Instant) {
        events.value = events.value.filterNot { it.id == event.id } + event
    }
    override suspend fun deleteEvent(id: NodeId) {
        events.value = events.value.filterNot { it.id == id }
    }
}

class FakeAssetRepository : AssetRepository {
    val assets = MutableStateFlow<List<Asset>>(emptyList())
    override fun observeAssets() = assets
    override suspend fun asset(id: NodeId) = assets.value.firstOrNull { it.id == id }
    override suspend fun saveAsset(asset: Asset, now: Instant) {
        assets.value = assets.value.filterNot { it.id == asset.id } + asset
    }
    override suspend fun deleteAsset(id: NodeId) {
        assets.value = assets.value.filterNot { it.id == id }
    }
}

class FakeDocumentRepository : DocumentRepository {
    val docs = MutableStateFlow<List<Document>>(emptyList())
    override fun observeDocuments() = docs
    override suspend fun document(id: NodeId) = docs.value.firstOrNull { it.id == id }
    override suspend fun findBySha(sha256: String) = docs.value.firstOrNull { it.sha256 == sha256 }
    override suspend fun saveDocument(document: Document, now: Instant) {
        docs.value = docs.value.filterNot { it.id == document.id } + document
    }
    override suspend fun deleteDocument(id: NodeId) {
        docs.value = docs.value.filterNot { it.id == id }
    }
    override suspend fun countBySha(sha256: String) = docs.value.count { it.sha256 == sha256 }
}

/** Inhaltsadressierter Speicher im Arbeitsspeicher – wie der echte Tresor, nur unverschlüsselt. */
class FakeDocumentStore : DocumentStore {
    val files = linkedMapOf<String, ByteArray>()
    override suspend fun store(input: InputStream): DocumentStore.Stored {
        val bytes = input.readBytes()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val present = files.containsKey(sha)
        files[sha] = bytes
        return DocumentStore.Stored(sha, bytes.size.toLong(), present)
    }
    override suspend fun delete(sha256: String) {
        files.remove(sha256)
    }
}

class FakeLearningRepository : LearningRepository {
    val subjects = MutableStateFlow<List<Subject>>(emptyList())
    val topics = MutableStateFlow<List<Topic>>(emptyList())
    val sessions = MutableStateFlow<List<StudySession>>(emptyList())
    val exams = MutableStateFlow<List<Exam>>(emptyList())
    override fun observeSubjects() = subjects
    override fun observeTopics() = topics
    override fun observeSessions() = sessions
    override fun observeExams() = exams
    override suspend fun subjects() = subjects.value
    override suspend fun sessions() = sessions.value
    override suspend fun exams() = exams.value
    override suspend fun saveSubject(subject: Subject, now: Instant) {
        subjects.value = subjects.value.filterNot { it.id == subject.id } + subject
    }
    override suspend fun deleteSubject(id: NodeId) {
        subjects.value = subjects.value.filterNot { it.id == id }
    }
    override suspend fun saveTopic(topic: Topic, now: Instant) {
        topics.value = topics.value.filterNot { it.id == topic.id } + topic
    }
    override suspend fun deleteTopic(id: NodeId) {
        topics.value = topics.value.filterNot { it.id == id }
    }
    override suspend fun saveSession(session: StudySession) {
        // Wie der Datenbank-Trigger: höchstens eine laufende Sitzung
        check(session.end != null || sessions.value.none { it.running && it.id != session.id }) { "LIFEOS_SESSION_RUNNING" }
        sessions.value = sessions.value.filterNot { it.id == session.id } + session
    }
    override suspend fun deleteSession(id: NodeId) {
        sessions.value = sessions.value.filterNot { it.id == id }
    }
    override suspend fun saveExam(exam: Exam, now: Instant) {
        exams.value = exams.value.filterNot { it.id == exam.id } + exam
    }
    override suspend fun deleteExam(id: NodeId) {
        exams.value = exams.value.filterNot { it.id == id }
    }
}
