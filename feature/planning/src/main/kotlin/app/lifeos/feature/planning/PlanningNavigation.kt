package app.lifeos.feature.planning

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.lifeos.core.model.NodeId
import app.lifeos.feature.planning.editor.EventEditorRoute
import app.lifeos.feature.planning.editor.EventEditorViewModel
import app.lifeos.feature.planning.editor.TaskEditorRoute
import app.lifeos.feature.planning.editor.TaskEditorViewModel
import app.lifeos.feature.planning.overview.PlanningNavigation
import app.lifeos.feature.planning.overview.PlanningRoute
import app.lifeos.feature.planning.overview.PlanningViewModel
import java.time.LocalDate

object PlanningRoutes {
    const val OVERVIEW = "planning"
    const val TASK = "planning/task?id={id}&date={date}&project={project}"
    const val EVENT = "planning/event?id={id}&date={date}"

    fun task(id: NodeId?, date: LocalDate? = null, project: NodeId? = null): String =
        "planning/task" + query("id" to id?.value, "date" to date?.toString(), "project" to project?.value)

    fun event(id: NodeId?, date: LocalDate? = null): String =
        "planning/event" + query("id" to id?.value, "date" to date?.toString())

    private fun query(vararg p: Pair<String, String?>): String {
        val parts = p.filter { it.second != null }.map { "${it.first}=${it.second}" }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }
}

private fun optionalString(name: String) = navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }

fun NavGraphBuilder.planningGraph(navController: NavController, deps: PlanningDependencies) {
    composable(PlanningRoutes.OVERVIEW) {
        val vm: PlanningViewModel = viewModel(factory = viewModelFactory { initializer { PlanningViewModel(deps) } })
        PlanningRoute(
            vm,
            PlanningNavigation(
                openTask = { id, date -> navController.navigate(PlanningRoutes.task(id, date)) },
                openEvent = { id, date -> navController.navigate(PlanningRoutes.event(id, date)) },
            ),
        )
    }
    composable(PlanningRoutes.TASK, arguments = listOf(optionalString("id"), optionalString("date"), optionalString("project"))) { entry ->
        val args = entry.arguments
        val id = args?.getString("id")?.let { runCatching { NodeId(it) }.getOrNull() }
        val date = args?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val project = args?.getString("project")?.let { runCatching { NodeId(it) }.getOrNull() }
        val vm: TaskEditorViewModel = viewModel(factory = viewModelFactory { initializer { TaskEditorViewModel(deps, id, date, project) } })
        TaskEditorRoute(vm, onClose = { navController.popBackStack() })
    }
    composable(PlanningRoutes.EVENT, arguments = listOf(optionalString("id"), optionalString("date"))) { entry ->
        val args = entry.arguments
        val id = args?.getString("id")?.let { runCatching { NodeId(it) }.getOrNull() }
        val date = args?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val vm: EventEditorViewModel = viewModel(factory = viewModelFactory { initializer { EventEditorViewModel(deps, id, date) } })
        EventEditorRoute(vm, onClose = { navController.popBackStack() })
    }
}
