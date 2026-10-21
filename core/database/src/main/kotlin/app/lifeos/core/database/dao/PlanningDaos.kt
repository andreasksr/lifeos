package app.lifeos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.lifeos.core.database.LifeOsDatabase
import app.lifeos.core.database.entity.CalendarEventEntity
import app.lifeos.core.database.entity.EventRow
import app.lifeos.core.database.entity.MilestoneEntity
import app.lifeos.core.database.entity.MilestoneRow
import app.lifeos.core.database.entity.NodeEntity
import app.lifeos.core.database.entity.ProjectEntity
import app.lifeos.core.database.entity.ProjectRow
import app.lifeos.core.database.entity.ReminderEntity
import app.lifeos.core.database.entity.TaskCompletionEntity
import app.lifeos.core.database.entity.TaskEntity
import app.lifeos.core.database.entity.TaskRow
import kotlinx.coroutines.flow.Flow

/** Aufgaben, Termine, Erinnerungen. Schreiben immer atomar (Knoten + Detailzeile). */
@Dao
abstract class PlanningDao(private val db: LifeOsDatabase) {
    private val nodes: NodeDao get() = db.nodeDao()

    @Query("SELECT t.*, n.title AS title, n.is_sample AS is_sample FROM task t JOIN node n ON n.id = t.node_id ORDER BY t.due_date IS NULL, t.due_date, t.due_time, n.title")
    abstract fun observeTasks(): Flow<List<TaskRow>>

    @Query("SELECT t.*, n.title AS title, n.is_sample AS is_sample FROM task t JOIN node n ON n.id = t.node_id ORDER BY t.due_date IS NULL, t.due_date, t.due_time, n.title")
    abstract suspend fun tasks(): List<TaskRow>

    @Query("SELECT t.*, n.title AS title, n.is_sample AS is_sample FROM task t JOIN node n ON n.id = t.node_id WHERE t.node_id = :id")
    abstract suspend fun task(id: String): TaskRow?

    @Query("SELECT e.*, n.title AS title, n.is_sample AS is_sample FROM calendar_event e JOIN node n ON n.id = e.node_id ORDER BY e.start_at")
    abstract fun observeEvents(): Flow<List<EventRow>>

    @Query("SELECT e.*, n.title AS title, n.is_sample AS is_sample FROM calendar_event e JOIN node n ON n.id = e.node_id ORDER BY e.start_at")
    abstract suspend fun events(): List<EventRow>

    @Query("SELECT e.*, n.title AS title, n.is_sample AS is_sample FROM calendar_event e JOIN node n ON n.id = e.node_id WHERE e.node_id = :id")
    abstract suspend fun event(id: String): EventRow?

    @Query("SELECT * FROM reminder ORDER BY minutes_before")
    abstract fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder ORDER BY minutes_before")
    abstract suspend fun reminders(): List<ReminderEntity>

    @Query("SELECT * FROM task_completion ORDER BY completed_at")
    abstract fun observeCompletions(): Flow<List<TaskCompletionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertTask(task: TaskEntity)

    @Update
    abstract suspend fun updateTask(task: TaskEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertEvent(event: CalendarEventEntity)

    @Update
    abstract suspend fun updateEvent(event: CalendarEventEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertReminder(reminder: ReminderEntity)

    @Update
    abstract suspend fun updateReminder(reminder: ReminderEntity): Int

    @Query("DELETE FROM reminder WHERE id = :id")
    abstract suspend fun deleteReminder(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertCompletion(completion: TaskCompletionEntity)

    @Transaction
    open suspend fun saveTask(node: NodeEntity, task: TaskEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertTask(task) else updateTask(task)
    }

    @Transaction
    open suspend fun saveEvent(node: NodeEntity, event: CalendarEventEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertEvent(event) else updateEvent(event)
    }

    @Transaction
    open suspend fun saveReminder(reminder: ReminderEntity) {
        if (updateReminder(reminder) == 0) insertReminder(reminder)
    }
}

/** Projekte und Meilensteine. Löschen über den Knoten – Kaskade + Trigger räumen auf. */
@Dao
abstract class ProjectDao(private val db: LifeOsDatabase) {
    private val nodes: NodeDao get() = db.nodeDao()

    @Query("SELECT p.*, n.title AS title, n.is_sample AS is_sample FROM project p JOIN node n ON n.id = p.node_id ORDER BY p.status = 'DONE', p.status = 'CANCELLED', n.title")
    abstract fun observeProjects(): Flow<List<ProjectRow>>

    @Query("SELECT p.*, n.title AS title, n.is_sample AS is_sample FROM project p JOIN node n ON n.id = p.node_id ORDER BY n.title")
    abstract suspend fun projects(): List<ProjectRow>

    @Query("SELECT p.*, n.title AS title, n.is_sample AS is_sample FROM project p JOIN node n ON n.id = p.node_id WHERE p.node_id = :id")
    abstract suspend fun project(id: String): ProjectRow?

    @Query("SELECT m.*, n.title AS title FROM milestone m JOIN node n ON n.id = m.node_id ORDER BY m.project_id, m.sort_order, m.due_date")
    abstract fun observeMilestones(): Flow<List<MilestoneRow>>

    @Query("SELECT m.*, n.title AS title FROM milestone m JOIN node n ON n.id = m.node_id ORDER BY m.project_id, m.sort_order, m.due_date")
    abstract suspend fun milestones(): List<MilestoneRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertProject(project: ProjectEntity)

    @Update
    abstract suspend fun updateProject(project: ProjectEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMilestone(milestone: MilestoneEntity)

    @Update
    abstract suspend fun updateMilestone(milestone: MilestoneEntity): Int

    @Transaction
    open suspend fun saveProject(node: NodeEntity, project: ProjectEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertProject(project) else updateProject(project)
    }

    @Transaction
    open suspend fun saveMilestone(node: NodeEntity, milestone: MilestoneEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertMilestone(milestone) else updateMilestone(milestone)
    }
}
