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
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.ledger.accounts.AccountsRoute
import app.lifeos.feature.ledger.accounts.AccountsViewModel
import app.lifeos.feature.ledger.budgets.BudgetsRoute
import app.lifeos.feature.ledger.budgets.BudgetsViewModel
import app.lifeos.feature.ledger.editor.TransactionEditorRoute
import app.lifeos.feature.ledger.editor.TransactionEditorViewModel
import app.lifeos.feature.ledger.overview.FinanceNavigation
import app.lifeos.feature.ledger.overview.FinanceRoute
import app.lifeos.feature.ledger.overview.FinanceViewModel
import app.lifeos.feature.ledger.recurring.RecurringRoute
import app.lifeos.feature.ledger.recurring.RecurringViewModel
import app.lifeos.feature.ledger.transactions.TransactionsRoute
import app.lifeos.feature.ledger.transactions.TransactionsViewModel
import java.time.YearMonth

object LedgerRoutes {
    const val OVERVIEW = "finance"
    const val TRANSACTIONS = "finance/transactions?month={month}"
    const val EDIT = "finance/tx?id={id}&kind={kind}"
    const val RECURRING = "finance/recurring"
    const val BUDGETS = "finance/budgets"
    const val ACCOUNTS = "finance/accounts"

    fun transactions(month: YearMonth?) = if (month == null) "finance/transactions" else "finance/transactions?month=$month"
    fun edit(id: NodeId?, kind: TransactionKind? = null): String = buildString {
        append("finance/tx")
        val params = listOfNotNull(id?.let { "id=${it.value}" }, kind?.let { "kind=${it.name}" })
        if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
    }
}

/** Registriert alle Ziele des Bereichs Finanzen im App-Navigationsgraphen. */
fun NavGraphBuilder.ledgerGraph(navController: NavController, deps: LedgerDependencies) {
    composable(LedgerRoutes.OVERVIEW) {
        val vm: FinanceViewModel = viewModel(factory = viewModelFactory { initializer { FinanceViewModel(deps) } })
        FinanceRoute(
            viewModel = vm,
            nav = FinanceNavigation(
                openTransactions = { navController.navigate(LedgerRoutes.transactions(it)) },
                openTransaction = { navController.navigate(LedgerRoutes.edit(it)) },
                newTransaction = { navController.navigate(LedgerRoutes.edit(null)) },
                openBudgets = { navController.navigate(LedgerRoutes.BUDGETS) },
                openRecurring = { navController.navigate(LedgerRoutes.RECURRING) },
                openAccounts = { navController.navigate(LedgerRoutes.ACCOUNTS) },
            ),
        )
    }
    composable(
        LedgerRoutes.TRANSACTIONS,
        arguments = listOf(navArgument("month") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        val month = entry.arguments?.getString("month")?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        val vm: TransactionsViewModel = viewModel(factory = viewModelFactory { initializer { TransactionsViewModel(deps, month) } })
        TransactionsRoute(vm, onBack = { navController.popBackStack() }, onOpen = { navController.navigate(LedgerRoutes.edit(it)) })
    }
    composable(
        LedgerRoutes.EDIT,
        arguments = listOf(
            navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("kind") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { entry ->
        val id = entry.arguments?.getString("id")?.let { runCatching { NodeId(it) }.getOrNull() }
        val kind = entry.arguments?.getString("kind")?.let { runCatching { TransactionKind.valueOf(it) }.getOrNull() } ?: TransactionKind.EXPENSE
        val vm: TransactionEditorViewModel = viewModel(factory = viewModelFactory { initializer { TransactionEditorViewModel(deps, id, kind) } })
        TransactionEditorRoute(vm, onClose = { navController.popBackStack() })
    }
    composable(LedgerRoutes.RECURRING) {
        val vm: RecurringViewModel = viewModel(factory = viewModelFactory { initializer { RecurringViewModel(deps) } })
        RecurringRoute(vm, onBack = { navController.popBackStack() })
    }
    composable(LedgerRoutes.BUDGETS) {
        val vm: BudgetsViewModel = viewModel(factory = viewModelFactory { initializer { BudgetsViewModel(deps) } })
        BudgetsRoute(vm, onBack = { navController.popBackStack() })
    }
    composable(LedgerRoutes.ACCOUNTS) {
        val vm: AccountsViewModel = viewModel(factory = viewModelFactory { initializer { AccountsViewModel(deps) } })
        AccountsRoute(vm, onBack = { navController.popBackStack() })
    }
}
