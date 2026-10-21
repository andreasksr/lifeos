package app.lifeos.core.domain.projects

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.ledger.LedgerMath
import app.lifeos.core.domain.ports.LedgerRepository
import app.lifeos.core.domain.ports.LinkRepository
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.Milestone
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeLink
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.ProgressMode
import app.lifeos.core.model.Project
import app.lifeos.core.model.ProjectStatus
import app.lifeos.core.model.Task
import app.lifeos.core.model.TaskStatus
import app.lifeos.core.model.TransactionKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    fun observeMilestones(): Flow<List<Milestone>>
    suspend fun project(id: NodeId): Project?
    suspend fun projects(): List<Project>
    suspend fun milestones(): List<Milestone>
    suspend fun saveProject(project: Project, now: Instant)
    suspend fun deleteProject(id: NodeId)
    suspend fun saveMilestone(milestone: Milestone, now: Instant)
    suspend fun deleteMilestone(id: NodeId)
}

sealed interface ProjectError {
    data object NameRequired : ProjectError
    data object NameTooLong : ProjectError
    data object TargetBeforeStart : ProjectError
    data object ManualProgressMissing : ProjectError
    data object NotFound : ProjectError
    data object NotATransaction : ProjectError
    data object AlreadyLinked : ProjectError
    data object InvalidBudget : ProjectError
}

sealed interface ProjectResult<out T> {
    data class Success<T>(val value: T) : ProjectResult<T>
    data class Invalid(val errors: List<ProjectError>) : ProjectResult<Nothing>
}

/** Ergebnis der Fortschrittsberechnung inkl. nachvollziehbarer Begründung. */
data class ProjectProgress(
    /** 0..1000 Promille; `null`, wenn es keine Grundlage gibt (z. B. 0 Aufgaben) – nie eine erfundene Zahl. */
    val permille: Int?,
    val basis: String,
)

object ProgressCalculator {
    fun compute(project: Project, tasks: List<Task>, milestones: List<Milestone>): ProjectProgress = when (project.progressMode) {
        ProgressMode.TASKS -> {
            val own = tasks.filter { it.projectId == project.id }
            if (own.isEmpty()) ProjectProgress(null, "Noch keine Aufgaben – Fortschritt wird aus erledigten Aufgaben berechnet.")
            else {
                val done = own.count { it.status == TaskStatus.DONE }
                ProjectProgress((done * 1000 / own.size), "$done von ${own.size} Aufgaben erledigt")
            }
        }
        ProgressMode.MILESTONES -> {
            val own = milestones.filter { it.projectId == project.id }
            if (own.isEmpty()) ProjectProgress(null, "Noch keine Meilensteine – Fortschritt wird aus erreichten Meilensteinen berechnet.")
            else {
                val done = own.count { it.doneAt != null }
                ProjectProgress((done * 1000 / own.size), "$done von ${own.size} Meilensteinen erreicht")
            }
        }
        ProgressMode.MANUAL -> ProjectProgress(project.manualProgressPermille, "Manuell festgelegt")
    }
}

data class ProjectCosts(
    /** Netto-Kosten: Ausgaben − Erstattungen der verknüpften Buchungen. */
    val netMinor: Long,
    val transactions: List<LedgerTransaction>,
    val budgetMinor: Long?,
) {
    val remainingMinor: Long? get() = budgetMinor?.let { it - netMinor }
    val usedPermille: Long? get() = budgetMinor?.let { LedgerMath.permille(netMinor, it) }
}

object ProjectCostCalculator {
    /** Summiert verknüpfte Buchungen – dieselben Buchungszeilen wie in der Haushaltskasse, keine Kopien. */
    fun compute(transactions: List<LedgerTransaction>, budgetMinor: Long?): ProjectCosts {
        val net = transactions.fold(0L) { acc, tx ->
            when (tx.kind) {
                TransactionKind.EXPENSE -> Math.addExact(acc, tx.amount.minor)
                TransactionKind.REFUND -> Math.subtractExact(acc, tx.amount.minor)
                else -> acc
            }
        }
        return ProjectCosts(net, transactions.sortedByDescending { it.bookingDate }, budgetMinor)
    }
}

data class ProjectDraft(
    val id: NodeId? = null,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val startDate: LocalDate,
    val targetDate: LocalDate? = null,
    val progressMode: ProgressMode = ProgressMode.TASKS,
    val manualProgressPermille: Int? = null,
    val budgetMinor: Long? = null,
    val notes: String? = null,
)

class SaveProject(private val repo: ProjectRepository, private val ids: IdGenerator, private val clock: LifeClock) {
    suspend operator fun invoke(d: ProjectDraft): ProjectResult<NodeId> {
        val errors = mutableListOf<ProjectError>()
        val name = d.name.trim()
        if (name.isEmpty()) errors += ProjectError.NameRequired
        if (name.length > 120) errors += ProjectError.NameTooLong
        if (d.targetDate != null && d.targetDate.isBefore(d.startDate)) errors += ProjectError.TargetBeforeStart
        if (d.progressMode == ProgressMode.MANUAL && (d.manualProgressPermille == null || d.manualProgressPermille !in 0..1000)) {
            errors += ProjectError.ManualProgressMissing
        }
        if (d.budgetMinor != null && d.budgetMinor <= 0) errors += ProjectError.InvalidBudget
        val existing = d.id?.let { repo.project(it) }
        if (d.id != null && existing == null) errors += ProjectError.NotFound
        if (errors.isNotEmpty()) return ProjectResult.Invalid(errors)
        val p = Project(
            id = d.id ?: ids.newId(),
            name = name,
            description = d.description?.trim()?.takeIf { it.isNotEmpty() },
            category = d.category?.trim()?.takeIf { it.isNotEmpty() },
            status = d.status,
            startDate = d.startDate,
            targetDate = d.targetDate,
            progressMode = d.progressMode,
            manualProgressPermille = if (d.progressMode == ProgressMode.MANUAL) d.manualProgressPermille else null,
            budgetMinor = d.budgetMinor,
            notes = d.notes?.trim()?.takeIf { it.isNotEmpty() },
            isSample = existing?.isSample ?: false,
        )
        repo.saveProject(p, clock.now())
        return ProjectResult.Success(p.id)
    }
}

class DeleteProject(private val repo: ProjectRepository) {
    /** Löscht Projekt und Meilensteine. Aufgaben verlieren nur die Zuordnung, Buchungen bleiben unberührt. */
    suspend operator fun invoke(id: NodeId): ProjectResult<Unit> {
        repo.project(id) ?: return ProjectResult.Invalid(listOf(ProjectError.NotFound))
        repo.deleteProject(id)
        return ProjectResult.Success(Unit)
    }
}

class SaveMilestone(private val repo: ProjectRepository, private val ids: IdGenerator, private val clock: LifeClock) {
    suspend operator fun invoke(projectId: NodeId, title: String, dueDate: LocalDate?, id: NodeId? = null, done: Boolean = false): ProjectResult<NodeId> {
        val t = title.trim()
        if (t.isEmpty()) return ProjectResult.Invalid(listOf(ProjectError.NameRequired))
        repo.project(projectId) ?: return ProjectResult.Invalid(listOf(ProjectError.NotFound))
        val existing = id?.let { mid -> repo.milestones().firstOrNull { it.id == mid } }
        val m = Milestone(
            id = id ?: ids.newId(),
            projectId = projectId,
            title = t,
            dueDate = dueDate,
            doneAt = if (done) (existing?.doneAt ?: clock.now()) else null,
            sortOrder = existing?.sortOrder ?: ((repo.milestones().filter { it.projectId == projectId }.maxOfOrNull { it.sortOrder } ?: -1) + 1),
        )
        repo.saveMilestone(m, clock.now())
        return ProjectResult.Success(m.id)
    }
}

/**
 * Ordnet eine BESTEHENDE Buchung einem Projekt zu (Auftrag Teil 6). Es entsteht nur ein Link;
 * die Buchung wird nicht kopiert. Änderungen an der Buchung wirken daher sofort überall.
 */
class AssignTransactionToProject(
    private val links: LinkRepository,
    private val ledger: LedgerRepository,
    private val projects: ProjectRepository,
    private val ids: IdGenerator,
    private val clock: LifeClock,
) {
    suspend operator fun invoke(transactionId: NodeId, projectId: NodeId): ProjectResult<NodeId> {
        ledger.transaction(transactionId) ?: return ProjectResult.Invalid(listOf(ProjectError.NotATransaction))
        projects.project(projectId) ?: return ProjectResult.Invalid(listOf(ProjectError.NotFound))
        if (links.linkExists(transactionId, projectId, LinkRelation.PART_OF)) return ProjectResult.Invalid(listOf(ProjectError.AlreadyLinked))
        val link = NodeLink(ids.newId(), transactionId, projectId, LinkRelation.PART_OF)
        links.link(link, clock.now())
        return ProjectResult.Success(link.id)
    }
}

class UnlinkNodes(private val links: LinkRepository) {
    suspend operator fun invoke(from: NodeId, to: NodeId) {
        links.linksOf(from).filter { (it.from == from && it.to == to) || (it.from == to && it.to == from) }.forEach { links.unlink(it.id) }
    }
}

/** Alles für die Projektansicht – inkl. Projekt-LifeLine (Meilensteine, Aufgaben, Kosten chronologisch). */
data class ProjectSnapshot(
    val project: Project,
    val tasks: List<Task>,
    val milestones: List<Milestone>,
    val progress: ProjectProgress,
    val costs: ProjectCosts,
    val line: List<ProjectLineItem>,
    val nextSteps: List<Task>,
)

sealed interface ProjectLineItem {
    val date: LocalDate?
    data class MilestoneItem(val milestone: Milestone) : ProjectLineItem { override val date get() = milestone.dueDate }
    data class TaskItem(val task: Task) : ProjectLineItem { override val date get() = task.dueDate }
    data class CostItem(val tx: LedgerTransaction) : ProjectLineItem { override val date: LocalDate get() = tx.bookingDate }
    data class StartItem(override val date: LocalDate) : ProjectLineItem
    data class TargetItem(override val date: LocalDate) : ProjectLineItem
}

object ProjectSnapshotBuilder {
    fun build(project: Project, tasks: List<Task>, milestones: List<Milestone>, linkedTransactions: List<LedgerTransaction>): ProjectSnapshot {
        val own = tasks.filter { it.projectId == project.id }
        val ms = milestones.filter { it.projectId == project.id }.sortedWith(compareBy<Milestone> { it.dueDate ?: LocalDate.MAX }.thenBy { it.sortOrder })
        val line = buildList<ProjectLineItem> {
            add(ProjectLineItem.StartItem(project.startDate))
            ms.forEach { add(ProjectLineItem.MilestoneItem(it)) }
            own.filter { it.dueDate != null }.forEach { add(ProjectLineItem.TaskItem(it)) }
            linkedTransactions.forEach { add(ProjectLineItem.CostItem(it)) }
            project.targetDate?.let { add(ProjectLineItem.TargetItem(it)) }
        }.sortedWith(compareBy<ProjectLineItem> { it.date ?: LocalDate.MAX }.thenBy {
            when (it) {
                is ProjectLineItem.StartItem -> 0
                is ProjectLineItem.MilestoneItem -> 1
                is ProjectLineItem.TaskItem -> 2
                is ProjectLineItem.CostItem -> 3
                is ProjectLineItem.TargetItem -> 4
            }
        })
        val next = own.filter { it.status == TaskStatus.OPEN }
            .sortedWith(compareBy<Task> { it.dueDate ?: LocalDate.MAX }.thenByDescending { it.priority })
            .take(3)
        return ProjectSnapshot(
            project = project,
            tasks = own,
            milestones = ms,
            progress = ProgressCalculator.compute(project, tasks, milestones),
            costs = ProjectCostCalculator.compute(linkedTransactions, project.budgetMinor),
            line = line,
            nextSteps = next,
        )
    }

    /** IDs der mit dem Projekt verknüpften Buchungen aus den Nachbarn eines Projekt-Nodes. */
    fun linkedTransactionIds(neighbors: List<app.lifeos.core.domain.ports.LinkedNode>): List<NodeId> =
        neighbors.filter { it.node.type == NodeType.TRANSACTION }.map { it.node.id }
}

/** Kompakte Projektübersicht für Listen und Home – alles aus echten, verknüpften Daten berechnet. */
data class ProjectCard(
    val project: Project,
    val progress: ProjectProgress,
    val costs: ProjectCosts,
    val openTasks: Int,
    val doneTasks: Int,
    val nextTask: Task?,
    val nextMilestone: Milestone?,
)

object ProjectOverview {

    /** Buchungen, die per `PART_OF` mit dem Projekt verknüpft sind (Richtung egal, ohne Duplikate). */
    fun linkedIds(projectId: NodeId, links: List<NodeLink>): Set<NodeId> =
        links.filter { it.relation == LinkRelation.PART_OF && (it.to == projectId || it.from == projectId) }
            .map { if (it.to == projectId) it.from else it.to }
            .toSet()

    private val statusOrder = listOf(ProjectStatus.ACTIVE, ProjectStatus.PLANNED, ProjectStatus.PAUSED, ProjectStatus.DONE, ProjectStatus.CANCELLED)

    fun build(
        projects: List<Project>,
        tasks: List<Task>,
        milestones: List<Milestone>,
        links: List<NodeLink>,
        transactions: List<LedgerTransaction>,
    ): List<ProjectCard> {
        val txById = transactions.associateBy { it.id }
        return projects.map { p ->
            val own = tasks.filter { it.projectId == p.id }
            val txs = linkedIds(p.id, links).mapNotNull { txById[it] }
            ProjectCard(
                project = p,
                progress = ProgressCalculator.compute(p, tasks, milestones),
                costs = ProjectCostCalculator.compute(txs, p.budgetMinor),
                openTasks = own.count { it.status == TaskStatus.OPEN },
                doneTasks = own.count { it.status == TaskStatus.DONE },
                nextTask = own.filter { it.status == TaskStatus.OPEN }
                    .sortedWith(compareBy<Task> { it.dueDate ?: LocalDate.MAX }.thenByDescending { it.priority })
                    .firstOrNull(),
                nextMilestone = milestones.filter { it.projectId == p.id && it.doneAt == null }
                    .sortedWith(compareBy<Milestone> { it.dueDate ?: LocalDate.MAX }.thenBy { it.sortOrder })
                    .firstOrNull(),
            )
        }.sortedWith(compareBy<ProjectCard> { statusOrder.indexOf(it.project.status) }.thenBy { it.project.name.lowercase() })
    }
}
