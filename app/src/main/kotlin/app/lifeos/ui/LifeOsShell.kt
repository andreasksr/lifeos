package app.lifeos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.lifeos.AppContainer
import app.lifeos.core.designsystem.component.LifeNavItem
import app.lifeos.core.designsystem.component.LifeNavigation
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumTheme
import app.lifeos.feature.ledger.LedgerRoutes
import app.lifeos.feature.ledger.ledgerGraph
import app.lifeos.navigation.LifeDestination

/** App-Rahmen: Theme, Navigationsgraph und LifeNavigation für die Hauptbereiche. */
@Composable
fun LifeOsShell(container: AppContainer, systemAnimationsOff: Boolean) {
    val motionPreference by container.settings.motionEnabled.collectAsStateWithLifecycle(initialValue = true)
    ContinuumTheme(motionEnabled = motionPreference && !systemAnimationsOff) {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val topLevel = LifeDestination.forRoute(currentRoute)

        Column(
            Modifier
                .fillMaxSize()
                .background(ContinuumColors.Obsidian),
        ) {
            Box(Modifier.weight(1f)) {
                NavHost(navController = navController, startDestination = LedgerRoutes.OVERVIEW) {
                    ledgerGraph(navController, container.ledger)
                    composable(LifeDestination.SYSTEM.route) {
                        SystemScreen(container, systemAnimationsOff)
                    }
                }
            }
            // Navigation nur auf Hauptebenen; Unterseiten (Formulare) bekommen den vollen Raum.
            if (topLevel != null) {
                LifeNavigation(
                    items = LifeDestination.visible.map { LifeNavItem(it.route, it.label, it.shape) },
                    selectedKey = topLevel.route,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}
