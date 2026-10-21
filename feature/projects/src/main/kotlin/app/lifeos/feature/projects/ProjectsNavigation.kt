package app.lifeos.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeLink
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.domain.home.AttentionItem
import app.lifeos.core.domain.home.AttentionSeverity
import app.lifeos.core.domain.home.AttentionTarget
import app.lifeos.core.domain.projects.ProjectCard
import app.lifeos.core.domain.projects.ProjectOverview
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.ProjectStatus
import app.lifeos.feature.projects.detail.ProjectDetailNavigation
import app.lifeos.feature.projects.detail.ProjectDetailRoute
import app.lifeos.feature.projects.detail.ProjectDetailViewModel
import app.lifeos.feature.projects.editor.ProjectEditorRoute
import app.lifeos.feature.projects.editor.ProjectEditorViewModel
import app.lifeos.feature.projects.list.ProjectsRoute
import app.lifeos.feature.projects.list.ProjectsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object ProjectsRoutes {
    const val OVERVIEW = "projects"
    const val DETAIL = "projects/detail/{id}"
    const val EDIT = "projects/edit?id={id}"
    fun detail(id: NodeId) = "projects/detail/${id.value}"
    fun edit(id: NodeId?) = if (id == null) "projects/edit" else "projects/edit?id=${id.value}"
}

/** Verbindungen in andere Bereiche (Aufgaben-Editor, Buchung, LifeMap) – von der App gesetzt. */
data class ProjectsCrossNavigation(
    val openTask: (NodeId) -> Unit,
    val newTask: (projectId: NodeId) -> Unit,
    val openTransaction: (NodeId) -> Unit,
    val openNode: (NodeId) -> Unit,
    val openMap: ((NodeId) -> Unit)? = null,
)

fun NavGraphBuilder.projectsGraph(navController: NavController, deps: ProjectsDependencies, cross: ProjectsCrossNavigation) {
    composable(ProjectsRoutes.OVERVIEW) {
        val vm: ProjectsViewModel = viewModel(factory = viewModelFactory { initializer { ProjectsViewModel(deps) } })
        ProjectsRoute(vm, openProject = { navController.navigate(ProjectsRoutes.detail(it)) }, newProject = { navController.navigate(ProjectsRoutes.edit(null)) })
    }
    composable(ProjectsRoutes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
        val id = NodeId(requireNotNull(entry.arguments?.getString("id")))
        val vm: ProjectDetailViewModel = viewModel(factory = viewModelFactory { initializer { ProjectDetailViewModel(deps, id) } })
        ProjectDetailRoute(
            vm,
            ProjectDetailNavigation(
                back = { navController.popBackStack() },
                edit = { navController.navigate(ProjectsRoutes.edit(id)) },
                openTask = cross.openTask,
                newTask = { cross.newTask(id) },
                openTransaction = cross.openTransaction,
                openNode = cross.openNode,
                openMap = cross.openMap?.let { open -> { open(id) } },
            ),
        )
    }
    composable(ProjectsRoutes.EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
        val id = entry.arguments?.getString("id")?.let { runCatching { NodeId(it) }.getOrNull() }
        val vm: ProjectEditorViewModel = viewModel(factory = viewModelFactory { initializer { ProjectEditorViewModel(deps, id) } })
        ProjectEditorRoute(
            vm,
            onClose = { navController.popBackStack() },
            onSaved = { savedId, wasEdit ->
                navController.popBackStack()
                if (!wasEdit) navController.navigate(ProjectsRoutes.detail(savedId))
            },
        )
    }
}

// ───────────────────────────── Home ─────────────────────────────

class ProjectsHomeViewModel(deps: ProjectsDependencies) : ViewModel() {
    val cards: StateFlow<List<ProjectCard>?> = combine(
        deps.projects.observeProjects(),
        deps.planning.observeTasks(),
        deps.projects.observeMilestones(),
        deps.links.observeAllLinks(),
        deps.ledger.observeAllTransactions(),
    ) { p, t, m, l, tx -> ProjectOverview.build(p, t, m, l, tx).filter { it.project.status == ProjectStatus.ACTIVE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** „Aufmerksamkeit erforderlich" aus Projekten: überschrittene Zieltermine und Budgets, fällige Meilensteine. */
fun projectAttention(deps: ProjectsDependencies, today: () -> LocalDate): Flow<List<AttentionItem>> = combine(
    deps.projects.observeProjects(),
    deps.planning.observeTasks(),
    deps.projects.observeMilestones(),
    deps.links.observeAllLinks(),
    deps.ledger.observeAllTransactions(),
) { p, t, m, l, tx ->
    val d = today()
    ProjectOverview.build(p, t, m, l, tx).filter { it.project.status == ProjectStatus.ACTIVE }.flatMap { c ->
        buildList {
            c.project.targetDate?.let { target ->
                val days = ChronoUnit.DAYS.between(d, target)
                if (days < 0) add(AttentionItem(c.project.name, "Projekt-Zieltermin seit ${-days} Tagen überschritten", AttentionSeverity.WARN, AttentionTarget.Node(c.project.id), days))
            }
            if ((c.costs.remainingMinor ?: 0) < 0) {
                add(AttentionItem(c.project.name, "Projektbudget überschritten", AttentionSeverity.WARN, AttentionTarget.Node(c.project.id), 0))
            }
            val ms = c.nextMilestone
            ms?.dueDate?.let { due ->
                val days = ChronoUnit.DAYS.between(d, due)
                if (days in 0..3) add(AttentionItem(ms.title, "Meilenstein · ${LifeFormat.relativeDue(due, d)} · ${c.project.name}", AttentionSeverity.INFO, AttentionTarget.Node(c.project.id), days))
                if (days < 0) add(AttentionItem(ms.title, "Meilenstein überfällig · ${c.project.name}", AttentionSeverity.WARN, AttentionTarget.Node(c.project.id), days))
            }
        }
    }
}

@Composable
fun ProjectsHomeSection(vm: ProjectsHomeViewModel, index: Int, advanced: Boolean, openProjects: () -> Unit, openProject: (NodeId) -> Unit, newProject: () -> Unit) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val list = cards ?: return
    SpineSection(
        "Projekte", index = index, shape = NodeShape.LINKED, tone = if (list.isNotEmpty()) NodeTone.ACTIVE else NodeTone.NEUTRAL,
        onOpen = openProjects, openLabel = "Projekte öffnen",
        trailing = { LifeLink("Öffnen", openProjects) },
    ) {
        if (list.isEmpty()) {
            LifeEmpty("Keine aktiven Projekte.", actionLabel = "Projekt anlegen", onAction = newProject)
            return@SpineSection
        }
        list.take(if (advanced) 5 else 3).forEach { c ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LifeNode(shape = NodeShape.LINKED, tone = NodeTone.ACTIVE, size = 12.dp, onClick = { openProject(c.project.id) }, onClickLabel = "${c.project.name} öffnen")
                    Text(c.project.name, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    c.progress.permille?.let { Text(LifeFormat.percent(it.toLong()), style = Continuum.type.figuresSmall, color = ContinuumColors.Muted) }
                }
                c.progress.permille?.let { LifeLine(progress = it / 1000f, meaning = LineMeaning.PROGRESS) }
                val next = c.nextMilestone?.title?.let { "Meilenstein: $it" } ?: c.nextTask?.title?.let { "Als Nächstes: $it" }
                if (next != null) Text(next, style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
