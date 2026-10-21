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
import app.lifeos.feature.ledger.capture.QuickExpenseCapture
import app.lifeos.feature.ledger.ledgerGraph
import app.lifeos.navigation.LifeDestination

/** Was die zentrale Schnellerfassung anbietet – nur tatsächlich implementierte Typen. */
enum class CaptureType(val label: String, val glyph: Glyph) {
    EXPENSE("Ausgabe", Glyph.ARROW_OUT),
    INCOME("Einnahme", Glyph.ARROW_IN),
}

/** App-Rahmen: Theme, LifeFrame mit durchgehender LifeLine, Navigation, Schnellerfassung. */
@Composable
fun LifeOsShell(container: AppContainer, systemAnimationsOff: Boolean) {
    val motionPreference by container.settings.motionEnabled.collectAsStateWithLifecycle(initialValue = true)
    ContinuumTheme(motionEnabled = motionPreference && !systemAnimationsOff) {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val topLevel = LifeDestination.forRoute(backStack?.destination?.route)
        val signal = remember { LifeSignalState() }
        var capture by rememberSaveable { mutableStateOf<CaptureType?>(null) }
        val captureVm: QuickCaptureViewModel = viewModel(factory = viewModelFactory { initializer { QuickCaptureViewModel(container.ledger) } })

        fun openCapture(type: CaptureType) {
            capture = type
            captureVm.reset(if (type == CaptureType.INCOME) TransactionKind.INCOME else TransactionKind.EXPENSE)
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
                    HomeRoute(
                        vm = vm,
                        actions = HomeActions(
                            openFinance = { navController.navigateTopLevel(LifeDestination.FINANCE.route) },
                            openBudgets = { navController.navigate(LedgerRoutes.BUDGETS) },
                            openRecurring = { navController.navigate(LedgerRoutes.RECURRING) },
                            openTarget = { target ->
                                when (target) {
                                    AttentionTarget.Budgets -> navController.navigate(LedgerRoutes.BUDGETS)
                                    AttentionTarget.RecurringPayments -> navController.navigate(LedgerRoutes.RECURRING)
                                    is AttentionTarget.Node -> navController.navigate(LedgerRoutes.edit(target.id))
                                }
                            },
                            captureExpense = { openCapture(CaptureType.EXPENSE) },
                            captureIncome = { openCapture(CaptureType.INCOME) },
                        ),
                    )
                }
                ledgerGraph(navController, container.ledger)
                composable(LifeDestination.LIFE.route) {
                    LifeHubScreen(onOpenSystem = { navController.navigate(SYSTEM_ROUTE) })
                }
                composable(SYSTEM_ROUTE) {
                    SystemScreen(container, systemAnimationsOff, onBack = { navController.popBackStack() })
                }
            }
        }

        CaptureOverlay(capture, onSelect = ::openCapture, onDismiss = { capture = null }, captureVm = captureVm)
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
) {
    LifeOverlay(visible = type != null, onDismiss = onDismiss, title = "Erfassen", overline = "Schnellerfassung", fullHeight = true) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptureType.entries.forEach { t -> LifeChoiceChip(t.label, t == type, { onSelect(t) }, glyph = t.glyph) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (type) {
                CaptureType.EXPENSE, CaptureType.INCOME -> QuickExpenseCapture(captureVm, onDone = onDismiss)
                null -> Text("", style = Continuum.type.small)
            }
        }
    }
}
