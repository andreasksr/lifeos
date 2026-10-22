package app.lifeos.feature.life

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.lifeos.core.model.NodeId
import app.lifeos.feature.life.assets.AssetDetailNavigation
import app.lifeos.feature.life.assets.AssetDetailRoute
import app.lifeos.feature.life.assets.AssetDetailViewModel
import app.lifeos.feature.life.assets.AssetEditorRoute
import app.lifeos.feature.life.assets.AssetEditorViewModel
import app.lifeos.feature.life.assets.AssetsRoute
import app.lifeos.feature.life.assets.AssetsViewModel
import app.lifeos.feature.life.documents.DocumentDetailNavigation
import app.lifeos.feature.life.documents.DocumentDetailRoute
import app.lifeos.feature.life.documents.DocumentDetailViewModel
import app.lifeos.feature.life.documents.DocumentsRoute
import app.lifeos.feature.life.documents.DocumentsViewModel
import app.lifeos.feature.life.hub.LifeHubNavigation
import app.lifeos.feature.life.hub.LifeHubRoute
import app.lifeos.feature.life.hub.LifeHubViewModel
import app.lifeos.feature.life.learning.LearningNavigation
import app.lifeos.feature.life.learning.LearningRoute
import app.lifeos.feature.life.learning.LearningViewModel
import app.lifeos.feature.life.vehicles.VehicleDetailNavigation
import app.lifeos.feature.life.vehicles.VehicleDetailRoute
import app.lifeos.feature.life.vehicles.VehicleDetailViewModel
import app.lifeos.feature.life.vehicles.VehicleEditorRoute
import app.lifeos.feature.life.vehicles.VehicleEditorViewModel
import app.lifeos.feature.life.vehicles.VehiclesRoute
import app.lifeos.feature.life.vehicles.VehiclesViewModel

object LifeRoutes {
    const val HUB = "life"
    const val VEHICLES = "life/vehicles"
    const val VEHICLE = "life/vehicle/{id}"
    const val VEHICLE_EDIT = "life/vehicle-edit?id={id}"
    const val ASSETS = "life/assets"
    const val ASSET = "life/asset/{id}"
    const val ASSET_EDIT = "life/asset-edit?id={id}"
    const val DOCUMENTS = "life/documents?pick={pick}"
    const val DOCUMENT = "life/document/{id}"
    const val LEARNING = "life/learning"

    fun vehicle(id: NodeId) = "life/vehicle/${id.value}"
    fun vehicleEdit(id: NodeId?) = if (id == null) "life/vehicle-edit" else "life/vehicle-edit?id=${id.value}"
    fun asset(id: NodeId) = "life/asset/${id.value}"
    fun assetEdit(id: NodeId?) = if (id == null) "life/asset-edit" else "life/asset-edit?id=${id.value}"
    fun document(id: NodeId) = "life/document/${id.value}"
    fun documents(pick: Boolean = false) = if (pick) "life/documents?pick=true" else "life/documents"
}

/** Verbindungen in andere Bereiche – von der App gesetzt. */
data class LifeCrossNavigation(
    val openTransaction: (NodeId) -> Unit,
    val openNode: (NodeId) -> Unit,
    val openEvent: (NodeId) -> Unit,
    val newLesson: () -> Unit,
    val openSystem: () -> Unit,
    val openMap: ((NodeId?) -> Unit)? = null,
    val openSearch: (() -> Unit)? = null,
)

private fun idArg() = listOf(navArgument("id") { type = NavType.StringType })
private fun optionalIdArg() = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })

fun NavGraphBuilder.lifeGraph(navController: NavController, deps: LifeDependencies, cross: LifeCrossNavigation) {
    val back: () -> Unit = { navController.popBackStack() }

    composable(LifeRoutes.HUB) {
        val vm: LifeHubViewModel = viewModel(factory = viewModelFactory { initializer { LifeHubViewModel(deps) } })
        LifeHubRoute(
            vm,
            LifeHubNavigation(
                vehicles = { navController.navigate(LifeRoutes.VEHICLES) },
                assets = { navController.navigate(LifeRoutes.ASSETS) },
                documents = { navController.navigate(LifeRoutes.documents()) },
                learning = { navController.navigate(LifeRoutes.LEARNING) },
                system = cross.openSystem,
                map = cross.openMap?.let { open -> { open(null) } },
                search = cross.openSearch,
            ),
        )
    }

    // ── Fahrzeuge ──
    composable(LifeRoutes.VEHICLES) {
        val vm: VehiclesViewModel = viewModel(factory = viewModelFactory { initializer { VehiclesViewModel(deps) } })
        VehiclesRoute(vm, back, onOpen = { navController.navigate(LifeRoutes.vehicle(it)) }, onNew = { navController.navigate(LifeRoutes.vehicleEdit(null)) })
    }
    composable(LifeRoutes.VEHICLE, arguments = idArg()) { entry ->
        val id = NodeId(requireNotNull(entry.arguments?.getString("id")))
        val vm: VehicleDetailViewModel = viewModel(factory = viewModelFactory { initializer { VehicleDetailViewModel(deps, id) } })
        VehicleDetailRoute(
            vm,
            VehicleDetailNavigation(
                back = back,
                edit = { navController.navigate(LifeRoutes.vehicleEdit(id)) },
                openTransaction = cross.openTransaction,
                openDocument = { navController.navigate(LifeRoutes.document(it)) },
                openNode = cross.openNode,
                openMap = cross.openMap?.let { open -> { open(id) } },
            ),
        )
    }
    composable(LifeRoutes.VEHICLE_EDIT, arguments = optionalIdArg()) { entry ->
        val id = entry.arguments?.getString("id")?.let { runCatching { NodeId(it) }.getOrNull() }
        val vm: VehicleEditorViewModel = viewModel(factory = viewModelFactory { initializer { VehicleEditorViewModel(deps, id) } })
        VehicleEditorRoute(vm, onClose = back, onSaved = { savedId, wasEdit ->
            navController.popBackStack()
            if (!wasEdit) navController.navigate(LifeRoutes.vehicle(savedId))
        })
    }

    // ── Inventar ──
    composable(LifeRoutes.ASSETS) {
        val vm: AssetsViewModel = viewModel(factory = viewModelFactory { initializer { AssetsViewModel(deps) } })
        AssetsRoute(vm, back, onOpen = { navController.navigate(LifeRoutes.asset(it)) }, onNew = { navController.navigate(LifeRoutes.assetEdit(null)) })
    }
    composable(LifeRoutes.ASSET, arguments = idArg()) { entry ->
        val id = NodeId(requireNotNull(entry.arguments?.getString("id")))
        val vm: AssetDetailViewModel = viewModel(factory = viewModelFactory { initializer { AssetDetailViewModel(deps, id) } })
        AssetDetailRoute(
            vm,
            AssetDetailNavigation(
                back = back,
                edit = { navController.navigate(LifeRoutes.assetEdit(id)) },
                openTransaction = cross.openTransaction,
                openDocument = { navController.navigate(LifeRoutes.document(it)) },
                openNode = cross.openNode,
                openMap = cross.openMap?.let { open -> { open(id) } },
            ),
        )
    }
    composable(LifeRoutes.ASSET_EDIT, arguments = optionalIdArg()) { entry ->
        val id = entry.arguments?.getString("id")?.let { runCatching { NodeId(it) }.getOrNull() }
        val vm: AssetEditorViewModel = viewModel(factory = viewModelFactory { initializer { AssetEditorViewModel(deps, id) } })
        AssetEditorRoute(vm, onClose = back, onSaved = { savedId, wasEdit ->
            navController.popBackStack()
            if (!wasEdit) navController.navigate(LifeRoutes.asset(savedId))
        })
    }

    // ── Dokumente ──
    composable(LifeRoutes.DOCUMENTS, arguments = listOf(navArgument("pick") { type = NavType.BoolType; defaultValue = false })) { entry ->
        val vm: DocumentsViewModel = viewModel(factory = viewModelFactory { initializer { DocumentsViewModel(deps) } })
        DocumentsRoute(vm, back, onOpen = { navController.navigate(LifeRoutes.document(it)) }, autoPick = entry.arguments?.getBoolean("pick") == true)
    }
    composable(LifeRoutes.DOCUMENT, arguments = idArg()) { entry ->
        val id = NodeId(requireNotNull(entry.arguments?.getString("id")))
        val vm: DocumentDetailViewModel = viewModel(factory = viewModelFactory { initializer { DocumentDetailViewModel(deps, id) } })
        DocumentDetailRoute(vm, DocumentDetailNavigation(back = back, openNode = cross.openNode))
    }

    // ── Weiterbildung ──
    composable(LifeRoutes.LEARNING) {
        val vm: LearningViewModel = viewModel(factory = viewModelFactory { initializer { LearningViewModel(deps) } })
        LearningRoute(vm, LearningNavigation(back = back, newLesson = cross.newLesson, openEvent = cross.openEvent))
    }
}
