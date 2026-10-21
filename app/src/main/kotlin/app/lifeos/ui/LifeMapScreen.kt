package app.lifeos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifeos.AppContainer
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTimelineHeader
import app.lifeos.core.designsystem.component.LifeTimelineRow
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.domain.map.LifeMapBuilder
import app.lifeos.core.domain.map.LifeMapView
import app.lifeos.core.domain.map.MapNeighbor
import app.lifeos.core.domain.search.GlobalSearch
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeSummary
import app.lifeos.core.model.NodeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class LifeMapState(val view: LifeMapView?, val centerMissing: Boolean, val candidates: List<NodeSummary>, val canGoBack: Boolean)

class LifeMapViewModel(c: AppContainer, initial: NodeId?) : ViewModel() {
    /** Verlauf der gewählten Mittelpunkte – „Zurück" springt zum vorherigen Objekt. */
    private val history = MutableStateFlow(listOfNotNull(initial))
    val query = MutableStateFlow("")

    private data class Graph(val nodes: List<NodeSummary>, val links: List<app.lifeos.core.model.NodeLink>, val tasks: List<app.lifeos.core.model.Task>, val milestones: List<app.lifeos.core.model.Milestone>, val events: List<app.lifeos.core.model.VehicleEvent>)

    private val graph = combine(
        c.linkRepository.observeNodes(emptySet()), c.linkRepository.observeAllLinks(), c.planningRepository.observeTasks(),
        c.projectRepository.observeMilestones(), c.vehicleRepository.observeEvents(), ::Graph,
    )

    val state: StateFlow<LifeMapState?> = combine(graph, c.learningRepository.observeTopics(), history, query) { g, topics, h, q ->
        val center = h.lastOrNull()
        val needle = q.trim().lowercase()
        LifeMapState(
            view = center?.let { LifeMapBuilder.build(it, g.nodes, g.links, g.tasks, g.milestones, g.events, topics) },
            centerMissing = center != null && g.nodes.none { it.id == center },
            candidates = g.nodes.filter { it.type in GlobalSearch.SEARCHABLE && it.type != NodeType.TRANSACTION && (needle.isEmpty() || it.title.lowercase().contains(needle)) }
                .sortedWith(compareBy({ it.type.ordinal }, { it.title.lowercase() })).take(80),
            canGoBack = h.size > 1,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun focus(id: NodeId) {
        history.update { (it + id).takeLast(30) }
    }

    fun back(): Boolean {
        if (history.value.size <= 1) return false
        history.update { it.dropLast(1) }
        return true
    }

    fun clear() {
        history.value = emptyList()
    }
}

private fun shape(t: NodeType) = when (t) {
    NodeType.TRANSACTION -> NodeShape.DIAMOND
    NodeType.TASK, NodeType.EVENT, NodeType.TOPIC -> NodeShape.RING
    NodeType.PROJECT -> NodeShape.LINKED
    NodeType.VEHICLE, NodeType.COURSE, NodeType.ASSET -> NodeShape.HEX
    else -> NodeShape.SQUARE
}

private fun tone(t: NodeType) = when (t) {
    NodeType.TRANSACTION -> NodeTone.OUTFLOW
    NodeType.PROJECT, NodeType.VEHICLE -> NodeTone.ACTIVE
    NodeType.MILESTONE, NodeType.EXAM -> NodeTone.WARN
    NodeType.DOCUMENT -> NodeTone.DONE
    else -> NodeTone.NEUTRAL
}

private const val RING_MAX = 10

@Composable
fun LifeMapScreen(vm: LifeMapViewModel, onBack: () -> Unit, onOpen: (NodeId) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val s = state ?: return
    BackHandler(enabled = s.canGoBack) { vm.back() }
    val view = s.view

    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            LifeTopBar(
                overline = "LifeMap", title = view?.center?.title ?: "Objekt wählen",
                subtitle = view?.let { LifeFormat.typeLabel(it.center.type) + " · " + if (it.neighbors.size == 1) "1 Verbindung" else "${it.neighbors.size} Verbindungen" },
                onBack = { if (!vm.back()) onBack() },
                actions = {
                    if (view != null) {
                        LifeIconButton(Glyph.SEARCH, "Anderes Objekt wählen", vm::clear)
                        LifeIconButton(Glyph.ARROW_OUT, "${view.center.title} öffnen", { onOpen(view.center.id) }, highlighted = true)
                    }
                },
            )
        }
        if (view == null) {
            item {
                Column(Modifier.padding(start = 48.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Die LifeMap zeigt immer nur ein Objekt und seine direkten Verbindungen – Buchungen, Aufgaben, Dokumente, Fahrzeuge, Projekte. Tippe eine Verbindung an, um weiterzuwandern.", style = Continuum.type.small)
                    if (s.centerMissing) Text("Das gewählte Objekt existiert nicht mehr.", style = Continuum.type.small, color = ContinuumColors.Amber)
                    LifeTextField(query, { vm.query.value = it.take(60) }, "", placeholder = "Projekt, Fahrzeug, Dokument …", leading = Glyph.SEARCH)
                }
            }
            if (s.candidates.isEmpty()) item { LifeEmpty("Keine passenden Einträge.", Modifier.padding(start = 48.dp, end = 20.dp)) }
            items(s.candidates, key = { it.id.value }) { n ->
                LifeTimelineRow(nodeShape = shape(n.type), nodeTone = tone(n.type), onClick = { vm.focus(n.id) }, onClickLabel = "${n.title} in der LifeMap zeigen") {
                    Text(n.title, style = Continuum.type.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(LifeFormat.typeLabel(n.type), style = Continuum.type.small)
                }
            }
            return@LazyColumn
        }

        item(key = "map-" + view.center.id.value) { MapCanvas(view, onFocus = vm::focus, onOpenCenter = { onOpen(view.center.id) }) }
        if (view.neighbors.isEmpty()) {
            item { LifeEmpty("Noch keine Verbindungen. Ordne z. B. eine Buchung, ein Dokument oder eine Aufgabe zu – sie erscheinen dann hier.", Modifier.padding(start = 48.dp, end = 20.dp)) }
        } else {
            item { LifeTimelineHeader("Verbindungen", trailing = view.counts.entries.joinToString(" · ") { "${it.value} ${LifeFormat.typeLabel(it.key)}" }) }
            items(view.neighbors, key = { "n-" + it.node.id.value }) { nb -> NeighborRow(nb, onFocus = { vm.focus(nb.node.id) }, onOpen = { onOpen(nb.node.id) }) }
        }
    }
}

@Composable
private fun NeighborRow(nb: MapNeighbor, onFocus: () -> Unit, onOpen: () -> Unit) {
    LifeTimelineRow(nodeShape = shape(nb.node.type), nodeTone = tone(nb.node.type), onClick = onFocus, onClickLabel = "${nb.node.title} in die Mitte holen") {
        Column(Modifier.weight(1f)) {
            Text(nb.node.title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(LifeFormat.typeLabel(nb.node.type) + " · " + nb.meaning, style = Continuum.type.small, maxLines = 1)
        }
        LifeIconButton(Glyph.ARROW_OUT, "${nb.node.title} öffnen", onOpen, tint = ContinuumColors.Faint)
    }
}

/** Radiale Ansicht: Mittelpunkt, Lichtlinien zu bis zu 10 Nachbarn. Linien wachsen beim Wechsel aus der Mitte. */
@Composable
private fun MapCanvas(view: LifeMapView, onFocus: (NodeId) -> Unit, onOpenCenter: () -> Unit) {
    val motion = Continuum.motion
    val grow = remember(view.center.id) { Animatable(if (motion.enabled) 0f else 1f) }
    LaunchedEffect(view.center.id) { if (motion.enabled) grow.animateTo(1f, tween(motion.emphasisMillis, easing = FastOutSlowInEasing)) }
    val ring = view.neighbors.take(RING_MAX)
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 12.dp)
            .semantics { contentDescription = "LifeMap von ${view.center.title} mit ${view.neighbors.size} Verbindungen" },
    ) {
        val sizePx = with(density) { min(maxWidth.toPx(), maxHeight.toPx()) }
        val c = Offset(sizePx / 2f, sizePx / 2f)
        val radius = sizePx * 0.36f
        val points = ring.indices.map { i ->
            val a = -Math.PI / 2 + 2 * Math.PI * i / ring.size.coerceAtLeast(1)
            Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
        }
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(ContinuumColors.Hairline, radius = radius, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            points.forEach { p ->
                val end = Offset(c.x + (p.x - c.x) * grow.value, c.y + (p.y - c.y) * grow.value)
                drawLine(Brush.linearGradient(listOf(ContinuumColors.Cyan, ContinuumColors.Violet), c, p), c, end, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        // Mittelpunkt
        val centerW = with(density) { 140.dp.roundToPx() }
        Column(
            Modifier.offset { IntOffset((c.x - centerW / 2).toInt(), (c.y - with(density) { 22.dp.toPx() }).toInt()) }
                .width(140.dp).clickable(role = Role.Button, onClickLabel = "Öffnen", onClick = onOpenCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LifeNode(shape = shape(view.center.type), tone = NodeTone.ACTIVE, active = true, size = 22.dp)
            Text(view.center.title, style = Continuum.type.body, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // Nachbarn
        val labelW = with(density) { 96.dp.roundToPx() }
        ring.forEachIndexed { i, nb ->
            val p = points[i]
            Column(
                Modifier.offset { IntOffset((p.x - labelW / 2).toInt(), (p.y - with(density) { 12.dp.toPx() }).toInt()) }
                    .width(96.dp).heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "${nb.node.title} in die Mitte holen") { onFocus(nb.node.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LifeNode(shape = shape(nb.node.type), tone = tone(nb.node.type), size = 14.dp)
                Text(nb.node.title, style = Continuum.type.small.copy(color = ContinuumColors.Frost), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (view.neighbors.size > RING_MAX) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                Text("+ ${view.neighbors.size - RING_MAX} weitere in der Liste", style = Continuum.type.small)
            }
        }
    }
}
