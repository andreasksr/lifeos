package app.lifeos.core.model

import java.util.UUID

/**
 * Stabile, geräteunabhängige Identität eines Objekts (UUID, kleingeschrieben).
 * Überlebt Export, Import und eine spätere Synchronisierung unverändert.
 */
@JvmInline
value class NodeId(val value: String) {
    init {
        require(UUID_PATTERN.matches(value)) { "Ungültige NodeId: '$value'" }
    }

    override fun toString(): String = value

    companion object {
        private val UUID_PATTERN =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

        fun random(): NodeId = NodeId(UUID.randomUUID().toString())
    }
}

/**
 * Typ eines Nodes. Gespeichert als TEXT (ADR-005) – neue Werte dürfen nur angehängt,
 * vorhandene nie umbenannt werden.
 */
enum class NodeType {
    ACCOUNT,
    CATEGORY,
    TRANSACTION,

    // Ab späteren Phasen – bereits reserviert, damit Links typstabil bleiben.
    PROJECT,
    TASK,
    EVENT,
    ASSET,
    VEHICLE,
    CONTRACT,
    DOCUMENT,
    NOTE,
    COURSE,
    TOPIC,

    // ab Schema v2
    RECURRING,

    // ab Schema v3/v4
    MILESTONE,
    VEHICLE_EVENT,
    EXAM,
}

/** Bedeutung einer gerichteten Verknüpfung `from → to`. */
enum class LinkRelation {
    /** Buchung/Dokument betrifft ein Objekt (z. B. Ersatzteil → Fahrzeug). */
    CONCERNS,

    /** Ist Bestandteil von (z. B. Buchung → Projekt, Aufgabe → Projekt). */
    PART_OF,

    /** Belegt/dokumentiert (z. B. Quittung → Buchung). */
    DOCUMENTS,

    /** Allgemeiner Bezug ohne weitere Semantik. */
    RELATES_TO,
}

data class NodeLink(
    val id: NodeId,
    val from: NodeId,
    val to: NodeId,
    val relation: LinkRelation,
)

/** Kurzbeschreibung eines beliebigen Nodes – Grundlage für Suche, Archiv und Link-Anzeige. */
data class NodeSummary(
    val id: NodeId,
    val type: NodeType,
    val title: String,
    val isSample: Boolean,
    val archived: Boolean,
)
