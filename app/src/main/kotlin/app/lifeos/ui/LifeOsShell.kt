package app.lifeos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.lifeos.AppContainer
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeCaptureButton
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeFrame
import app.lifeos.core.designsystem.component.LifeNavItem
import app.lifeos.core.designsystem.component.LifeNavigation
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeSignalState
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumTheme
import app.lifeos.core.domain.home.AttentionTarget
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.home.HomeActions
import app.lifeos.feature.home.HomeRoute
import app.lifeos.feature.home.HomeViewModel
import app.lifeos.feature.ledger.LedgerRoutes
import app.lifeos.feature.ledger.capture.QuickCaptureViewModel
import app.lifeos.feature.ledger.capture.QuickCaptureSaveBar
import app.lifeos.feature.ledger.capture.QuickExpenseCapture
import app.lifeos.feature.ledger.ledgerGraph
import app.lifeos.navigation.LifeDestination
import app.lifeos.core.domain.home.HomeDensity
import app.lifeos.core.domain.home.HomeSectionId
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeType
import app.lifeos.feature.planning.PlanningRoutes
import app.lifeos.feature.planning.capture.QuickTaskCapture
import app.lifeos.feature.planning.capture.QuickTaskSaveBar
import app.lifeos.feature.planning.capture.QuickTaskViewModel
import app.lifeos.feature.planning.home.PlanningHomeNavigation
import app.lifeos.feature.planning.home.PlanningHomeSection
import app.lifeos.feature.planning.home.PlanningHomeViewModel
import app.lifeos.feature.planning.home.PlanningTodayBlock
import app.lifeos.feature.planning.planningGraph
import app.lifeos.feature.projects.ProjectsCrossNavigation
import app.lifeos.feature.projects.ProjectsHomeSection
import app.lifeos.feature.projects.ProjectsHomeViewModel
import app.lifeos.feature.projects.ProjectsRoutes
import app.lifeos.feature.projects.projectsGraph
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Was die zentrale Schnellerfassung anbietet – nur tatsächlich implementierte Typen. */
enum class CaptureType(val label: String, val glyph: Glyph) {
    EXPENSE("Ausgabe", Glyph.ARROW_OUT),
    INCOME("Einnahme", Glyph.ARROW_IN),
    TASK("Aufgabe", Glyph.TASKS),
    EVENT("Termin", Glyph.CALENDAR),
    PROJECT("Projekt", Glyph.PROJECT),
}

/** App-Rahmen: Theme, LifeFrame mit durchgehender LifeLine, Navigation, Schnellerfassung. */
@Composable
fun LifeOsShell(container: AppContainer, systemAnimationsOff: Boolean, pendingOpen: MutableStateFlow<String?>) {
    val motionPreference by container.settings.motionEnabled.collectAsStateWithLifecycle(initialValue = true)
    ContinuumTheme(motionEnabled = motionPreference && !systemAnimationsOff) {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val topLevel = LifeDestination.forRoute(backStack?.destination?.route)
        val signal = remember { LifeSignalState() }
        var capture by rememberSaveable { mutableStateOf<CaptureType?>(null) }
        val captureVm: QuickCaptureViewModel = viewModel(factory = viewModelFactory { initializer { QuickCaptureViewModel(container.ledger) } })
        val taskCaptureVm: QuickTaskViewModel = viewModel(factory = viewModelFactory { initializer { QuickTaskViewModel(container.planning) } })

        fun openCapture(type: CaptureType) {
            when (type) {
                CaptureType.EXPENSE, CaptureType.INCOME -> {
                    val kind = if (type == CaptureType.INCOME) TransactionKind.INCOME else TransactionKind.EXPENSE
                    if (capture == null || capture == CaptureType.TASK) captureVm.reset(kind) else captureVm.kind(kind)
                }
                CaptureType.TASK -> if (capture != CaptureType.TASK) taskCaptureVm.reset()
                CaptureType.EVENT -> {
                    // Termine brauchen Zeit und Ort – direkt der vollständige Editor.
                    capture = null
                    navController.navigate(PlanningRoutes.event(null, container.clock.today()))
                    return
                }
                CaptureType.PROJECT -> {
                    capture = null
                    navController.navigate(ProjectsRoutes.edit(null))
                    return
                }
            }
            capture = type
        }

        // Ziele aus Benachrichtigungen/Widget öffnen („task:<id>", „event:<id>", „capture:expense" …)
        val open by pendingOpen.collectAsStateWithLifecycle()
        LaunchedEffect(open) {
            val target = open ?: return@LaunchedEffect
            pendingOpen.value = null
            val (kind, value) = target.split(':', limit = 2).let { it[0] to it.getOrNull(1) }
            val id = value?.let { runCatching { NodeId(it) }.getOrNull() }
            when (kind) {
                "task" -> if (id != null) navController.navigate(PlanningRoutes.task(id))
                "event" -> if (id != null) navController.navigate(PlanningRoutes.event(id))
                "planning" -> navController.navigateTopLevel(LifeDestination.PLANNING.route)
                "capture" -> openCapture(if (value == "task") CaptureType.TASK else CaptureType.EXPENSE)
            }
        }

        /** Öffnet einen beliebigen Eintrag anhand seiner ID (Typ wird über die Datenbank ermittelt). */
        val scope = rememberCoroutineScope()
        fun openNode(id: NodeId) {
            scope.launch {
                when (container.linkRepository.node(id)?.type) {
                    NodeType.TASK -> navController.navigate(PlanningRoutes.task(id))
                    NodeType.EVENT -> navController.navigate(PlanningRoutes.event(id))
                    NodeType.TRANSACTION -> navController.navigate(LedgerRoutes.edit(id))
                    NodeType.PROJECT, NodeType.MILESTONE -> {
                        val projectId = if (container.linkRepository.node(id)?.type == NodeType.MILESTONE) {
                            container.projectRepository.milestones().firstOrNull { it.id == id }?.projectId
                        } else {
                            id
                        }
                        projectId?.let { navController.navigate(ProjectsRoutes.detail(it)) }
                    }
                    else -> Unit
                }
            }
        }

        LifeFrame(
            signal = signal,
            navigation = if (topLevel != null) {
                { start ->
                    LifeNavigation(
                        items = LifeDestination.visible.map { LifeNavItem(it.route, it.label, it.glyph) },
                        selectedKey = topLevel.route,
                        onSelect = { route -> navController.navigateTopLevel(route) },
                        lineStartX = start,
                    )
                }
            } else {
                null
            },
            capture = { LifeCaptureButton(onClick = { openCapture(CaptureType.EXPENSE) }) },
        ) {
            NavHost(navController = navController, startDestination = LifeDestination.HOME.route) {
                composable(LifeDestination.HOME.route) {
                    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container.home) } })
                    val planningHome: PlanningHomeViewModel = viewModel(factory = viewModelFactory { initializer { PlanningHomeViewModel(container.planning) } })
                    val planningNav = PlanningHomeNavigation(
                        openTask = { navController.navigate(PlanningRoutes.task(it)) },
                        openEvent = { navController.navigate(PlanningRoutes.event(it)) },
                        openPlanning = { navController.navigateTopLevel(LifeDestination.PLANNING.route) },
                        newTask = { openCapture(CaptureType.TASK) },
                    )
                    val projectsHome: ProjectsHomeViewModel = viewModel(factory = viewModelFactory { initializer { ProjectsHomeViewModel(container.projects) } })
                    HomeRoute(
                        vm = vm,
                        sectionContent = mapOf(
                            HomeSectionId.PLANNING to { index, density -> PlanningHomeSection(planningHome, planningNav, index, density == HomeDensity.ADVANCED) },
                            HomeSectionId.PROJECTS to { index, density ->
                                ProjectsHomeSection(
                                    projectsHome, index, density == HomeDensity.ADVANCED,
                                    openProjects = { navController.navigateTopLevel(LifeDestination.PROJECTS.route) },
                                    openProject = { navController.navigate(ProjectsRoutes.detail(it)) },
                                    newProject = { navController.navigate(ProjectsRoutes.edit(null)) },
                                )
                            },
                        ),
                        todayContent = { density ->
                            PlanningTodayBlock(
                                planningHome, planningNav,
                                maxItems = when (density) {
                                    HomeDensity.MINIMAL -> 3
                                    HomeDensity.STANDARD -> 6
                                    HomeDensity.ADVANCED -> 12
                                },
                            )
                        },
                        actions = HomeActions(
                            openFinance = { navController.navigateTopLevel(LifeDestination.FINANCE.route) },
                            openBudgets = { navController.navigate(LedgerRoutes.BUDGETS) },
                            openRecurring = { navController.navigate(LedgerRoutes.RECURRING) },
                            openTarget = { target ->
                                when (target) {
                                    AttentionTarget.Budgets -> navController.navigate(LedgerRoutes.BUDGETS)
                                    AttentionTarget.RecurringPayments -> navController.navigate(LedgerRoutes.RECURRING)
                                    is AttentionTarget.Node -> openNode(target.id)
                                }
                            },
                            captureExpense = { openCapture(CaptureType.EXPENSE) },
                            captureIncome = { openCapture(CaptureType.INCOME) },
                            extraCaptures = listOf(
                                Triple("Aufgabe", Glyph.TASKS) { openCapture(CaptureType.TASK) },
                                Triple("Termin", Glyph.CALENDAR) { openCapture(CaptureType.EVENT) },
                                Triple("Projekt", Glyph.PROJECT) { openCapture(CaptureType.PROJECT) },
                            ),
                        ),
                    )
                }
                ledgerGraph(navController, container.ledger)
                planningGraph(navController, container.planning)
                projectsGraph(
                    navController, container.projects,
                    ProjectsCrossNavigation(
                        openTask = { navController.navigate(PlanningRoutes.task(it)) },
                        newTask = { projectId -> navController.navigate(PlanningRoutes.task(null, null, projectId)) },
                        openTransaction = { navController.navigate(LedgerRoutes.edit(it)) },
                        openNode = { openNode(it) },
                    ),
                )
                composable(LifeDestination.LIFE.route) {
                    LifeHubScreen(onOpenSystem = { navController.navigate(SYSTEM_ROUTE) })
                }
                composable(SYSTEM_ROUTE) {
                    SystemScreen(container, systemAnimationsOff, onBack = { navController.popBackStack() })
                }
            }
        }

        CaptureOverlay(capture, onSelect = ::openCapture, onDismiss = { capture = null }, captureVm = captureVm, taskVm = taskCaptureVm)
    }
}

const val SYSTEM_ROUTE = "life/system"

fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureOverlay(
    type: CaptureType?,
    onSelect: (CaptureType) -> Unit,
    onDismiss: () -> Unit,
    captureVm: QuickCaptureViewModel,
    taskVm: QuickTaskViewModel,
) {
    LifeOverlay(visible = type != null, onDismiss = onDismiss, title = "Erfassen", overline = "Schnellerfassung", fullHeight = true) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptureType.entries.forEach { t -> LifeChoiceChip(t.label, t == type, { onSelect(t) }, glyph = t.glyph) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (type) {
                CaptureType.EXPENSE, CaptureType.INCOME -> QuickExpenseCapture(captureVm, onDone = onDismiss)
                CaptureType.TASK -> QuickTaskCapture(taskVm, onDone = onDismiss)
                CaptureType.EVENT, CaptureType.PROJECT, null -> Text("", style = Continuum.type.small)
            }
        }
        when (type) {
            CaptureType.EXPENSE, CaptureType.INCOME -> QuickCaptureSaveBar(captureVm)
            CaptureType.TASK -> QuickTaskSaveBar(taskVm)
            CaptureType.EVENT, CaptureType.PROJECT, null -> Unit
        }
    }
}
