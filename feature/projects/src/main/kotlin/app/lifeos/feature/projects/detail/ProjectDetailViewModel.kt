package app.lifeos.feature.projects.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.ports.LinkedNode
import app.lifeos.core.domain.projects.ProjectResult
import app.lifeos.core.domain.projects.ProjectSnapshot
import app.lifeos.core.domain.projects.ProjectSnapshotBuilder
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.Milestone
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.Task
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.projects.ProjectsDependencies
import app.lifeos.feature.projects.ProjectsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProjectDetailState(
    val snapshot: ProjectSnapshot?,
    val missing: Boolean,
    val today: LocalDate,
    val categoryNames: Map<NodeId, String>,
    /** Verbindungen außer Buchungen (Fahrzeuge, Dokumente, …) – Vorschau der LifeMap. */
    val related: List<LinkedNode>,
    val candidates: List<LedgerTransaction>,
)

data class MilestoneDraftState(val id: NodeId? = null, val title: String = "", val dueDate: LocalDate? = null, val done: Boolean = false, val error: String? = null)

data class ProjectMessage(val text: String, val ok: Boolean, val undoLabel: String? = null, val undo: (() -> Unit)? = null)

class ProjectDetailViewModel(private val deps: ProjectsDependencies, val projectId: NodeId) : ViewModel() {

    private val _events = Channel<ProjectMessage>(Channel.BUFFERED)
    val events: Flow<ProjectMessage> = _events.receiveAsFlow()

    private val query = MutableStateFlow("")
    val pickerQuery: StateFlow<String> = query
    val milestoneDraft = MutableStateFlow<MilestoneDraftState?>(null)
    val confirmDelete = MutableStateFlow(false)
    val deleted = MutableStateFlow(false)

    private data class Base(
        val project: app.lifeos.core.model.Project?,
        val tasks: List<Task>,
        val milestones: List<Milestone>,
        val neighbors: List<LinkedNode>,
        val transactions: List<LedgerTransaction>,
    )

    private val base = combine(
        deps.projects.observeProjects(),
        deps.planning.observeTasks(),
        deps.projects.observeMilestones(),
        deps.links.observeNeighbors(projectId),
        deps.ledger.observeAllTransactions(),
    ) { p, t, m, n, tx -> Base(p.firstOrNull { it.id == projectId }, t, m, n, tx) }

    val state: StateFlow<ProjectDetailState?> = combine(base, deps.ledger.observeCategories(), query) { b, cats, q ->
        val linkedIds = b.neighbors.filter { it.node.type == NodeType.TRANSACTION }.map { it.node.id }.toSet()
        val txById = b.transactions.associateBy { it.id }
        val linked = linkedIds.mapNotNull { txById[it] }
        val catNames = cats.associate { it.id to it.name }
        val needle = q.trim().lowercase()
        val candidates = b.transactions.asSequence()
            .filter { (it.kind == TransactionKind.EXPENSE || it.kind == TransactionKind.REFUND) && it.id !in linkedIds }
            .filter { tx ->
                needle.isEmpty() || listOfNotNull(tx.payee, tx.note, *tx.splits.mapNotNull { catNames[it.categoryId] }.toTypedArray())
                    .any { it.lowercase().contains(needle) } || app.lifeos.core.model.MoneyText.format(tx.amount).contains(needle)
            }
            .sortedByDescending { it.bookingDate }
            .take(80)
            .toList()
        ProjectDetailState(
            snapshot = b.project?.let { ProjectSnapshotBuilder.build(it, b.tasks, b.milestones, linked) },
            missing = b.project == null,
            today = deps.clock.today(),
            categoryNames = catNames,
            related = b.neighbors.filter { it.node.type != NodeType.TRANSACTION },
            candidates = candidates,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(q: String) {
        query.value = q.take(60)
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ProjectMessage("Speichern fehlgeschlagen. Es wurde nichts verändert.", false))
            }
        }
    }

    fun toggleTask(t: Task): Unit = run {
        deps.toggleTask(t)
        _events.send(ProjectMessage(if (t.status == app.lifeos.core.model.TaskStatus.DONE) "„${t.title}“ wieder offen" else "„${t.title}“ erledigt", true))
    }

    fun toggleMilestone(m: Milestone): Unit = run {
        when (val r = deps.saveMilestone(projectId, m.title, m.dueDate, m.id, done = m.doneAt == null)) {
            is ProjectResult.Success -> _events.send(ProjectMessage(if (m.doneAt == null) "Meilenstein „${m.title}“ erreicht" else "Meilenstein wieder offen", true))
            is ProjectResult.Invalid -> _events.send(ProjectMessage(ProjectsText.error(r.errors.first()), false))
        }
    }

    fun editMilestone(m: Milestone?) {
        milestoneDraft.value = m?.let { MilestoneDraftState(it.id, it.title, it.dueDate, it.doneAt != null) } ?: MilestoneDraftState()
    }

    fun updateMilestone(change: (MilestoneDraftState) -> MilestoneDraftState) {
        milestoneDraft.value = milestoneDraft.value?.let { change(it).copy(error = null) }
    }

    fun closeMilestone() {
        milestoneDraft.value = null
    }

    fun saveMilestone() {
        val d = milestoneDraft.value ?: return
        run {
            when (val r = deps.saveMilestone(projectId, d.title, d.dueDate, d.id, d.done)) {
                is ProjectResult.Success -> {
                    milestoneDraft.value = null
                    _events.send(ProjectMessage("Meilenstein gespeichert", true))
                }
                is ProjectResult.Invalid -> milestoneDraft.value = d.copy(error = ProjectsText.error(r.errors.first()))
            }
        }
    }

    fun deleteMilestone() {
        val id = milestoneDraft.value?.id ?: return
        run {
            deps.projects.deleteMilestone(id)
            milestoneDraft.value = null
            _events.send(ProjectMessage("Meilenstein gelöscht", true))
        }
    }

    /** Ordnet eine BESTEHENDE Buchung zu – es entsteht nur eine Verknüpfung, keine zweite Buchung. */
    fun assign(tx: LedgerTransaction): Unit = run {
        when (val r = deps.assignTransaction(tx.id, projectId)) {
            is ProjectResult.Success -> _events.send(
                ProjectMessage("${tx.payee ?: "Buchung"} zugeordnet", true, "Rückgängig") { unlink(tx, announce = false) },
            )
            is ProjectResult.Invalid -> _events.send(ProjectMessage(ProjectsText.error(r.errors.first()), false))
        }
    }

    fun unlink(tx: LedgerTransaction, announce: Boolean = true): Unit = run {
        deps.unlink(tx.id, projectId)
        if (announce) {
            _events.send(ProjectMessage("Zuordnung entfernt – die Buchung bleibt in der Haushaltskasse", true, "Rückgängig") { assign(tx) })
        }
    }

    fun requestDelete() {
        confirmDelete.value = true
    }

    fun cancelDelete() {
        confirmDelete.value = false
    }

    fun confirmDeleteProject(): Unit = run {
        when (deps.deleteProject(projectId)) {
            is ProjectResult.Success -> {
                confirmDelete.value = false
                deleted.value = true
            }
            is ProjectResult.Invalid -> _events.send(ProjectMessage("Projekt nicht gefunden.", false))
        }
    }

    fun categoryLabel(tx: LedgerTransaction, names: Map<NodeId, String>): String =
        tx.splits.mapNotNull { names[it.categoryId] }.joinToString(", ").ifEmpty { LifeFormat.kindLabel(tx.kind) }
}
