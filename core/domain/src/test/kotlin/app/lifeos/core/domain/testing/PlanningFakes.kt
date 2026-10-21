package app.lifeos.core.domain.testing

import app.lifeos.core.domain.planning.PlanningRepository
import app.lifeos.core.domain.planning.ReminderScheduler
import app.lifeos.core.domain.planning.TaskCompletion
import app.lifeos.core.domain.projects.ProjectRepository
import app.lifeos.core.model.CalendarEvent
import app.lifeos.core.model.Milestone
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeSummary
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.Project
import app.lifeos.core.model.Reminder
import app.lifeos.core.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

class CountingScheduler : ReminderScheduler {
    var calls = 0
    override suspend fun reschedule() { calls++ }
}

class FakePlanningRepository(private val nodes: MutableMap<NodeId, NodeSummary> = linkedMapOf()) : PlanningRepository {
    val tasks = MutableStateFlow<List<Task>>(emptyList())
    val events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val completions = MutableStateFlow<List<TaskCompletion>>(emptyList())

    override fun observeTasks() = tasks
    override fun observeEvents() = events
    override fun observeReminders() = reminders
    override suspend fun task(id: NodeId) = tasks.value.firstOrNull { it.id == id }
    override suspend fun event(id: NodeId) = events.value.firstOrNull { it.id == id }
    override suspend fun tasks() = tasks.value
    override suspend fun events() = events.value
    override suspend fun reminders() = reminders.value
    override suspend fun saveTask(task: Task, now: Instant) {
        tasks.value = tasks.value.filterNot { it.id == task.id } + task
        nodes[task.id] = NodeSummary(task.id, NodeType.TASK, task.title, false, false)
    }
    override suspend fun deleteTask(id: NodeId) {
        tasks.value = tasks.value.filterNot { it.id == id }
        reminders.value = reminders.value.filterNot { it.nodeId == id }
        nodes.remove(id)
    }
    override suspend fun saveEvent(event: CalendarEvent, now: Instant) {
        events.value = events.value.filterNot { it.id == event.id } + event
        nodes[event.id] = NodeSummary(event.id, NodeType.EVENT, event.title, false, false)
    }
    override suspend fun deleteEvent(id: NodeId) {
        events.value = events.value.filterNot { it.id == id }
        reminders.value = reminders.value.filterNot { it.nodeId == id }
        nodes.remove(id)
    }
    override suspend fun saveReminder(reminder: Reminder) {
        reminders.value = reminders.value.filterNot { it.id == reminder.id } + reminder
    }
    override suspend fun deleteReminder(id: NodeId) {
        reminders.value = reminders.value.filterNot { it.id == id }
    }
    override suspend fun recordCompletion(completion: TaskCompletion) {
        completions.value = completions.value + completion
    }
    override fun observeCompletions() = completions
}

class FakeProjectRepository(
    private val nodes: MutableMap<NodeId, NodeSummary> = linkedMapOf(),
    private val planning: FakePlanningRepository? = null,
) : ProjectRepository {
    val projects = MutableStateFlow<List<Project>>(emptyList())
    val milestones = MutableStateFlow<List<Milestone>>(emptyList())
    override fun observeProjects() = projects
    override fun observeMilestones() = milestones
    override suspend fun project(id: NodeId) = projects.value.firstOrNull { it.id == id }
    override suspend fun projects() = projects.value
    override suspend fun milestones() = milestones.value
    override suspend fun saveProject(project: Project, now: Instant) {
        projects.value = projects.value.filterNot { it.id == project.id } + project
        nodes[project.id] = NodeSummary(project.id, NodeType.PROJECT, project.name, false, false)
    }
    override suspend fun deleteProject(id: NodeId) {
        projects.value = projects.value.filterNot { it.id == id }
        milestones.value = milestones.value.filterNot { it.projectId == id }
        planning?.let { p -> p.tasks.value = p.tasks.value.map { if (it.projectId == id) it.copy(projectId = null) else it } }
        nodes.remove(id)
    }
    override suspend fun saveMilestone(milestone: Milestone, now: Instant) {
        milestones.value = milestones.value.filterNot { it.id == milestone.id } + milestone
    }
    override suspend fun deleteMilestone(id: NodeId) {
        milestones.value = milestones.value.filterNot { it.id == id }
    }
}
