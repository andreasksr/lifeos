package app.lifeos.feature.planning

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.links.LinkNodes
import app.lifeos.core.domain.planning.CompleteTask
import app.lifeos.core.domain.planning.DeleteEvent
import app.lifeos.core.domain.planning.DeleteTask
import app.lifeos.core.domain.planning.PlanningRepository
import app.lifeos.core.domain.planning.ReminderScheduler
import app.lifeos.core.domain.planning.ReopenTask
import app.lifeos.core.domain.planning.SaveEvent
import app.lifeos.core.domain.planning.SaveTask
import app.lifeos.core.domain.planning.SetReminder
import app.lifeos.core.domain.ports.LinkRepository
import app.lifeos.core.domain.projects.ProjectRepository
import app.lifeos.core.domain.projects.UnlinkNodes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Zugriff auf die Android-Benachrichtigungsberechtigung. Implementiert in der App; das Feature
 * zeigt nur an, ob Erinnerungen tatsächlich zugestellt werden können, und bietet „Erlauben" an.
 */
interface NotificationAccess {
    val allowed: StateFlow<Boolean>
    fun request()
}

/** Alles, was der Bereich Planung braucht. Erzeugt im `AppContainer`, kennt nur Domänen-Ports. */
class PlanningDependencies(
    val repository: PlanningRepository,
    val projects: ProjectRepository,
    val links: LinkRepository,
    val clock: LifeClock,
    ids: IdGenerator,
    val scheduler: ReminderScheduler,
    val notifications: NotificationAccess,
    /** Überlebt einzelne Bildschirme – für „Rückgängig" nach dem Schließen. */
    val background: CoroutineScope,
) {
    val saveTask = SaveTask(repository, ids, clock, scheduler)
    val completeTask = CompleteTask(repository, clock, scheduler)
    val reopenTask = ReopenTask(repository, clock, scheduler)
    val deleteTask = DeleteTask(repository, scheduler)
    val saveEvent = SaveEvent(repository, ids, clock, scheduler)
    val deleteEvent = DeleteEvent(repository, scheduler)
    val setReminder = SetReminder(repository, ids, scheduler)
    val linkNodes = LinkNodes(links, ids, clock)
    val unlinkNodes = UnlinkNodes(links)
}
