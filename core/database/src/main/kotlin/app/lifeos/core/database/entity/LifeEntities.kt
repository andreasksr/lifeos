package app.lifeos.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Schema v4 – muss exakt SchemaSql.TABLE_* / INDEX_* entsprechen (geprüft durch schema_test.py gegen 4.json).

@Entity(
    tableName = "vehicle",
    foreignKeys = [ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["node_id"], onDelete = ForeignKey.CASCADE)],
)
data class VehicleEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "make") val make: String,
    @ColumnInfo(name = "model") val model: String,
    @ColumnInfo(name = "build_year") val buildYear: Int?,
    @ColumnInfo(name = "plate") val plate: String?,
    @ColumnInfo(name = "vin") val vin: String?,
    @ColumnInfo(name = "odometer_km") val odometerKm: Int?,
    @ColumnInfo(name = "purchase_date") val purchaseDate: String?,
    @ColumnInfo(name = "purchase_price_minor") val purchasePriceMinor: Long?,
    @ColumnInfo(name = "note") val note: String?,
)

@Entity(
    tableName = "vehicle_event",
    foreignKeys = [
        ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["node_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["node_id"], childColumns = ["vehicle_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("vehicle_id")],
)
data class VehicleEventEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "vehicle_id") val vehicleId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "event_date") val eventDate: String,
    @ColumnInfo(name = "odometer_km") val odometerKm: Int?,
    @ColumnInfo(name = "done") val done: Boolean,
    @ColumnInfo(name = "note") val note: String?,
)

@Entity(
    tableName = "asset",
    foreignKeys = [ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["node_id"], onDelete = ForeignKey.CASCADE)],
)
data class AssetEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "manufacturer") val manufacturer: String?,
    @ColumnInfo(name = "model") val model: String?,
    @ColumnInfo(name = "serial_number") val serialNumber: String?,
    @ColumnInfo(name = "purchase_date") val purchaseDate: String?,
    @ColumnInfo(name = "price_minor") val priceMinor: Long?,
    @ColumnInfo(name = "warranty_until") val warrantyUntil: String?,
    @ColumnInfo(name = "location") val location: String?,
    @ColumnInfo(name = "note") val note: String?,
)

@Entity(
    tableName = "document",
    foreignKeys = [ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["node_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sha256"], unique = true)],
)
data class DocumentEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "sha256") val sha256: String,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "doc_date") val docDate: String?,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "ocr_text") val ocrText: String?,
)

@Entity(
    tableName = "subject",
    foreignKeys = [ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["node_id"], onDelete = ForeignKey.CASCADE)],
)
data class SubjectEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "part") val part: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "note") val note: String?,
)

@Entity(
    tableName = "topic",
    foreignKeys = [
        ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["node_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SubjectEntity::class, parentColumns = ["node_id"], childColumns = ["subject_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("subject_id")],
)
data class TopicEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "study_session",
    foreignKeys = [
        ForeignKey(entity = SubjectEntity::class, parentColumns = ["node_id"], childColumns = ["subject_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("subject_id"), Index("start_at")],
)
data class StudySessionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "subject_id") val subjectId: String?,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long?,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "note") val note: String?,
)

@Entity(
    tableName = "exam",
    foreignKeys = [ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["node_id"], onDelete = ForeignKey.CASCADE)],
)
data class ExamEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "part") val part: String,
    @ColumnInfo(name = "target_month") val targetMonth: String?,
    @ColumnInfo(name = "target_label") val targetLabel: String?,
    @ColumnInfo(name = "confirmed_date") val confirmedDate: String?,
    @ColumnInfo(name = "location") val location: String?,
    @ColumnInfo(name = "note") val note: String?,
)

// ── Lesesichten (Fachzeile + Knoten-Titel) ──

data class VehicleRow(@Embedded val vehicle: VehicleEntity, @ColumnInfo(name = "title") val title: String, @ColumnInfo(name = "is_sample") val isSample: Boolean)
data class VehicleEventRow(@Embedded val event: VehicleEventEntity, @ColumnInfo(name = "title") val title: String)
data class AssetRow(@Embedded val asset: AssetEntity, @ColumnInfo(name = "title") val title: String, @ColumnInfo(name = "is_sample") val isSample: Boolean)
data class DocumentRow(@Embedded val document: DocumentEntity, @ColumnInfo(name = "title") val title: String)
data class SubjectRow(@Embedded val subject: SubjectEntity, @ColumnInfo(name = "title") val title: String)
data class TopicRow(@Embedded val topic: TopicEntity, @ColumnInfo(name = "title") val title: String)
data class ExamRow(@Embedded val exam: ExamEntity, @ColumnInfo(name = "title") val title: String)
