package app.lifeos.feature.projects

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.planning.PlanningRepository
import app.lifeos.core.domain.ports.LedgerRepository
import app.lifeos.core.domain.ports.LinkRepository
import app.lifeos.core.domain.projects.AssignTransactionToProject
import app.lifeos.core.domain.projects.DeleteProject
import app.lifeos.core.domain.projects.ProjectRepository
import app.lifeos.core.domain.projects.SaveMilestone
import app.lifeos.core.domain.projects.SaveProject
import app.lifeos.core.domain.projects.UnlinkNodes
import app.lifeos.core.model.Task
import kotlinx.coroutines.CoroutineScope

/**
 * Alles für den Projektmanager. Aufgaben gehören fachlich zur Planung – das Abhaken läuft deshalb über
 * [toggleTask] (CompleteTask/ReopenTask der Planung inkl. Erinnerungen), nicht über eine Kopie der Logik.
 */
class ProjectsDependencies(
    val projects: ProjectRepository,
    val planning: PlanningRepository,
    val ledger: LedgerRepository,
    val links: LinkRepository,
    val clock: LifeClock,
    ids: IdGenerator,
    val toggleTask: suspend (Task) -> Unit,
    val background: CoroutineScope,
) {
    val saveProject = SaveProject(projects, ids, clock)
    val deleteProject = DeleteProject(projects)
    val saveMilestone = SaveMilestone(projects, ids, clock)
    val assignTransaction = AssignTransactionToProject(links, ledger, projects, ids, clock)
    val unlink = UnlinkNodes(links)
}
