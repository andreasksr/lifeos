package app.lifeos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.lifeos.core.database.LifeOsDatabase
import app.lifeos.core.database.entity.AssetEntity
import app.lifeos.core.database.entity.AssetRow
import app.lifeos.core.database.entity.DocumentEntity
import app.lifeos.core.database.entity.DocumentRow
import app.lifeos.core.database.entity.ExamEntity
import app.lifeos.core.database.entity.ExamRow
import app.lifeos.core.database.entity.NodeEntity
import app.lifeos.core.database.entity.StudySessionEntity
import app.lifeos.core.database.entity.SubjectEntity
import app.lifeos.core.database.entity.SubjectRow
import app.lifeos.core.database.entity.TopicEntity
import app.lifeos.core.database.entity.TopicRow
import app.lifeos.core.database.entity.VehicleEntity
import app.lifeos.core.database.entity.VehicleEventEntity
import app.lifeos.core.database.entity.VehicleEventRow
import app.lifeos.core.database.entity.VehicleRow
import kotlinx.coroutines.flow.Flow

/**
 * Fahrzeuge, Inventar, Dokumente und Weiterbildung. Schreibvorgänge legen Knoten + Fachzeile in EINER
 * Transaktion an (nie `REPLACE`, siehe CLAUDE.md Regel 4).
 */
@Dao
abstract class LifeDao(private val db: LifeOsDatabase) {
    private val nodes: NodeDao get() = db.nodeDao()

    // ── Fahrzeuge ──
    @Query("SELECT v.*, n.title AS title, n.is_sample AS is_sample FROM vehicle v JOIN node n ON n.id = v.node_id ORDER BY n.title")
    abstract fun observeVehicles(): Flow<List<VehicleRow>>

    @Query("SELECT v.*, n.title AS title, n.is_sample AS is_sample FROM vehicle v JOIN node n ON n.id = v.node_id WHERE v.node_id = :id")
    abstract suspend fun vehicle(id: String): VehicleRow?

    @Query("SELECT e.*, n.title AS title FROM vehicle_event e JOIN node n ON n.id = e.node_id ORDER BY e.event_date DESC")
    abstract fun observeVehicleEvents(): Flow<List<VehicleEventRow>>

    @Query("SELECT e.*, n.title AS title FROM vehicle_event e JOIN node n ON n.id = e.node_id ORDER BY e.event_date DESC")
    abstract suspend fun vehicleEvents(): List<VehicleEventRow>

    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertVehicle(e: VehicleEntity)
    @Update abstract suspend fun updateVehicle(e: VehicleEntity): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertVehicleEvent(e: VehicleEventEntity)
    @Update abstract suspend fun updateVehicleEvent(e: VehicleEventEntity): Int

    @Transaction
    open suspend fun saveVehicle(node: NodeEntity, e: VehicleEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertVehicle(e) else updateVehicle(e)
    }

    @Transaction
    open suspend fun saveVehicleEvent(node: NodeEntity, e: VehicleEventEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertVehicleEvent(e) else updateVehicleEvent(e)
    }

    // ── Inventar ──
    @Query("SELECT a.*, n.title AS title, n.is_sample AS is_sample FROM asset a JOIN node n ON n.id = a.node_id ORDER BY n.title")
    abstract fun observeAssets(): Flow<List<AssetRow>>

    @Query("SELECT a.*, n.title AS title, n.is_sample AS is_sample FROM asset a JOIN node n ON n.id = a.node_id WHERE a.node_id = :id")
    abstract suspend fun asset(id: String): AssetRow?

    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertAsset(e: AssetEntity)
    @Update abstract suspend fun updateAsset(e: AssetEntity): Int

    @Transaction
    open suspend fun saveAsset(node: NodeEntity, e: AssetEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertAsset(e) else updateAsset(e)
    }

    // ── Dokumente ──
    @Query("SELECT d.*, n.title AS title FROM document d JOIN node n ON n.id = d.node_id ORDER BY d.imported_at DESC")
    abstract fun observeDocuments(): Flow<List<DocumentRow>>

    @Query("SELECT d.*, n.title AS title FROM document d JOIN node n ON n.id = d.node_id WHERE d.node_id = :id")
    abstract suspend fun document(id: String): DocumentRow?

    @Query("SELECT d.*, n.title AS title FROM document d JOIN node n ON n.id = d.node_id WHERE d.sha256 = :sha")
    abstract suspend fun documentBySha(sha: String): DocumentRow?

    @Query("SELECT COUNT(*) FROM document WHERE sha256 = :sha")
    abstract suspend fun countBySha(sha: String): Int

    @Query("SELECT sha256 FROM document")
    abstract suspend fun allDocumentHashes(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertDocument(e: DocumentEntity)
    @Update abstract suspend fun updateDocument(e: DocumentEntity): Int

    @Transaction
    open suspend fun saveDocument(node: NodeEntity, e: DocumentEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertDocument(e) else updateDocument(e)
    }

    // ── Weiterbildung ──
    @Query("SELECT s.*, n.title AS title FROM subject s JOIN node n ON n.id = s.node_id ORDER BY s.part, s.sort_order, n.title")
    abstract fun observeSubjects(): Flow<List<SubjectRow>>

    @Query("SELECT s.*, n.title AS title FROM subject s JOIN node n ON n.id = s.node_id ORDER BY s.part, s.sort_order, n.title")
    abstract suspend fun subjects(): List<SubjectRow>

    @Query("SELECT t.*, n.title AS title FROM topic t JOIN node n ON n.id = t.node_id ORDER BY t.subject_id, t.sort_order, n.title")
    abstract fun observeTopics(): Flow<List<TopicRow>>

    @Query("SELECT * FROM study_session ORDER BY start_at DESC")
    abstract fun observeSessions(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_session ORDER BY start_at DESC")
    abstract suspend fun sessions(): List<StudySessionEntity>

    @Query("SELECT e.*, n.title AS title FROM exam e JOIN node n ON n.id = e.node_id ORDER BY e.part, n.title")
    abstract fun observeExams(): Flow<List<ExamRow>>

    @Query("SELECT e.*, n.title AS title FROM exam e JOIN node n ON n.id = e.node_id ORDER BY e.part, n.title")
    abstract suspend fun exams(): List<ExamRow>

    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertSubject(e: SubjectEntity)
    @Update abstract suspend fun updateSubject(e: SubjectEntity): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertTopic(e: TopicEntity)
    @Update abstract suspend fun updateTopic(e: TopicEntity): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertExam(e: ExamEntity)
    @Update abstract suspend fun updateExam(e: ExamEntity): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertSession(e: StudySessionEntity)
    @Update abstract suspend fun updateSession(e: StudySessionEntity): Int

    @Query("SELECT * FROM study_session WHERE id = :id")
    abstract suspend fun session(id: String): StudySessionEntity?

    @Query("DELETE FROM study_session WHERE id = :id")
    abstract suspend fun deleteSession(id: String): Int

    @Transaction
    open suspend fun saveSubject(node: NodeEntity, e: SubjectEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertSubject(e) else updateSubject(e)
    }

    @Transaction
    open suspend fun saveTopic(node: NodeEntity, e: TopicEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertTopic(e) else updateTopic(e)
    }

    @Transaction
    open suspend fun saveExam(node: NodeEntity, e: ExamEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertExam(e) else updateExam(e)
    }

    @Transaction
    open suspend fun saveSession(e: StudySessionEntity) {
        if (session(e.id) == null) insertSession(e) else updateSession(e)
    }
}
