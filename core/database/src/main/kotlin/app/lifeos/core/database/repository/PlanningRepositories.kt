package app.lifeos.core.database.repository

import app.lifeos.core.database.LifeOsDatabase
import app.lifeos.core.database.entity.CalendarEventEntity
import app.lifeos.core.database.entity.EventRow
import app.lifeos.core.database.entity.MilestoneEntity
import app.lifeos.core.database.entity.MilestoneRow
import app.lifeos.core.database.entity.ProjectEntity
import app.lifeos.core.database.entity.ProjectRow
import app.lifeos.core.database.entity.ReminderEntity
import app.lifeos.core.database.entity.TaskCompletionEntity
import app.lifeos.core.database.entity.TaskEntity
import app.lifeos.core.database.entity.TaskRow
import app.lifeos.core.domain.planning.PlanningRepository
import app.lifeos.core.domain.planning.TaskCompletion
import app.lifeos.core.domain.projects.ProjectRepository
import app.lifeos.core.model.CalendarEvent
import app.lifeos.core.model.Milestone
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.Priority
import app.lifeos.core.model.ProgressMode
import app.lifeos.core.model.Project
import app.lifeos.core.model.ProjectStatus
import app.lifeos.core.model.Reminder
import app.lifeos.core.model.Repeat
import app.lifeos.core.model.RepeatUnit
import app.lifeos.core.model.Task
import app.lifeos.core.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun repeatOf(unit: String?, interval: Int): Repeat? = unit?.let { Repeat(RepeatUnit.valueOf(it), interval) }
private fun Long.instant(): Instant = Instant.ofEpochMilli(this)

internal fun TaskRow.toDomain() = Task(
    id = NodeId(task.nodeId),
    title = title,
    description = task.description,
    status = TaskStatus.valueOf(task.status),
    priority = Priority.valueOf(task.priority),
    dueDate = task.dueDate?.let(LocalDate::parse),
    dueTime = task.dueTime?.let { LocalTime.parse(it, HHMM) },
    repeat = repeatOf(task.repeatUnit, task.repeatInterval),
    repeatAnchor = task.repeatAnchor?.let(LocalDate::parse),
    projectId = task.projectId?.let(::NodeId),
    completedAt = task.completedAt?.instant(),
    isSample = isSample,
)

internal fun Task.toEntity() = TaskEntity(
    nodeId = id.value,
    description = description,
    status = status.name,
    priority = priority.name,
    dueDate = dueDate?.toString(),
    dueTime = dueTime?.format(HHMM),
    repeatUnit = repeat?.unit?.name,
    repeatInterval = repeat?.interval ?: 1,
    repeatAnchor = repeatAnchor?.toString(),
    projectId = projectId?.value,
    completedAt = completedAt?.toEpochMilli(),
)

internal fun EventRow.toDomain() = CalendarEvent(
    id = NodeId(event.nodeId),
    title = title,
    start = LocalDateTime.parse(event.startAt, MINUTE),
    end = LocalDateTime.parse(event.endAt, MINUTE),
    allDay = event.allDay,
    location = event.location,
    note = event.note,
    repeat = repeatOf(event.repeatUnit, event.repeatInterval),
    isSample = isSample,
)

internal fun CalendarEvent.toEntity() = CalendarEventEntity(
    nodeId = id.value,
    startAt = start.format(MINUTE),
    endAt = end.format(MINUTE),
    allDay = allDay,
    location = location,
    note = note,
    repeatUnit = repeat?.unit?.name,
    repeatInterval = repeat?.interval ?: 1,
)

internal fun ReminderEntity.toDomain() = Reminder(
    id = NodeId(id),
    nodeId = NodeId(nodeId),
    minutesBefore = minutesBefore,
    enabled = enabled,
    lastFiredFor = lastFiredFor?.instant(),
    snoozedUntil = snoozedUntil?.instant(),
)

internal fun Reminder.toEntity() = ReminderEntity(
    id = id.value,
    nodeId = nodeId.value,
    minutesBefore = minutesBefore,
    enabled = enabled,
    lastFiredFor = lastFiredFor?.toEpochMilli(),
    snoozedUntil = snoozedUntil?.toEpochMilli(),
)

internal fun ProjectRow.toDomain() = Project(
    id = NodeId(project.nodeId),
    name = title,
    description = project.description,
    category = project.category,
    status = ProjectStatus.valueOf(project.status),
    startDate = LocalDate.parse(project.startDate),
    targetDate = project.targetDate?.let(LocalDate::parse),
    progressMode = ProgressMode.valueOf(project.progressMode),
    manualProgressPermille = project.manualProgress,
    budgetMinor = project.budgetMinor,
    notes = project.notes,
    isSample = isSample,
)

internal fun Project.toEntity() = ProjectEntity(
    nodeId = id.value,
    description = description,
    category = category,
    status = status.name,
    startDate = startDate.toString(),
    targetDate = targetDate?.toString(),
    progressMode = progressMode.name,
    manualProgress = manualProgressPermille,
    budgetMinor = budgetMinor,
    notes = notes,
)

internal fun MilestoneRow.toDomain() = Milestone(
    id = NodeId(milestone.nodeId),
    projectId = NodeId(milestone.projectId),
    title = title,
    dueDate = milestone.dueDate?.let(LocalDate::parse),
    doneAt = milestone.doneAt?.instant(),
    sortOrder = milestone.sortOrder,
)

internal fun Milestone.toEntity() = MilestoneEntity(
    nodeId = id.value,
    projectId = projectId.value,
    dueDate = dueDate?.toString(),
    doneAt = doneAt?.toEpochMilli(),
    sortOrder = sortOrder,
)

class RoomPlanningRepository(private val db: LifeOsDatabase) : PlanningRepository {
    private val dao get() = db.planningDao()

    override fun observeTasks(): Flow<List<Task>> = dao.observeTasks().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override fun observeEvents(): Flow<List<CalendarEvent>> = dao.observeEvents().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override fun observeReminders(): Flow<List<Reminder>> = dao.observeReminders().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override fun observeCompletions(): Flow<List<TaskCompletion>> = dao.observeCompletions().map { r ->
        r.map { TaskCompletion(NodeId(it.taskId), it.completedAt.instant(), it.dueDate?.let(LocalDate::parse)) }
    }.distinctUntilChanged()

    override suspend fun task(id: NodeId): Task? = dao.task(id.value)?.toDomain()
    override suspend fun event(id: NodeId): CalendarEvent? = dao.event(id.value)?.toDomain()
    override suspend fun tasks(): List<Task> = dao.tasks().map { it.toDomain() }
    override suspend fun events(): List<CalendarEvent> = dao.events().map { it.toDomain() }
    override suspend fun reminders(): List<Reminder> = dao.reminders().map { it.toDomain() }

    override suspend fun saveTask(task: Task, now: Instant) =
        dao.saveTask(nodeEntity(task.id, NodeType.TASK, task.title, now, archived = false, isSample = task.isSample), task.toEntity())

    override suspend fun deleteTask(id: NodeId) {
        db.nodeDao().delete(id.value) // Kaskade: task → task_completion, reminder, link
    }

    override suspend fun saveEvent(event: CalendarEvent, now: Instant) =
        dao.saveEvent(nodeEntity(event.id, NodeType.EVENT, event.title, now, archived = false, isSample = event.isSample), event.toEntity())

    override suspend fun deleteEvent(id: NodeId) {
        db.nodeDao().delete(id.value)
    }

    override suspend fun saveReminder(reminder: Reminder) = dao.saveReminder(reminder.toEntity())

    override suspend fun deleteReminder(id: NodeId) {
        dao.deleteReminder(id.value)
    }

    override suspend fun recordCompletion(completion: TaskCompletion) =
        dao.insertCompletion(
            TaskCompletionEntity(UUID.randomUUID().toString(), completion.taskId.value, completion.completedAt.toEpochMilli(), completion.dueDate?.toString()),
        )
}

class RoomProjectRepository(private val db: LifeOsDatabase) : ProjectRepository {
    private val dao get() = db.projectDao()

    override fun observeProjects(): Flow<List<Project>> = dao.observeProjects().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override fun observeMilestones(): Flow<List<Milestone>> = dao.observeMilestones().map { r -> r.map { it.toDomain() } }.distinctUntilChanged()
    override suspend fun project(id: NodeId): Project? = dao.project(id.value)?.toDomain()
    override suspend fun projects(): List<Project> = dao.projects().map { it.toDomain() }
    override suspend fun milestones(): List<Milestone> = dao.milestones().map { it.toDomain() }

    override suspend fun saveProject(project: Project, now: Instant) =
        dao.saveProject(nodeEntity(project.id, NodeType.PROJECT, project.name, now, archived = false, isSample = project.isSample), project.toEntity())

    /** Kaskade: project → milestone (+ Knoten per Trigger), link; Aufgaben behalten sich selbst (project_id → NULL). */
    override suspend fun deleteProject(id: NodeId) {
        db.nodeDao().delete(id.value)
    }

    override suspend fun saveMilestone(milestone: Milestone, now: Instant) =
        dao.saveMilestone(nodeEntity(milestone.id, NodeType.MILESTONE, milestone.title, now, archived = false), milestone.toEntity())

    override suspend fun deleteMilestone(id: NodeId) {
        db.nodeDao().delete(id.value)
    }
}
