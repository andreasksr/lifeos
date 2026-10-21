package app.lifeos.core.domain.life

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.home.AttentionItem
import app.lifeos.core.domain.home.AttentionSeverity
import app.lifeos.core.domain.home.AttentionTarget
import app.lifeos.core.model.Asset
import app.lifeos.core.model.AssetKind
import app.lifeos.core.model.Document
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.NodeId
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ───────────────────────────── Inventar ─────────────────────────────

interface AssetRepository {
    fun observeAssets(): Flow<List<Asset>>
    suspend fun asset(id: NodeId): Asset?
    suspend fun saveAsset(asset: Asset, now: Instant)
    suspend fun deleteAsset(id: NodeId)
}

data class AssetDraft(
    val id: NodeId? = null,
    val name: String,
    val kind: AssetKind,
    val manufacturer: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val purchaseDate: LocalDate? = null,
    val priceMinor: Long? = null,
    val warrantyUntil: LocalDate? = null,
    val location: String? = null,
    val note: String? = null,
)

class SaveAsset(private val repo: AssetRepository, private val ids: IdGenerator, private val clock: LifeClock) {
    suspend operator fun invoke(d: AssetDraft): LifeResult<NodeId> {
        val errors = mutableListOf<LifeError>()
        val name = d.name.trim()
        if (name.isEmpty()) errors += LifeError.NameRequired
        if (name.length > 120) errors += LifeError.NameTooLong
        if (d.priceMinor != null && d.priceMinor < 0) errors += LifeError.InvalidAmount
        if (d.purchaseDate != null && d.warrantyUntil != null && d.warrantyUntil.isBefore(d.purchaseDate)) errors += LifeError.InvalidDate
        val existing = d.id?.let { repo.asset(it) }
        if (d.id != null && existing == null) errors += LifeError.NotFound
        if (errors.isNotEmpty()) return LifeResult.Invalid(errors)
        fun String?.c() = this?.trim()?.takeIf { it.isNotEmpty() }
        val a = Asset(
            id = d.id ?: ids.newId(), name = name, kind = d.kind,
            manufacturer = d.manufacturer.c(), model = d.model.c(), serialNumber = d.serialNumber.c(),
            purchaseDate = d.purchaseDate, priceMinor = d.priceMinor, warrantyUntil = d.warrantyUntil,
            location = d.location.c(), note = d.note.c(), isSample = existing?.isSample ?: false,
        )
        repo.saveAsset(a, clock.now())
        return LifeResult.Success(a.id)
    }
}

enum class WarrantyState { NONE, ACTIVE, EXPIRING, EXPIRED }

object AssetLogic {
    /** Garantie: läuft in ≤ 60 Tagen ab → EXPIRING. */
    fun warranty(a: Asset, today: LocalDate): WarrantyState {
        val until = a.warrantyUntil ?: return WarrantyState.NONE
        val days = ChronoUnit.DAYS.between(today, until)
        return when {
            days < 0 -> WarrantyState.EXPIRED
            days <= 60 -> WarrantyState.EXPIRING
            else -> WarrantyState.ACTIVE
        }
    }

    fun kindLabel(k: AssetKind): String = when (k) {
        AssetKind.TOOL -> "Werkzeug"
        AssetKind.ELECTRONICS -> "Elektronik"
        AssetKind.MACHINE -> "Maschine"
        AssetKind.HOUSEHOLD -> "Haushalt"
        AssetKind.OTHER -> "Sonstiges"
    }

    fun attention(assets: List<Asset>, today: LocalDate): List<AttentionItem> = assets.mapNotNull { a ->
        val until = a.warrantyUntil ?: return@mapNotNull null
        val days = ChronoUnit.DAYS.between(today, until)
        if (days in 0..30) {
            val whenText = when (days) {
                0L -> "heute"
                1L -> "morgen"
                else -> "in $days Tagen"
            }
            AttentionItem(a.name, "Garantie endet $whenText (${LifeFormat.numericDate(until)})", AttentionSeverity.INFO, AttentionTarget.Node(a.id), days)
        } else {
            null
        }
    }
}

// ───────────────────────────── Dokumente ─────────────────────────────

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    suspend fun document(id: NodeId): Document?
    suspend fun findBySha(sha256: String): Document?
    suspend fun saveDocument(document: Document, now: Instant)
    suspend fun deleteDocument(id: NodeId)
    /** Anzahl der Dokument-Datensätze, die auf dieselbe Datei verweisen. */
    suspend fun countBySha(sha256: String): Int
}

/** Verschlüsselter Dateispeicher (inhaltsadressiert über SHA-256). Implementiert in der App. */
interface DocumentStore {
    data class Stored(val sha256: String, val sizeBytes: Long, val alreadyPresent: Boolean)

    suspend fun store(input: InputStream): Stored
    suspend fun delete(sha256: String)
}

data class ImportResult(val documentId: NodeId, val duplicate: Boolean)

/** Datei überschreitet die Größengrenze des Dokumentenspeichers. */
class DocumentTooLargeException(val limitBytes: Long) : Exception("Datei größer als ${limitBytes / (1024 * 1024)} MB")

/**
 * Importiert eine Datei. Ist dieselbe Datei (gleicher Inhalt) schon vorhanden, wird KEIN zweiter Datensatz
 * und keine zweite Kopie angelegt – das bestehende Dokument wird zurückgegeben.
 */
class ImportDocument(
    private val repo: DocumentRepository,
    private val store: DocumentStore,
    private val ids: IdGenerator,
    private val clock: LifeClock,
) {
    suspend operator fun invoke(input: InputStream, fileName: String, mimeType: String, title: String? = null): ImportResult {
        val stored = store.store(input)
        repo.findBySha(stored.sha256)?.let { return ImportResult(it.id, duplicate = true) }
        val doc = Document(
            id = ids.newId(),
            title = (title ?: fileName.substringBeforeLast('.')).trim().ifEmpty { "Dokument" }.take(120),
            fileName = fileName.take(200),
            mimeType = mimeType,
            sizeBytes = stored.sizeBytes,
            sha256 = stored.sha256,
            importedAt = clock.now(),
        )
        repo.saveDocument(doc, clock.now())
        return ImportResult(doc.id, duplicate = false)
    }
}

/** Löscht ein Dokument; die Datei nur, wenn kein anderer Datensatz sie noch braucht. */
class DeleteDocument(private val repo: DocumentRepository, private val store: DocumentStore) {
    suspend operator fun invoke(id: NodeId): LifeResult<Unit> {
        val doc = repo.document(id) ?: return LifeResult.Invalid(listOf(LifeError.NotFound))
        repo.deleteDocument(id)
        if (repo.countBySha(doc.sha256) == 0) store.delete(doc.sha256)
        return LifeResult.Success(Unit)
    }
}
