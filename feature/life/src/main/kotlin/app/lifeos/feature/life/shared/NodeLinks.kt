package app.lifeos.feature.life.shared

import app.lifeos.core.domain.life.DocumentTooLargeException
import app.lifeos.core.domain.links.LinkResult
import app.lifeos.core.domain.ports.LinkedNode
import app.lifeos.core.model.Category
import app.lifeos.core.model.Document
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.life.LifeDependencies
import app.lifeos.feature.life.LifeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

data class LifeMessage(val text: String, val ok: Boolean, val undoLabel: String? = null, val undo: (() -> Unit)? = null)

/** Verknüpfungen eines Objekts (Fahrzeug, Inventar, Dokument …) mit Buchungen und Dokumenten. */
data class LinksState(
    val transactions: List<LedgerTransaction>,
    val documents: List<Document>,
    /** Weitere Verbindungen (Projekte, Aufgaben …) – für „Verbunden mit". */
    val other: List<LinkedNode>,
    val txCandidates: List<LedgerTransaction>,
    val docCandidates: List<Document>,
    val categoryNames: Map<NodeId, String>,
) {
    fun categoryLabel(tx: LedgerTransaction): String =
        tx.splits.mapNotNull { categoryNames[it.categoryId] }.joinToString(", ").ifEmpty { app.lifeos.core.model.LifeFormat.kindLabel(tx.kind) }
}

/**
 * Gemeinsame Logik für „Buchung zuordnen" und „Dokument verknüpfen". Es entstehen ausschließlich
 * Verknüpfungen (`link`) – nie Kopien von Buchungen oder Dateien.
 */
class NodeLinksController(
    private val deps: LifeDependencies,
    private val ownerId: NodeId,
    private val scope: CoroutineScope,
    private val send: suspend (LifeMessage) -> Unit,
) {
    private val query = MutableStateFlow("")
    val txQuery: StateFlow<String> = query

    fun setQuery(q: String) {
        query.value = q.take(60)
    }

    private data class Base(val neighbors: List<LinkedNode>, val txs: List<LedgerTransaction>, val docs: List<Document>, val cats: List<Category>)

    val state: Flow<LinksState> = combine(
        combine(deps.links.observeNeighbors(ownerId), deps.ledger.observeAllTransactions(), deps.documents.observeDocuments(), deps.ledger.observeCategories(), ::Base),
        query,
    ) { b, q ->
        val txIds = b.neighbors.filter { it.node.type == NodeType.TRANSACTION }.map { it.node.id }.toSet()
        val docIds = b.neighbors.filter { it.node.type == NodeType.DOCUMENT }.map { it.node.id }.toSet()
        val names = b.cats.associate { it.id to it.name }
        val needle = q.trim().lowercase()
        LinksState(
            transactions = b.txs.filter { it.id in txIds }.sortedByDescending { it.bookingDate },
            documents = b.docs.filter { it.id in docIds },
            other = b.neighbors.filter { it.node.type != NodeType.TRANSACTION && it.node.type != NodeType.DOCUMENT },
            txCandidates = b.txs.asSequence()
                .filter { (it.kind == TransactionKind.EXPENSE || it.kind == TransactionKind.REFUND) && it.id !in txIds }
                .filter { tx ->
                    needle.isEmpty() || listOfNotNull(tx.payee, tx.note, *tx.splits.mapNotNull { names[it.categoryId] }.toTypedArray())
                        .any { it.lowercase().contains(needle) } || MoneyText.format(tx.amount).contains(needle)
                }
                .sortedByDescending { it.bookingDate }
                .take(80)
                .toList(),
            docCandidates = b.docs.filter { it.id !in docIds && it.id != ownerId },
            categoryNames = names,
        )
    }

    private fun run(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DocumentTooLargeException) {
                send(LifeMessage("Die Datei ist zu groß (max. ${e.limitBytes / (1024 * 1024)} MB).", false))
            } catch (e: Exception) {
                send(LifeMessage("Das hat nicht geklappt. Es wurde nichts verändert.", false))
            }
        }
    }

    private suspend fun link(other: NodeId, relation: LinkRelation, label: String, undo: () -> Unit) {
        when (deps.linkNodes(other, ownerId, relation)) {
            is LinkResult.Linked -> send(LifeMessage(label, true, "Rückgängig", undo))
            LinkResult.AlreadyLinked -> send(LifeMessage("Ist bereits verknüpft.", false))
            else -> send(LifeMessage(LifeText.error(app.lifeos.core.domain.life.LifeError.NotFound), false))
        }
    }

    fun assign(tx: LedgerTransaction): Unit = run {
        link(tx.id, LinkRelation.CONCERNS, "${tx.payee ?: "Buchung"} zugeordnet") { unassign(tx, announce = false) }
    }

    fun unassign(tx: LedgerTransaction, announce: Boolean = true): Unit = run {
        deps.unlink(tx.id, ownerId)
        if (announce) send(LifeMessage("Zuordnung entfernt – die Buchung bleibt in der Haushaltskasse", true, "Rückgängig") { assign(tx) })
    }

    fun linkDocument(doc: Document): Unit = run {
        link(doc.id, LinkRelation.DOCUMENTS, "„${doc.title}“ verknüpft") { unlinkDocument(doc, announce = false) }
    }

    fun unlinkDocument(doc: Document, announce: Boolean = true): Unit = run {
        deps.unlink(doc.id, ownerId)
        if (announce) send(LifeMessage("Verknüpfung entfernt – das Dokument bleibt im Archiv", true, "Rückgängig") { linkDocument(doc) })
    }

    /** Importiert eine Datei (ohne Duplikat) und verknüpft sie direkt mit diesem Objekt. */
    fun importAndLink(open: () -> InputStream, fileName: String, mimeType: String): Unit = run {
        val r = withContext(Dispatchers.IO) { open().use { deps.importDocument(it, fileName, mimeType) } }
        deps.linkNodes(r.documentId, ownerId, LinkRelation.DOCUMENTS)
        send(LifeMessage(if (r.duplicate) "Dokument war schon im Archiv – verknüpft, nicht doppelt gespeichert" else "Dokument gespeichert und verknüpft", true))
    }
}
