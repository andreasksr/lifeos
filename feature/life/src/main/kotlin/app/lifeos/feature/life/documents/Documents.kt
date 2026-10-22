package app.lifeos.feature.life.documents

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.domain.life.DocumentTooLargeException
import app.lifeos.core.domain.life.LifeResult
import app.lifeos.core.domain.ports.LinkedNode
import app.lifeos.core.model.Document
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeSummary
import app.lifeos.core.model.NodeType
import app.lifeos.feature.life.LifeDependencies
import app.lifeos.feature.life.LifeText
import app.lifeos.feature.life.shared.CollectLifeMessages
import app.lifeos.feature.life.shared.DocumentRow
import app.lifeos.feature.life.shared.LifeMessage
import app.lifeos.feature.life.shared.RelatedRow
import app.lifeos.feature.life.shared.SpineInset
import app.lifeos.feature.life.shared.rememberDocumentPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.LocalDate

// ───────────────────────────── ViewModels ─────────────────────────────

data class DocumentsState(val documents: List<Document>, val query: String, val linkCounts: Map<NodeId, Int>)

class DocumentsViewModel(private val deps: LifeDependencies) : ViewModel() {
    private val _events = Channel<LifeMessage>(Channel.BUFFERED)
    val events: Flow<LifeMessage> = _events.receiveAsFlow()
    private val query = MutableStateFlow("")
    val openAfterImport = MutableStateFlow<NodeId?>(null)

    val state: StateFlow<DocumentsState?> = combine(deps.documents.observeDocuments(), deps.links.observeAllLinks(), query) { docs, links, q ->
        val needle = q.trim().lowercase()
        val counts = HashMap<NodeId, Int>()
        val ids = docs.map { it.id }.toSet()
        links.forEach { l ->
            if (l.from in ids) counts[l.from] = (counts[l.from] ?: 0) + 1
            if (l.to in ids) counts[l.to] = (counts[l.to] ?: 0) + 1
        }
        DocumentsState(
            docs.filter { d -> needle.isEmpty() || listOfNotNull(d.title, d.fileName, d.note).any { it.lowercase().contains(needle) } },
            q, counts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(q: String) {
        query.value = q.take(60)
    }

    fun import(open: () -> InputStream, fileName: String, mimeType: String) {
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { open().use { deps.importDocument(it, fileName, mimeType) } }
                _events.send(LifeMessage(if (r.duplicate) "Diese Datei ist bereits im Archiv – nicht doppelt gespeichert" else "Dokument verschlüsselt gespeichert", true))
                openAfterImport.value = r.documentId
            } catch (e: CancellationException) {
                throw e
            } catch (e: DocumentTooLargeException) {
                _events.send(LifeMessage("Die Datei ist zu groß (max. ${e.limitBytes / (1024 * 1024)} MB).", false))
            } catch (e: Exception) {
                _events.send(LifeMessage("Die Datei konnte nicht gelesen werden.", false))
            }
        }
    }

    fun consumedOpen() {
        openAfterImport.value = null
    }
}

data class DocumentDetailState(val document: Document?, val missing: Boolean, val related: List<LinkedNode>, val targets: List<NodeSummary>, val today: LocalDate)

data class DocumentMetaDraft(val title: String, val date: LocalDate?, val note: String, val error: String? = null)

class DocumentDetailViewModel(private val deps: LifeDependencies, val documentId: NodeId) : ViewModel() {
    private val _events = Channel<LifeMessage>(Channel.BUFFERED)
    val events: Flow<LifeMessage> = _events.receiveAsFlow()
    val meta = MutableStateFlow<DocumentMetaDraft?>(null)
    val confirmDelete = MutableStateFlow(false)
    val deleted = MutableStateFlow(false)

    val state: StateFlow<DocumentDetailState?> = combine(
        deps.documents.observeDocuments(),
        deps.links.observeNeighbors(documentId),
        deps.links.observeNodes(setOf(NodeType.PROJECT, NodeType.VEHICLE, NodeType.ASSET, NodeType.COURSE, NodeType.EXAM)),
    ) { docs, neighbors, nodes ->
        val d = docs.firstOrNull { it.id == documentId }
        val linked = neighbors.map { it.node.id }.toSet()
        DocumentDetailState(d, d == null, neighbors, nodes.filter { it.id !in linked }, deps.clock.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(LifeMessage("Das hat nicht geklappt. Es wurde nichts verändert.", false))
            }
        }
    }

    suspend fun bytes(doc: Document): ByteArray = deps.documentAccess.read(doc)

    fun openExternally(doc: Document): Unit = run {
        if (!deps.documentAccess.openExternally(doc)) _events.send(LifeMessage("Keine App zum Öffnen dieses Dateityps gefunden.", false))
    }

    fun link(target: NodeSummary): Unit = run {
        deps.linkNodes(documentId, target.id, LinkRelation.DOCUMENTS)
        _events.send(LifeMessage("Mit „${target.title}“ verknüpft", true, "Rückgängig") { unlink(target.id, announce = false) })
    }

    fun unlink(target: NodeId, announce: Boolean = true): Unit = run {
        deps.unlink(documentId, target)
        if (announce) _events.send(LifeMessage("Verknüpfung entfernt", true))
    }

    fun editMeta(d: Document) {
        meta.value = DocumentMetaDraft(d.title, d.docDate, d.note.orEmpty())
    }

    fun updateMeta(change: (DocumentMetaDraft) -> DocumentMetaDraft) {
        meta.value = meta.value?.let { change(it).copy(error = null) }
    }

    fun closeMeta() {
        meta.value = null
    }

    fun saveMeta() {
        val m = meta.value ?: return
        val title = m.title.trim()
        if (title.isEmpty()) {
            meta.value = m.copy(error = "Bitte einen Titel eingeben.")
            return
        }
        run {
            val doc = deps.documents.document(documentId) ?: return@run
            deps.documents.saveDocument(doc.copy(title = title.take(120), docDate = m.date, note = m.note.trim().ifEmpty { null }), deps.clock.now())
            meta.value = null
            _events.send(LifeMessage("Gespeichert", true))
        }
    }

    fun requestDelete() {
        confirmDelete.value = true
    }

    fun cancelDelete() {
        confirmDelete.value = false
    }

    fun confirmDeleteDocument(): Unit = run {
        when (deps.deleteDocument(documentId)) {
            is LifeResult.Success -> {
                confirmDelete.value = false
                deleted.value = true
            }
            is LifeResult.Invalid -> _events.send(LifeMessage("Dokument nicht gefunden.", false))
        }
    }
}

// ───────────────────────────── Oberfläche ─────────────────────────────

@Composable
fun DocumentsRoute(vm: DocumentsViewModel, onBack: () -> Unit, onOpen: (NodeId) -> Unit, autoPick: Boolean = false) {
    val state by vm.state.collectAsStateWithLifecycle()
    val openAfter by vm.openAfterImport.collectAsStateWithLifecycle()
    val pick = rememberDocumentPicker { open, name, mime -> vm.import(open, name, mime) }
    // Schnellerfassung „Dokument": Dateiauswahl genau einmal automatisch öffnen
    var autoPicked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoPick) {
        if (autoPick && !autoPicked) {
            autoPicked = true
            pick()
        }
    }
    CollectLifeMessages(vm.events)
    LaunchedEffect(openAfter) {
        openAfter?.let {
            vm.consumedOpen()
            onOpen(it)
        }
    }
    val s = state ?: return
    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 150.dp)) {
        item {
            LifeTopBar(
                overline = "Mein Leben", title = "Dokumente", subtitle = "Verschlüsselt auf diesem Gerät", onBack = onBack,
                actions = { LifeIconButton(Glyph.PLUS, "Dokument hinzufügen", pick, highlighted = true) },
            )
        }
        item { LifeTextField(s.query, vm::setQuery, "", SpineInset.padding(bottom = 8.dp), placeholder = "Titel, Dateiname, Notiz", leading = Glyph.SEARCH) }
        if (s.documents.isEmpty()) {
            item {
                LifeEmpty(
                    if (s.query.isBlank()) "Noch keine Dokumente. Füge Rechnungen, Garantiescheine oder Fahrzeugpapiere als PDF oder Bild hinzu – sie werden verschlüsselt gespeichert und lassen sich Projekten, Fahrzeugen und Inventar zuordnen."
                    else "Keine Treffer.",
                    SpineInset, if (s.query.isBlank()) "Datei wählen" else null, if (s.query.isBlank()) pick else null, Glyph.DOCUMENT,
                )
            }
        }
        items(s.documents, key = { it.id.value }) { d ->
            DocumentRow(d, onOpen = { onOpen(d.id) })
        }
        if (s.documents.isNotEmpty()) {
            item { Text("Texterkennung (OCR) ist noch nicht eingebaut – gesucht wird in Titel, Dateiname und Notiz.", style = Continuum.type.small, modifier = SpineInset.padding(top = 16.dp)) }
        }
    }
}

data class DocumentDetailNavigation(val back: () -> Unit, val openNode: (NodeId) -> Unit)

@Composable
fun DocumentDetailRoute(vm: DocumentDetailViewModel, nav: DocumentDetailNavigation) {
    val state by vm.state.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val confirmDelete by vm.confirmDelete.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    var linkPicker by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    CollectLifeMessages(vm.events)
    LaunchedEffect(deleted) {
        if (deleted) {
            signal.confirm("Dokument gelöscht")
            nav.back()
        }
    }
    val s = state ?: return
    val d = s.document
    if (d == null) {
        Column(Modifier.fillMaxSize()) {
            LifeTopBar(title = "Dokument", onBack = nav.back)
            if (s.missing) LifeEmpty("Dieses Dokument existiert nicht mehr.", SpineInset)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 150.dp)) {
        item {
            LifeTopBar(
                overline = "Dokument · " + (if (d.isPdf) "PDF" else if (d.isImage) "Bild" else "Datei") + " · " + LifeText.fileSize(d.sizeBytes),
                title = d.title, subtitle = d.docDate?.let { "vom " + LifeFormat.numericDate(it) },
                onBack = nav.back,
                actions = {
                    LifeIconButton(Glyph.ARROW_OUT, "In anderer App öffnen", { vm.openExternally(d) })
                    LifeIconButton(Glyph.EDIT, "Angaben bearbeiten", { vm.editMeta(d) })
                },
            )
        }
        item { Preview(d, vm) }
        item {
            SpineSection("Zugeordnet zu", shape = NodeShape.LINKED, fullBleed = true, trailing = { LifeLink("Zuordnen", { linkPicker = true }) }) {
                if (s.related.isEmpty()) LifeEmpty("Ordne das Dokument einem Projekt, Fahrzeug oder Gegenstand zu – es bleibt eine Datei, nur verknüpft.", SpineInset, "Zuordnen", { linkPicker = true })
            }
        }
        items(s.related, key = { it.link.id.value }) { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { RelatedRow(r) { nav.openNode(r.node.id) } }
                LifeIconButton(Glyph.CLOSE, "Verknüpfung mit ${r.node.title} entfernen", { vm.unlink(r.node.id) }, tint = ContinuumColors.Faint)
            }
        }
        item {
            SpineSection("Datei", shape = NodeShape.SQUARE) {
                Text(d.fileName, style = Continuum.type.body)
                Text("Importiert " + LifeFormat.numericDate(d.importedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate()) + " · verschlüsselt (AES-256) gespeichert", style = Continuum.type.small)
                d.note?.let { Text(it, style = Continuum.type.body) }
                Text("Texterkennung (OCR): noch nicht verfügbar.", style = Continuum.type.small)
            }
        }
        item { Row(SpineInset.padding(top = 24.dp)) { LifeButton("Dokument löschen", vm::requestDelete, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH) } }
    }

    LifeOverlay(visible = linkPicker, onDismiss = { linkPicker = false }, title = "Zuordnen", overline = d.title, fullHeight = true) {
        LazyColumn(Modifier.weight(1f)) {
            if (s.targets.isEmpty()) item { LifeEmpty("Keine Projekte, Fahrzeuge, Gegenstände oder Fächer zum Zuordnen vorhanden.") }
            items(s.targets, key = { it.id.value }) { t ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Zuordnen") { vm.link(t); linkPicker = false },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LifeNode(shape = NodeShape.LINKED, tone = NodeTone.ACTIVE, size = 10.dp)
                    Text(t.title, style = Continuum.type.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(LifeFormat.typeLabel(t.type), style = Continuum.type.small)
                }
            }
        }
    }

    val m = meta
    LifeOverlay(visible = m != null, onDismiss = vm::closeMeta, title = "Angaben", overline = d.fileName) {
        if (m == null) return@LifeOverlay
        LifeTextField(m.title, { t -> vm.updateMeta { it.copy(title = t.take(120)) } }, "Titel", error = m.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LifeChoiceChip(m.date?.let { "Datum " + LifeFormat.numericDate(it) } ?: "Belegdatum …", m.date != null, { pickDate = true }, glyph = Glyph.CALENDAR)
            if (m.date != null) LifeIconButton(Glyph.CLOSE, "Datum entfernen", { vm.updateMeta { it.copy(date = null) } })
        }
        LifeTextField(m.note, { t -> vm.updateMeta { it.copy(note = t.take(2000)) } }, "Notiz", singleLine = false, placeholder = "Stichworte für die Suche")
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            LifeButton("Speichern", vm::saveMeta, glyph = Glyph.CHECK)
        }
        LifeDatePickerOverlay(pickDate, m.date ?: s.today, s.today, { pickDate = false }, { date -> vm.updateMeta { it.copy(date = date) } }, "Belegdatum")
    }

    LifeOverlay(visible = confirmDelete, onDismiss = vm::cancelDelete, title = "Dokument löschen?", overline = "Sicherheitsabfrage") {
        Text("„${d.title}“ wird mit allen Zuordnungen gelöscht. Die verknüpften Objekte selbst bleiben erhalten.", style = Continuum.type.bodyMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeButton("Abbrechen", vm::cancelDelete, style = LifeButtonStyle.SECONDARY)
            Spacer(Modifier.weight(1f))
            LifeButton("Löschen", vm::confirmDeleteDocument, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
        }
    }
}

private sealed interface PreviewState {
    data object Loading : PreviewState
    data class Pages(val pages: List<Bitmap>, val total: Int) : PreviewState
    data class Failed(val reason: String) : PreviewState
}

/** Vorschau im Speicher: Bilder werden verkleinert dekodiert, PDFs seitenweise gerendert (max. 10 Seiten). */
@Composable
private fun Preview(d: Document, vm: DocumentDetailViewModel) {
    val context = LocalContext.current
    val preview by produceState<PreviewState>(PreviewState.Loading, d.sha256) {
        value = try {
            withContext(Dispatchers.IO) {
                val bytes = vm.bytes(d)
                when {
                    d.isImage -> {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        if (bmp == null) PreviewState.Failed("Bild konnte nicht dargestellt werden.") else PreviewState.Pages(listOf(bmp), 1)
                    }
                    d.isPdf -> {
                        // PdfRenderer braucht eine Datei: kurzlebige Kopie im privaten Cache, sofort wieder gelöscht.
                        val tmp = File.createTempFile("preview", ".pdf", context.cacheDir)
                        try {
                            tmp.writeBytes(bytes)
                            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                                PdfRenderer(fd).use { r ->
                                    val pages = (0 until minOf(r.pageCount, 10)).map { i ->
                                        r.openPage(i).use { p ->
                                            val w = 1240
                                            val h = (w.toFloat() * p.height / p.width).toInt().coerceIn(1, 4000)
                                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                                                b.eraseColor(Color.WHITE)
                                                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                            }
                                        }
                                    }
                                    PreviewState.Pages(pages, r.pageCount)
                                }
                            }
                        } finally {
                            tmp.delete()
                        }
                    }
                    else -> PreviewState.Failed("Für diesen Dateityp gibt es keine Vorschau.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PreviewState.Failed("Vorschau nicht möglich. Die Datei ist evtl. beschädigt oder passwortgeschützt.")
        }
    }
    Column(SpineInset.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val p = preview) {
            PreviewState.Loading -> Text("Entschlüssele …", style = Continuum.type.small)
            is PreviewState.Failed -> Text(p.reason, style = Continuum.type.small, color = ContinuumColors.Amber)
            is PreviewState.Pages -> {
                p.pages.forEachIndexed { i, b ->
                    Image(
                        b.asImageBitmap(), contentDescription = if (d.isPdf) "Seite ${i + 1} von ${p.total}" else d.title,
                        modifier = Modifier.fillMaxWidth().background(ContinuumColors.Elevated), contentScale = ContentScale.FillWidth,
                    )
                }
                if (p.total > p.pages.size) Text("Vorschau zeigt ${p.pages.size} von ${p.total} Seiten – vollständig über „In anderer App öffnen“.", style = Continuum.type.small)
            }
        }
    }
}
