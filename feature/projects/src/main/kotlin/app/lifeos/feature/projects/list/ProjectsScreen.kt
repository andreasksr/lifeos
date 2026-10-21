package app.lifeos.feature.projects.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeSegmented
import app.lifeos.core.designsystem.component.LifeTag
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineRow
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.domain.projects.ProjectCard
import app.lifeos.core.domain.projects.ProjectOverview
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.ProjectStatus
import app.lifeos.feature.projects.ProjectsDependencies
import app.lifeos.feature.projects.ProjectsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ProjectFilter { ACTIVE, PLANNED, DONE, ALL }

data class ProjectsUiState(val filter: ProjectFilter, val cards: List<ProjectCard>, val total: Int, val today: LocalDate)

class ProjectsViewModel(private val deps: ProjectsDependencies) : ViewModel() {
    private val filter = MutableStateFlow(ProjectFilter.ACTIVE)

    private val cards = combine(
        deps.projects.observeProjects(),
        deps.planning.observeTasks(),
        deps.projects.observeMilestones(),
        deps.links.observeAllLinks(),
        deps.ledger.observeAllTransactions(),
    ) { p, t, m, l, tx -> ProjectOverview.build(p, t, m, l, tx) }

    val state: StateFlow<ProjectsUiState?> = combine(cards, filter) { all, f ->
        val shown = when (f) {
            ProjectFilter.ACTIVE -> all.filter { it.project.status == ProjectStatus.ACTIVE || it.project.status == ProjectStatus.PAUSED }
            ProjectFilter.PLANNED -> all.filter { it.project.status == ProjectStatus.PLANNED }
            ProjectFilter.DONE -> all.filter { it.project.status == ProjectStatus.DONE || it.project.status == ProjectStatus.CANCELLED }
            ProjectFilter.ALL -> all
        }
        ProjectsUiState(f, shown, all.size, deps.clock.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFilter(f: ProjectFilter) {
        filter.value = f
    }
}

@Composable
fun ProjectsRoute(vm: ProjectsViewModel, openProject: (NodeId) -> Unit, newProject: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val inset = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)
    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 150.dp)) {
        item(key = "top") {
            LifeTopBar(
                overline = "Projekte",
                title = "Vorhaben",
                subtitle = if (s.total == 0) null else "${s.total} Projekte",
                large = true,
                actions = { LifeIconButton(Glyph.PLUS, "Neues Projekt", newProject, tint = ContinuumColors.Cyan) },
            )
        }
        item(key = "filter") {
            LifeSegmented(
                listOf(ProjectFilter.ACTIVE to "Aktiv", ProjectFilter.PLANNED to "Geplant", ProjectFilter.DONE to "Fertig", ProjectFilter.ALL to "Alle"),
                s.filter, vm::setFilter, modifier = inset.padding(bottom = 8.dp),
            )
        }
        if (s.cards.isEmpty()) {
            item(key = "empty") {
                SpineSection(if (s.total == 0) "Noch keine Projekte" else "Keine Projekte in dieser Ansicht", shape = NodeShape.LINKED) {
                    LifeEmpty(
                        if (s.total == 0) {
                            "Ein Projekt bündelt Aufgaben, Meilensteine, Notizen und Kosten. Ausgaben aus der Haushaltskasse ordnest du einfach zu – ohne sie doppelt zu buchen."
                        } else {
                            "Wechsle den Filter, um weitere Projekte zu sehen."
                        },
                        actionLabel = if (s.total == 0) "Erstes Projekt anlegen" else null,
                        onAction = if (s.total == 0) newProject else null,
                        glyph = Glyph.PROJECT,
                    )
                }
            }
        }
        items(s.cards, key = { it.project.id.value }) { card -> ProjectRow(card, s.today) { openProject(card.project.id) } }
    }
}

/** Ein Projekt an der LifeLine: Doppelring-Knoten, Fortschrittslinie, nächster Schritt, Kosten. */
@Composable
private fun ProjectRow(c: ProjectCard, today: LocalDate, onOpen: () -> Unit) {
    val p = c.project
    val done = p.status == ProjectStatus.DONE || p.status == ProjectStatus.CANCELLED
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Projekt öffnen", onClick = onOpen)) {
        SpineRow(
            modifier = Modifier.heightIn(min = 56.dp),
            node = {
                LifeNode(
                    shape = NodeShape.LINKED,
                    tone = when {
                        done -> NodeTone.DONE
                        p.status == ProjectStatus.PAUSED -> NodeTone.NEUTRAL
                        p.targetDate?.isBefore(today) == true -> NodeTone.ALERT
                        else -> NodeTone.ACTIVE
                    },
                    active = !done,
                    size = 16.dp,
                )
            },
        ) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name, style = Continuum.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (p.status != ProjectStatus.ACTIVE) LifeTag(ProjectsText.status(p.status), if (done) NodeTone.DONE else NodeTone.NEUTRAL)
                    if (p.isSample) LifeTag("Beispiel", NodeTone.WARN)
                }
                val pr = c.progress.permille
                if (pr != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LifeLine(progress = pr / 1000f, meaning = if (done) LineMeaning.DONE else LineMeaning.PROGRESS, modifier = Modifier.weight(1f), progressDescription = c.progress.basis)
                        Text(LifeFormat.percent(pr.toLong()), style = Continuum.type.figuresSmall, color = ContinuumColors.Muted)
                    }
                }
                Text(c.progress.basis, style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val facts = buildList {
                    p.category?.let { add(it) }
                    if (c.costs.netMinor != 0L || p.budgetMinor != null) {
                        add(MoneyText.format(c.costs.netMinor) + (p.budgetMinor?.let { " von " + MoneyText.format(it) } ?: ""))
                    }
                    p.targetDate?.let { t ->
                        val days = ChronoUnit.DAYS.between(today, t)
                        add(if (done) "Ziel ${LifeFormat.numericDate(t)}" else if (days >= 0) "Ziel in $days Tagen" else "Ziel seit ${-days} Tagen überschritten")
                    }
                }
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = Continuum.type.small, color = ContinuumColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val next = c.nextMilestone?.let { "Meilenstein: ${it.title}" } ?: c.nextTask?.let { "Als Nächstes: ${it.title}" }
                if (next != null && !done) Text(next, style = Continuum.type.body, color = ContinuumColors.Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
