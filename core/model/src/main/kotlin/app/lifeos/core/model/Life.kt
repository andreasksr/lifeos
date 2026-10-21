package app.lifeos.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// ───────────────────────────── Fahrzeuge ─────────────────────────────

data class Vehicle(
    val id: NodeId,
    /** Anzeigename, z. B. „Suzuki Samurai" oder ein Spitzname. */
    val name: String,
    val make: String,
    val model: String,
    val buildYear: Int? = null,
    val plate: String? = null,
    val vin: String? = null,
    /** Zuletzt bekannter Kilometerstand (wird von Ereignissen mit höherem Stand fortgeschrieben). */
    val odometerKm: Int? = null,
    val purchaseDate: LocalDate? = null,
    val purchasePriceMinor: Long? = null,
    val note: String? = null,
    val isSample: Boolean = false,
)

/** Art eines Fahrzeugereignisses. Gespeichert als TEXT – nur anhängen. */
enum class VehicleEventKind { PURCHASE, MAINTENANCE, REPAIR, PARTS, INSPECTION, TIRES, OTHER }

/**
 * Ereignis in der Fahrzeughistorie. [done] = false bedeutet geplant/fällig (z. B. Hauptuntersuchung);
 * Kosten werden nicht hier gespeichert, sondern über verknüpfte Buchungen der Haushaltskasse.
 */
data class VehicleEvent(
    val id: NodeId,
    val vehicleId: NodeId,
    val kind: VehicleEventKind,
    val title: String,
    val date: LocalDate,
    val odometerKm: Int? = null,
    val done: Boolean = true,
    val note: String? = null,
)

// ───────────────────────────── Inventar ─────────────────────────────

enum class AssetKind { TOOL, ELECTRONICS, MACHINE, HOUSEHOLD, OTHER }

data class Asset(
    val id: NodeId,
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
    val isSample: Boolean = false,
)

// ───────────────────────────── Dokumente ─────────────────────────────

/**
 * Metadaten eines Dokuments. Die Datei liegt verschlüsselt im App-Speicher, adressiert über [sha256] –
 * dieselbe Datei wird daher nie doppelt gespeichert. [ocrText] bleibt leer, solange keine Texterkennung existiert.
 */
data class Document(
    val id: NodeId,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val importedAt: Instant,
    val docDate: LocalDate? = null,
    val note: String? = null,
    val ocrText: String? = null,
) {
    val isPdf: Boolean get() = mimeType == "application/pdf"
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

// ───────────────────────────── Weiterbildung ─────────────────────────────

/** Prüfungsteil der Fortbildung Industriemeister Mechatronik. */
enum class ExamPart { BQ, HQ, OTHER }

data class Subject(
    val id: NodeId,
    val name: String,
    val part: ExamPart,
    val sortOrder: Int = 0,
    val note: String? = null,
)

enum class TopicStatus { OPEN, IN_PROGRESS, DONE }

data class Topic(
    val id: NodeId,
    val subjectId: NodeId,
    val title: String,
    val status: TopicStatus = TopicStatus.OPEN,
    val sortOrder: Int = 0,
)

enum class SessionSource { TIMER, MANUAL }

/** Lernzeit. [end] = null bedeutet: läuft gerade (höchstens eine Sitzung gleichzeitig). */
data class StudySession(
    val id: NodeId,
    val subjectId: NodeId?,
    val start: Instant,
    val end: Instant?,
    val source: SessionSource,
    val note: String? = null,
) {
    val running: Boolean get() = end == null
}

/**
 * Prüfung. Solange kein [confirmedDate] bekannt ist, gibt es nur einen Zielzeitraum
 * ([targetMonth], [targetLabel]) – er wird nie als bestätigter Termin dargestellt.
 */
data class Exam(
    val id: NodeId,
    val title: String,
    val part: ExamPart,
    val targetMonth: YearMonth? = null,
    val targetLabel: String? = null,
    val confirmedDate: LocalDate? = null,
    val location: String? = null,
    val note: String? = null,
) {
    val isConfirmed: Boolean get() = confirmedDate != null
}
