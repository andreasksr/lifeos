package app.lifeos.core.domain.map

import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.Milestone
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeLink
import app.lifeos.core.model.NodeSummary
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.Task
import app.lifeos.core.model.Topic
import app.lifeos.core.model.VehicleEvent

/** Eine Verbindung des ausgewählten Objekts – mit verständlicher Bedeutung. */
data class MapNeighbor(val node: NodeSummary, val meaning: String, val viaLink: NodeId?)

data class LifeMapView(val center: NodeSummary, val neighbors: List<MapNeighbor>) {
    /** Gruppiert nach Typ für die Legende („3 Buchungen, 2 Aufgaben …"). */
    val counts: Map<NodeType, Int> get() = neighbors.groupingBy { it.node.type }.eachCount()
}

/**
 * LifeMap (Auftrag Teil 10): zeigt für EIN ausgewähltes Objekt nur dessen direkte Verbindungen – nie das
 * ganze Netz. Quellen sind die frei gesetzten Verknüpfungen (`link`) UND die festen Beziehungen des
 * Datenmodells (Aufgabe → Projekt, Meilenstein → Projekt, Eintrag → Fahrzeug, Thema → Fach).
 */
object LifeMapBuilder {

    fun build(
        centerId: NodeId,
        nodes: List<NodeSummary>,
        links: List<NodeLink>,
        tasks: List<Task>,
        milestones: List<Milestone>,
        vehicleEvents: List<VehicleEvent>,
        topics: List<Topic>,
    ): LifeMapView? {
        val byId = nodes.associateBy { it.id }
        val center = byId[centerId] ?: return null
        val out = LinkedHashMap<NodeId, MapNeighbor>()
        fun add(id: NodeId, meaning: String, link: NodeId? = null) {
            if (id == centerId || id in out) return
            byId[id]?.let { out[id] = MapNeighbor(it, meaning, link) }
        }

        // Freie Verknüpfungen (beide Richtungen)
        links.filter { it.from == centerId || it.to == centerId }.forEach { l ->
            val other = if (l.from == centerId) l.to else l.from
            add(other, meaning(l.relation, outgoing = l.from == centerId), l.id)
        }
        // Feste Beziehungen
        when (center.type) {
            NodeType.PROJECT -> {
                tasks.filter { it.projectId == centerId }.forEach { add(it.id, "Aufgabe im Projekt") }
                milestones.filter { it.projectId == centerId }.forEach { add(it.id, "Meilenstein") }
            }
            NodeType.TASK -> tasks.firstOrNull { it.id == centerId }?.projectId?.let { add(it, "gehört zu Projekt") }
            NodeType.MILESTONE -> milestones.firstOrNull { it.id == centerId }?.projectId?.let { add(it, "gehört zu Projekt") }
            NodeType.VEHICLE -> vehicleEvents.filter { it.vehicleId == centerId }.forEach { add(it.id, "Fahrzeughistorie") }
            NodeType.VEHICLE_EVENT -> vehicleEvents.firstOrNull { it.id == centerId }?.vehicleId?.let { add(it, "Fahrzeug") }
            NodeType.COURSE -> topics.filter { it.subjectId == centerId }.forEach { add(it.id, "Thema") }
            NodeType.TOPIC -> topics.firstOrNull { it.id == centerId }?.subjectId?.let { add(it, "Fach") }
            else -> Unit
        }
        val order = listOf(NodeType.PROJECT, NodeType.VEHICLE, NodeType.ASSET, NodeType.TRANSACTION, NodeType.TASK, NodeType.MILESTONE, NodeType.VEHICLE_EVENT, NodeType.DOCUMENT, NodeType.EVENT)
        return LifeMapView(center, out.values.sortedWith(compareBy<MapNeighbor> { order.indexOf(it.node.type).let { i -> if (i < 0) order.size else i } }.thenBy { it.node.title.lowercase() }))
    }

    fun meaning(r: LinkRelation, outgoing: Boolean): String = when (r) {
        LinkRelation.CONCERNS -> if (outgoing) "betrifft" else "betroffen von"
        LinkRelation.PART_OF -> if (outgoing) "Teil von" else "enthält"
        LinkRelation.DOCUMENTS -> if (outgoing) "belegt" else "Beleg"
        LinkRelation.RELATES_TO -> "verbunden"
    }
}
