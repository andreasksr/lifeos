package app.lifeos.feature.life.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeGlyph
import app.lifeos.core.designsystem.component.LifeLink
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeTimelineRow
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.domain.home.AttentionItem
import app.lifeos.core.domain.life.AssetLogic
import app.lifeos.core.domain.life.ExamCountdown
import app.lifeos.core.domain.life.ExamLogic
import app.lifeos.core.domain.life.StudyStatistics
import app.lifeos.core.domain.life.VehicleSnapshotBuilder
import app.lifeos.core.domain.life.WarrantyState
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.StudySession
import app.lifeos.feature.life.LifeDependencies
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LifeHubState(
    val vehicles: Int,
    val vehicleNote: String?,
    val assets: Int,
    val assetNote: String?,
    val documents: Int,
    val weekMinutes: Long,
    val running: StudySession?,
    val nextExam: ExamCountdown?,
)

class LifeHubViewModel(deps: LifeDependencies) : ViewModel() {
    private val learning = combine(deps.learning.observeSessions(), deps.learning.observeExams()) { s, e -> s to e }

    val state: StateFlow<LifeHubState?> = combine(
        deps.vehicles.observeVehicles(), deps.vehicles.observeEvents(), deps.assets.observeAssets(), deps.documents.observeDocuments(), learning,
    ) { vs, es, assets, docs, (sessions, exams) ->
        val today = deps.clock.today()
        val due = VehicleSnapshotBuilder.attention(vs, es, today).minByOrNull { it.rank }
        val expiring = assets.count { AssetLogic.warranty(it, today) == WarrantyState.EXPIRING }
        LifeHubState(
            vehicles = vs.size,
            vehicleNote = due?.let { "${it.title}: ${it.detail}" },
            assets = assets.size,
            assetNote = if (expiring > 0) "$expiring × Garantie läuft bald ab" else null,
            documents = docs.size,
            weekMinutes = StudyStatistics.compute(sessions, deps.clock.now(), deps.zone()).weekMinutes,
            running = sessions.firstOrNull { it.running },
            nextExam = exams.map { ExamLogic.countdown(it, today) }.filter { (it.days ?: -1) >= 0 }.minByOrNull { it.days ?: Long.MAX_VALUE },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class LifeHubNavigation(
    val vehicles: () -> Unit,
    val assets: () -> Unit,
    val documents: () -> Unit,
    val learning: () -> Unit,
    val system: () -> Unit,
    val map: (() -> Unit)? = null,
    val search: (() -> Unit)? = null,
)

@Composable
fun LifeHubRoute(vm: LifeHubViewModel, nav: LifeHubNavigation) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 150.dp)) {
        item {
            LifeTopBar(
                overline = "Mein Leben", title = "Mein Leben", subtitle = "Besitz, Unterlagen, Weiterbildung", large = true,
                actions = { nav.search?.let { app.lifeos.core.designsystem.component.LifeIconButton(Glyph.SEARCH, "Suchen", it) } },
            )
        }
        item { SpineSection("Besitz & Unterlagen", index = 0, shape = NodeShape.HEX, tone = NodeTone.ACTIVE, fullBleed = true) {} }
        item { Module(Glyph.CAR, "Fahrzeuge", countText(s.vehicles, "Fahrzeug", "Fahrzeuge"), s.vehicleNote, if (s.vehicleNote != null) NodeTone.WARN else NodeTone.NEUTRAL, nav.vehicles) }
        item { Module(Glyph.BOX, "Inventar", countText(s.assets, "Gegenstand", "Gegenstände"), s.assetNote, if (s.assetNote != null) NodeTone.WARN else NodeTone.NEUTRAL, nav.assets) }
        item { Module(Glyph.DOCUMENT, "Dokumente", countText(s.documents, "Dokument", "Dokumente") + " · verschlüsselt", null, NodeTone.NEUTRAL, nav.documents) }
        item { SpineSection("Weiterbildung", index = 1, shape = NodeShape.RING, tone = if (s.running != null) NodeTone.ACTIVE else NodeTone.NEUTRAL, fullBleed = true) {} }
        item {
            Module(
                Glyph.BOOK, "Industriemeister",
                if (s.running != null) "Lernzeit läuft" else "Diese Woche " + LifeFormat.duration(s.weekMinutes),
                s.nextExam?.let { it.exam.title + " · " + it.label },
                if (s.running != null) NodeTone.ACTIVE else NodeTone.NEUTRAL, nav.learning,
            )
        }
        item { SpineSection("Übersicht & System", index = 2, shape = NodeShape.LINKED, fullBleed = true) {} }
        nav.map?.let { m -> item { Module(Glyph.MAP, "LifeMap", "Verbindungen zwischen deinen Einträgen", null, NodeTone.NEUTRAL, m) } }
        item { Module(Glyph.SHIELD, "System & Sicherheit", "Sicherung, App-Sperre, Bewegung, Datenintegrität", null, NodeTone.NEUTRAL, nav.system) }
    }
}

private fun countText(n: Int, one: String, many: String) = when (n) {
    0 -> "Noch keine $many"
    1 -> "1 $one"
    else -> "$n $many"
}

@Composable
private fun Module(glyph: Glyph, title: String, subtitle: String, note: String?, tone: NodeTone, open: () -> Unit) {
    LifeTimelineRow(nodeShape = NodeShape.HEX, nodeTone = tone, onClick = open, onClickLabel = "$title öffnen") {
        LifeGlyph(glyph, tint = ContinuumColors.Cyan)
        Column(Modifier.weight(1f)) {
            Text(title, style = Continuum.type.body)
            Text(subtitle, style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            note?.let { Text(it, style = Continuum.type.small, color = ContinuumColors.Amber, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        LifeGlyph(Glyph.CHEVRON_RIGHT, tint = ContinuumColors.Faint, size = 16.dp)
    }
}

// ───────────────────────────── Home ─────────────────────────────

/** Hinweise aus „Mein Leben" für Home: fällige Fahrzeugtermine, auslaufende Garantien, bestätigte Prüfungen. */
fun lifeAttention(deps: LifeDependencies): Flow<List<AttentionItem>> = combine(
    deps.vehicles.observeVehicles(), deps.vehicles.observeEvents(), deps.assets.observeAssets(), deps.learning.observeExams(),
) { vs, es, assets, exams ->
    val today = deps.clock.today()
    VehicleSnapshotBuilder.attention(vs, es, today) + AssetLogic.attention(assets, today) + ExamLogic.attention(exams, today)
}

/** Weiterbildung auf Home: laufende Lernzeit, Wochensumme, nächste Prüfung. */
@Composable
fun LearningHomeSection(vm: LifeHubViewModel, index: Int, open: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    SpineSection(
        "Weiterbildung", index = index, shape = NodeShape.RING, tone = if (s.running != null) NodeTone.ACTIVE else NodeTone.NEUTRAL,
        onOpen = open, openLabel = "Weiterbildung öffnen", trailing = { LifeLink("Öffnen", open) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LifeNode(shape = NodeShape.RING, tone = if (s.running != null) NodeTone.ACTIVE else NodeTone.NEUTRAL, active = s.running != null, size = 12.dp)
            Text(if (s.running != null) "Lernzeit läuft gerade" else "Diese Woche gelernt: " + LifeFormat.duration(s.weekMinutes), style = Continuum.type.body, modifier = Modifier.weight(1f))
        }
        val e = s.nextExam
        if (e != null) {
            Text(e.exam.title + " · " + e.label + (e.days?.let { " · noch $it Tage" + if (e.confirmed) "" else " (ca.)" } ?: ""), style = Continuum.type.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else if (s.weekMinutes == 0L && s.running == null) {
            LifeEmpty("Starte die Lernzeit mit einem Tipp.", actionLabel = "Zur Weiterbildung", onAction = open)
        }
    }
}
