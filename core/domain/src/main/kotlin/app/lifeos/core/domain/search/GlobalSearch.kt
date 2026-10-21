package app.lifeos.core.domain.search

import app.lifeos.core.model.Category
import app.lifeos.core.model.Document
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeSummary
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.Task

data class SearchHit(val id: NodeId, val type: NodeType, val title: String, val subtitle: String?, val rank: Int)

/**
 * Zentrale Suche über alle Bereiche. Deterministisch, ohne Index-Tabelle: Titel aller Einträge plus
 * Felder, die nicht im Titel stehen (Händler, Notiz, Betrag einer Buchung; Dateiname/Notiz eines
 * Dokuments; Beschreibung einer Aufgabe). Rang: Titel beginnt mit Suchwort < enthält < Nebenfeld.
 */
object GlobalSearch {
    /** Typen, die eine eigene Detailansicht haben. */
    val SEARCHABLE: Set<NodeType> = setOf(
        NodeType.TRANSACTION, NodeType.TASK, NodeType.EVENT, NodeType.PROJECT, NodeType.MILESTONE,
        NodeType.VEHICLE, NodeType.VEHICLE_EVENT, NodeType.ASSET, NodeType.DOCUMENT,
        NodeType.COURSE, NodeType.TOPIC, NodeType.EXAM,
    )

    fun search(
        query: String,
        nodes: List<NodeSummary>,
        transactions: List<LedgerTransaction>,
        tasks: List<Task>,
        documents: List<Document>,
        categories: List<Category>,
        limit: Int = 60,
    ): List<SearchHit> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val txById = transactions.associateBy { it.id }
        val taskById = tasks.associateBy { it.id }
        val docById = documents.associateBy { it.id }
        val catNames = categories.associate { it.id to it.name }

        return nodes.asSequence().filter { it.type in SEARCHABLE }.mapNotNull { n ->
            val tx = txById[n.id]
            val title = tx?.let { it.payee ?: n.title } ?: n.title
            val secondary: List<String> = when (n.type) {
                NodeType.TRANSACTION -> tx?.let { t ->
                    listOfNotNull(n.title, t.note, MoneyText.format(t.amount), MoneyText.toEditText(t.amount.minor)) + t.splits.mapNotNull { catNames[it.categoryId] }
                }.orEmpty()
                NodeType.TASK -> listOfNotNull(taskById[n.id]?.description)
                NodeType.DOCUMENT -> docById[n.id]?.let { listOfNotNull(it.fileName, it.note, it.ocrText) }.orEmpty()
                else -> emptyList()
            }
            val t = title.lowercase()
            val rank = when {
                t.startsWith(q) -> 0
                t.contains(q) -> 1
                secondary.any { it.lowercase().contains(q) } -> 2
                else -> return@mapNotNull null
            }
            val subtitle = when (n.type) {
                NodeType.TRANSACTION -> tx?.let { LifeFormat.numericDate(it.bookingDate) + " · " + MoneyText.format(it.amount) }
                NodeType.TASK -> taskById[n.id]?.dueDate?.let { "fällig " + LifeFormat.numericDate(it) }
                NodeType.DOCUMENT -> docById[n.id]?.fileName
                else -> null
            }
            SearchHit(n.id, n.type, title, subtitle, rank)
        }.sortedWith(compareBy<SearchHit> { it.rank }.thenBy { it.title.lowercase() }).take(limit).toList()
    }
}
