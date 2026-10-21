package app.lifeos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifeos.AppContainer
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTimelineHeader
import app.lifeos.core.designsystem.component.LifeTimelineRow
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.domain.search.GlobalSearch
import app.lifeos.core.domain.search.SearchHit
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")

    private val sources = combine(
        c.linkRepository.observeNodes(emptySet()),
        c.ledgerRepository.observeAllTransactions(),
        c.planningRepository.observeTasks(),
        c.documentRepository.observeDocuments(),
        c.ledgerRepository.observeCategories(),
    ) { nodes, txs, tasks, docs, cats -> { q: String -> GlobalSearch.search(q, nodes, txs, tasks, docs, cats) } }

    @OptIn(FlowPreview::class)
    val hits: StateFlow<List<SearchHit>?> = combine(query.debounce(150), sources) { q, run -> run(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private fun shapeOf(t: NodeType) = when (t) {
    NodeType.TRANSACTION -> NodeShape.DIAMOND
    NodeType.TASK, NodeType.EVENT, NodeType.TOPIC -> NodeShape.RING
    NodeType.PROJECT -> NodeShape.LINKED
    NodeType.VEHICLE, NodeType.COURSE -> NodeShape.HEX
    else -> NodeShape.SQUARE
}

@Composable
fun SearchScreen(vm: SearchViewModel, onBack: () -> Unit, onOpen: (NodeId) -> Unit) {
    val q by vm.query.collectAsStateWithLifecycle()
    val hits by vm.hits.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize().imePadding()) {
        LifeTopBar(overline = "LifeOS", title = "Suche", onBack = onBack)
        LifeTextField(
            q, { vm.query.value = it.take(80) }, "",
            Modifier.padding(start = 48.dp, end = 20.dp).focusRequester(focus),
            placeholder = "Buchung, Aufgabe, Projekt, Fahrzeug, Dokument …", leading = Glyph.SEARCH,
        )
        val list = hits
        LazyColumn(Modifier.weight(1f).testTag("lifeos-screen"), contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)) {
            when {
                q.trim().length < 2 -> item { LifeEmpty("Mindestens zwei Zeichen eingeben. Gesucht wird in Titeln, Händlern, Notizen, Beträgen, Dateinamen und Aufgabenbeschreibungen.", Modifier.padding(start = 48.dp, end = 20.dp), glyph = Glyph.SEARCH) }
                list == null -> Unit
                list.isEmpty() -> item { LifeEmpty("Keine Treffer für „${q.trim()}“.", Modifier.padding(start = 48.dp, end = 20.dp)) }
                else -> list.groupBy { it.type }.toSortedMap(compareBy { it.ordinal }).forEach { (type, group) ->
                    item(key = "h-" + type.name) { LifeTimelineHeader(LifeFormat.typeLabel(type), trailing = group.size.toString()) }
                    items(group, key = { it.id.value }) { h ->
                        LifeTimelineRow(nodeShape = shapeOf(h.type), nodeTone = if (h.rank == 0) NodeTone.ACTIVE else NodeTone.NEUTRAL, onClick = { onOpen(h.id) }, onClickLabel = "${h.title} öffnen") {
                            Column(Modifier.weight(1f)) {
                                Text(h.title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                h.subtitle?.let { Text(it, style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
        }
    }
}
