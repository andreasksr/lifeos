package app.lifeos.feature.ledger

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.lifeos.core.model.NodeId
import app.lifeos.feature.ledger.editor.TransactionEditorRoute
import app.lifeos.feature.ledger.editor.TransactionEditorViewModel
import app.lifeos.feature.ledger.overview.LedgerRoute
import app.lifeos.feature.ledger.overview.LedgerViewModel
import app.lifeos.feature.ledger.structure.StructureRoute
import app.lifeos.feature.ledger.structure.StructureViewModel

object LedgerRoutes {
    const val OVERVIEW = "ledger"
    const val EDIT = "ledger/edit?id={id}"
    const val STRUCTURE = "ledger/structure"

    fun edit(id: NodeId?): String = if (id == null) "ledger/edit" else "ledger/edit?id=${id.value}"
}

/** Registriert alle Ziele der Haushaltskasse im App-Navigationsgraphen. */
fun NavGraphBuilder.ledgerGraph(navController: NavController, deps: LedgerDependencies) {
    composable(LedgerRoutes.OVERVIEW) {
        val vm: LedgerViewModel = viewModel(factory = viewModelFactory { initializer { LedgerViewModel(deps) } })
        LedgerRoute(
            viewModel = vm,
            onAddTransaction = { navController.navigate(LedgerRoutes.edit(null)) },
            onEditTransaction = { navController.navigate(LedgerRoutes.edit(it)) },
            onOpenStructure = { navController.navigate(LedgerRoutes.STRUCTURE) },
        )
    }
    composable(
        route = LedgerRoutes.EDIT,
        arguments = listOf(
            navArgument("id") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { entry ->
        val id = entry.arguments?.getString("id")?.let { runCatching { NodeId(it) }.getOrNull() }
        val vm: TransactionEditorViewModel = viewModel(
            factory = viewModelFactory { initializer { TransactionEditorViewModel(deps, id) } },
        )
        TransactionEditorRoute(vm, onClose = { navController.popBackStack() })
    }
    composable(LedgerRoutes.STRUCTURE) {
        val vm: StructureViewModel = viewModel(factory = viewModelFactory { initializer { StructureViewModel(deps) } })
        StructureRoute(vm, onBack = { navController.popBackStack() })
    }
}
