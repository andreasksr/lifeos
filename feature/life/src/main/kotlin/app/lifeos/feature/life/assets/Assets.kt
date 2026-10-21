package app.lifeos.feature.life.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeDatePickerOverlay
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeLink
import app.lifeos.core.designsystem.component.LifeMetric
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeTag
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTimelineRow
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.MetricSize
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.domain.life.AssetDraft
import app.lifeos.core.domain.life.AssetLogic
import app.lifeos.core.domain.life.LifeError
import app.lifeos.core.domain.life.LifeResult
import app.lifeos.core.domain.life.WarrantyState
import app.lifeos.core.model.Asset
import app.lifeos.core.model.AssetKind
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.feature.life.LifeDependencies
import app.lifeos.feature.life.LifeText
import app.lifeos.feature.life.shared.CollectLifeMessages
import app.lifeos.feature.life.shared.DocumentPickerOverlay
import app.lifeos.feature.life.shared.DocumentRow
import app.lifeos.feature.life.shared.EditorScaffold
import app.lifeos.feature.life.shared.FieldGroup
import app.lifeos.feature.life.shared.LifeMessage
import app.lifeos.feature.life.shared.LinkedTransactionRow
import app.lifeos.feature.life.shared.LinksState
import app.lifeos.feature.life.shared.NodeLinksController
import app.lifeos.feature.life.shared.RelatedRow
import app.lifeos.feature.life.shared.SpineInset
import app.lifeos.feature.life.shared.TransactionPickerOverlay
import app.lifeos.feature.life.shared.rememberDocumentPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ───────────────────────────── ViewModels ─────────────────────────────

data class AssetsState(val assets: List<Asset>, val today: LocalDate)

class AssetsViewModel(deps: LifeDependencies) : ViewModel() {
    val state: StateFlow<AssetsState?> = deps.assets.observeAssets()
        .map { AssetsState(it, deps.clock.today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class AssetDetailState(val asset: Asset?, val missing: Boolean, val today: LocalDate, val links: LinksState)

class AssetDetailViewModel(private val deps: LifeDependencies, val assetId: NodeId) : ViewModel() {
    private val _events = Channel<LifeMessage>(Channel.BUFFERED)
    val events: Flow<LifeMessage> = _events.receiveAsFlow()
    val links = NodeLinksController(deps, assetId, viewModelScope) { _events.send(it) }
    val confirmDelete = MutableStateFlow(false)
    val deleted = MutableStateFlow(false)

    val state: StateFlow<AssetDetailState?> = combine(deps.assets.observeAssets(), links.state) { all, ls ->
        val a = all.firstOrNull { it.id == assetId }
        AssetDetailState(a, a == null, deps.clock.today(), ls)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun requestDelete() {
        confirmDelete.value = true
    }

    fun cancelDelete() {
        confirmDelete.value = false
    }

    fun confirmDeleteAsset() {
        viewModelScope.launch {
            try {
                deps.assets.deleteAsset(assetId)
                confirmDelete.value = false
                deleted.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(LifeMessage("Löschen fehlgeschlagen.", false))
            }
        }
    }
}

data class AssetForm(
    val name: String = "",
    val kind: AssetKind = AssetKind.TOOL,
    val manufacturer: String = "",
    val model: String = "",
    val serial: String = "",
    val purchaseDate: LocalDate? = null,
    val price: String = "",
    val warrantyUntil: LocalDate? = null,
    val location: String = "",
    val note: String = "",
)

data class AssetEditorState(
    val form: AssetForm = AssetForm(),
    val isEdit: Boolean,
    val loading: Boolean,
    val saving: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val savedId: NodeId? = null,
    val today: LocalDate,
)

class AssetEditorViewModel(private val deps: LifeDependencies, private val editId: NodeId?) : ViewModel() {
    private val _state = MutableStateFlow(AssetEditorState(isEdit = editId != null, loading = editId != null, today = deps.clock.today()))
    val state: StateFlow<AssetEditorState> = _state.asStateFlow()

    init {
        if (editId != null) {
            viewModelScope.launch {
                val a = deps.assets.asset(editId)
                _state.update {
                    it.copy(
                        loading = false,
                        form = a?.let { x ->
                            AssetForm(
                                x.name, x.kind, x.manufacturer.orEmpty(), x.model.orEmpty(), x.serialNumber.orEmpty(), x.purchaseDate,
                                x.priceMinor?.let(MoneyText::toEditText).orEmpty(), x.warrantyUntil, x.location.orEmpty(), x.note.orEmpty(),
                            )
                        } ?: it.form,
                        errors = if (a == null) mapOf("general" to LifeText.error(LifeError.NotFound)) else emptyMap(),
                    )
                }
            }
        }
    }

    fun update(change: (AssetForm) -> AssetForm) {
        _state.update { it.copy(form = change(it.form), errors = emptyMap()) }
    }

    /** Garantie schnell setzen: Kaufdatum + n Jahre. */
    fun warrantyYears(years: Long) {
        _state.update { s -> s.copy(form = s.form.copy(warrantyUntil = (s.form.purchaseDate ?: s.today).plusYears(years)), errors = emptyMap()) }
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        val f = s.form
        val price = if (f.price.isBlank()) null else when (val r = MoneyText.parse(f.price)) {
            is MoneyText.ParseResult.Success -> r.minor
            is MoneyText.ParseResult.Failure -> {
                _state.update { it.copy(errors = mapOf("price" to "Bitte einen gültigen Betrag eingeben.")) }
                return
            }
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                when (val r = deps.saveAsset(AssetDraft(editId, f.name, f.kind, f.manufacturer, f.model, f.serial, f.purchaseDate, price, f.warrantyUntil, f.location, f.note))) {
                    is LifeResult.Success -> _state.update { it.copy(saving = false, savedId = r.value) }
                    is LifeResult.Invalid -> _state.update {
                        it.copy(
                            saving = false,
                            errors = r.errors.associate { e ->
                                when (e) {
                                    LifeError.NameRequired, LifeError.NameTooLong -> "name"
                                    LifeError.InvalidAmount -> "price"
                                    LifeError.InvalidDate -> "warranty"
                                    else -> "general"
                                } to LifeText.error(e)
                            },
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, errors = mapOf("general" to "Speichern fehlgeschlagen. Es wurde nichts verändert.")) }
            }
        }
    }
}

// ───────────────────────────── Oberfläche ─────────────────────────────

private fun warrantyTone(w: WarrantyState) = when (w) {
    WarrantyState.NONE -> NodeTone.NEUTRAL
    WarrantyState.ACTIVE -> NodeTone.DONE
    WarrantyState.EXPIRING -> NodeTone.WARN
    WarrantyState.EXPIRED -> NodeTone.NEUTRAL
}

@Composable
fun AssetsRoute(vm: AssetsViewModel, onBack: () -> Unit, onOpen: (NodeId) -> Unit, onNew: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    var filter by rememberSaveable { mutableStateOf<AssetKind?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 150.dp)) {
        item { LifeTopBar(overline = "Mein Leben", title = "Inventar", subtitle = "Werkzeug, Maschinen, Elektronik", onBack = onBack, actions = { LifeIconButton(Glyph.PLUS, "Neuer Gegenstand", onNew, highlighted = true) }) }
        if (s.assets.isEmpty()) {
            item { LifeEmpty("Noch nichts erfasst. Lege wertvolle Werkzeuge und Geräte mit Seriennummer und Garantie an.", SpineInset, "Gegenstand anlegen", onNew, Glyph.BOX) }
        } else {
            item {
                Row(SpineInset.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeChoiceChip("Alle", filter == null, { filter = null })
                    AssetKind.entries.filter { k -> s.assets.any { it.kind == k } }.forEach { k -> LifeChoiceChip(AssetLogic.kindLabel(k), filter == k, { filter = if (filter == k) null else k }) }
                }
            }
        }
        items(s.assets.filter { filter == null || it.kind == filter }, key = { it.id.value }) { a ->
            val w = AssetLogic.warranty(a, s.today)
            LifeTimelineRow(nodeShape = NodeShape.SQUARE, nodeTone = warrantyTone(w), onClick = { onOpen(a.id) }, onClickLabel = "${a.name} öffnen") {
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(AssetLogic.kindLabel(a.kind), a.location, a.warrantyUntil?.let { u -> if (w == WarrantyState.EXPIRED) "Garantie abgelaufen" else "Garantie bis " + LifeFormat.numericDate(u) }).joinToString(" · "),
                        style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (w == WarrantyState.EXPIRING) ContinuumColors.Amber else ContinuumColors.Muted,
                    )
                }
                a.priceMinor?.let { Text(MoneyText.format(it), style = Continuum.type.figures) }
                if (a.isSample) LifeTag("Beispiel", NodeTone.WARN)
            }
        }
    }
}

data class AssetDetailNavigation(
    val back: () -> Unit,
    val edit: () -> Unit,
    val openTransaction: (NodeId) -> Unit,
    val openDocument: (NodeId) -> Unit,
    val openNode: (NodeId) -> Unit,
    val openMap: (() -> Unit)? = null,
)

@Composable
fun AssetDetailRoute(vm: AssetDetailViewModel, nav: AssetDetailNavigation) {
    val state by vm.state.collectAsStateWithLifecycle()
    val confirmDelete by vm.confirmDelete.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val query by vm.links.txQuery.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    var txPicker by rememberSaveable { mutableStateOf(false) }
    var docPicker by rememberSaveable { mutableStateOf(false) }
    val importDoc = rememberDocumentPicker { open, name, mime -> vm.links.importAndLink(open, name, mime) }
    CollectLifeMessages(vm.events)
    LaunchedEffect(deleted) {
        if (deleted) {
            signal.confirm("Gegenstand gelöscht – Buchungen und Dokumente bleiben erhalten")
            nav.back()
        }
    }
    val s = state ?: return
    val a = s.asset
    if (a == null) {
        Column(Modifier.fillMaxSize()) {
            LifeTopBar(title = "Inventar", onBack = nav.back)
            if (s.missing) LifeEmpty("Dieser Gegenstand existiert nicht mehr.", SpineInset)
        }
        return
    }
    val w = AssetLogic.warranty(a, s.today)
    val links = s.links
    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 150.dp)) {
        item {
            LifeTopBar(
                overline = "Inventar · " + AssetLogic.kindLabel(a.kind), title = a.name,
                subtitle = listOfNotNull(a.manufacturer, a.model).joinToString(" ").ifEmpty { null },
                onBack = nav.back,
                actions = {
                    nav.openMap?.let { LifeIconButton(Glyph.MAP, "In der LifeMap zeigen", it) }
                    LifeIconButton(Glyph.EDIT, "Bearbeiten", nav.edit)
                },
            )
        }
        item {
            SpineSection("Garantie", shape = NodeShape.SQUARE, tone = warrantyTone(w)) {
                val until = a.warrantyUntil
                if (until == null) {
                    Text(LifeText.warranty(w), style = Continuum.type.bodyMuted)
                } else {
                    val days = ChronoUnit.DAYS.between(s.today, until)
                    LifeMetric(
                        LifeText.warranty(w),
                        if (days >= 0) "$days Tage" else "seit ${-days} Tagen",
                        size = MetricSize.LARGE, tone = warrantyTone(w), caption = "bis " + LifeFormat.numericDate(until),
                    )
                }
            }
        }
        item {
            SpineSection("Angaben", shape = NodeShape.DOT) {
                Detail("Seriennummer", a.serialNumber)
                Detail("Kaufdatum", a.purchaseDate?.let(LifeFormat::numericDate))
                Detail("Kaufpreis", a.priceMinor?.let { MoneyText.format(it) })
                Detail("Standort", a.location)
                a.note?.let { Text(it, style = Continuum.type.body) }
            }
        }
        item {
            SpineSection("Kaufbeleg & Buchungen", shape = NodeShape.DIAMOND, fullBleed = true, trailing = { LifeLink("Buchung zuordnen", { txPicker = true }) }) {
                if (links.transactions.isEmpty()) LifeEmpty("Ordne den Kauf oder Reparaturen aus der Haushaltskasse zu – ohne Kopie.", SpineInset, "Buchung zuordnen", { txPicker = true })
            }
        }
        items(links.transactions, key = { "x-" + it.id.value }) { tx -> LinkedTransactionRow(tx, s.today, links.categoryLabel(tx), { nav.openTransaction(tx.id) }, { vm.links.unassign(tx) }) }
        item {
            SpineSection("Dokumente", shape = NodeShape.SQUARE, fullBleed = true, trailing = { LifeLink("Verknüpfen", { docPicker = true }) }) {
                if (links.documents.isEmpty()) LifeEmpty("Rechnung, Garantieschein, Bedienungsanleitung.", SpineInset, "Dokument hinzufügen", { docPicker = true })
            }
        }
        items(links.documents, key = { "d-" + it.id.value }) { d -> DocumentRow(d, { nav.openDocument(d.id) }, { vm.links.unlinkDocument(d) }) }
        if (links.other.isNotEmpty()) {
            item { SpineSection("Verbunden mit", shape = NodeShape.LINKED, fullBleed = true) {} }
            items(links.other, key = { "r-" + it.link.id.value }) { r -> RelatedRow(r) { nav.openNode(r.node.id) } }
        }
        item { Row(SpineInset.padding(top = 24.dp)) { LifeButton("Gegenstand löschen", vm::requestDelete, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH) } }
    }
    TransactionPickerOverlay(txPicker, a.name, links, query, vm.links::setQuery, s.today, onPick = { vm.links.assign(it); txPicker = false }, onDismiss = { txPicker = false })
    DocumentPickerOverlay(docPicker, a.name, links.docCandidates, onPick = { vm.links.linkDocument(it); docPicker = false }, onImportNew = { docPicker = false; importDoc() }, onDismiss = { docPicker = false })
    LifeOverlay(visible = confirmDelete, onDismiss = vm::cancelDelete, title = "Gegenstand löschen?", overline = "Sicherheitsabfrage") {
        Text("„${a.name}“ wird gelöscht. Zugeordnete Buchungen und Dokumente bleiben unverändert erhalten.", style = Continuum.type.bodyMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeButton("Abbrechen", vm::cancelDelete, style = LifeButtonStyle.SECONDARY)
            Spacer(Modifier.weight(1f))
            LifeButton("Löschen", vm::confirmDeleteAsset, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
        }
    }
}

@Composable
private fun Detail(label: String, value: String?) {
    if (value == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Continuum.type.small, modifier = Modifier.weight(1f))
        Text(value, style = Continuum.type.body)
    }
}

private enum class AssetDate { PURCHASE, WARRANTY }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetEditorRoute(vm: AssetEditorViewModel, onClose: () -> Unit, onSaved: (NodeId, Boolean) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    var pick by rememberSaveable { mutableStateOf<AssetDate?>(null) }
    LaunchedEffect(state.savedId) {
        state.savedId?.let {
            haptics.confirm()
            signal.confirm(if (state.isEdit) "Gespeichert" else "„${state.form.name.trim()}“ angelegt")
            onSaved(it, state.isEdit)
        }
    }
    LaunchedEffect(state.errors) { if (state.errors.isNotEmpty()) haptics.reject() }
    val f = state.form
    val e = state.errors
    EditorScaffold(if (state.isEdit) "Inventar bearbeiten" else "Neuer Gegenstand", f.name.ifBlank { "Gegenstand" }, onClose, state.saving, vm::save, e["general"]) {
        if (state.loading) return@EditorScaffold
        LifeTextField(f.name, { t -> vm.update { it.copy(name = t.take(120)) } }, "Bezeichnung", SpineInset, placeholder = "z. B. Akku-Bohrhammer", error = e["name"], textStyle = Continuum.type.title, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        FieldGroup("Art") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssetKind.entries.forEach { k -> LifeChoiceChip(AssetLogic.kindLabel(k), f.kind == k, { vm.update { it.copy(kind = k) } }) }
            }
        }
        Row(SpineInset, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeTextField(f.manufacturer, { t -> vm.update { it.copy(manufacturer = t.take(60)) } }, "Hersteller", Modifier.weight(1f), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            LifeTextField(f.model, { t -> vm.update { it.copy(model = t.take(60)) } }, "Modell", Modifier.weight(1f))
        }
        LifeTextField(f.serial, { t -> vm.update { it.copy(serial = t.take(60)) } }, "Seriennummer", SpineInset, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
        FieldGroup("Kauf", e["price"]) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LifeChoiceChip(f.purchaseDate?.let { "Gekauft " + LifeFormat.numericDate(it) } ?: "Kaufdatum …", f.purchaseDate != null, { pick = AssetDate.PURCHASE }, glyph = Glyph.CALENDAR)
                if (f.purchaseDate != null) LifeIconButton(Glyph.CLOSE, "Kaufdatum entfernen", { vm.update { it.copy(purchaseDate = null) } })
            }
            LifeTextField(f.price, { t -> vm.update { it.copy(price = t.take(16)) } }, "Kaufpreis in €", placeholder = "0,00", textStyle = Continuum.type.figuresMedium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        FieldGroup("Garantie", e["warranty"]) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeChoiceChip(f.warrantyUntil?.let { "Bis " + LifeFormat.numericDate(it) } ?: "Garantieende …", f.warrantyUntil != null, { pick = AssetDate.WARRANTY }, glyph = Glyph.CALENDAR)
                LifeChoiceChip("+2 Jahre", false, { vm.warrantyYears(2) })
                LifeChoiceChip("+3 Jahre", false, { vm.warrantyYears(3) })
                if (f.warrantyUntil != null) LifeIconButton(Glyph.CLOSE, "Garantie entfernen", { vm.update { it.copy(warrantyUntil = null) } })
            }
            Text("„+2 Jahre“ rechnet ab Kaufdatum (oder heute, falls keins erfasst ist).", style = Continuum.type.small)
        }
        LifeTextField(f.location, { t -> vm.update { it.copy(location = t.take(60)) } }, "Standort", SpineInset, placeholder = "z. B. Werkstatt, Regal 2")
        LifeTextField(f.note, { t -> vm.update { it.copy(note = t.take(4000)) } }, "Notizen", SpineInset, singleLine = false)
    }
    LifeDatePickerOverlay(pick == AssetDate.PURCHASE, f.purchaseDate ?: state.today, state.today, { pick = null }, { d -> vm.update { it.copy(purchaseDate = d) } }, "Kaufdatum")
    LifeDatePickerOverlay(pick == AssetDate.WARRANTY, f.warrantyUntil ?: (f.purchaseDate ?: state.today).plusYears(2), state.today, { pick = null }, { d -> vm.update { it.copy(warrantyUntil = d) } }, "Garantieende")
}

