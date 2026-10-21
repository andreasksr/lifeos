package app.lifeos.feature.life.shared

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeGlyph
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeToastMessage
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineRow
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.domain.ports.LinkedNode
import app.lifeos.core.model.Document
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.life.LifeText
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.time.LocalDate

/** Zeigt Meldungen eines ViewModels als Bestätigung (mit Haptik) oder Fehlerhinweis an. */
@Composable
fun CollectLifeMessages(events: Flow<LifeMessage>) {
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    LaunchedEffect(events) {
        events.collect { m ->
            if (m.ok) {
                haptics.confirm()
                signal.confirm(m.text, m.undoLabel, m.undo)
            } else {
                haptics.reject()
                signal.toast(LifeToastMessage(m.text, tone = NodeTone.ALERT))
            }
        }
    }
}

/** Datei über den Android-Dateiauswahldialog (SAF) wählen – nur PDF und Bilder. */
@Composable
fun rememberDocumentPicker(onPicked: (open: () -> InputStream, fileName: String, mimeType: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "Dokument"
        val mime = resolver.getType(uri) ?: if (name.lowercase().endsWith(".pdf")) "application/pdf" else "application/octet-stream"
        onPicked({ requireNotNull(resolver.openInputStream(uri)) { "Datei nicht lesbar" } }, name, mime)
    }
    return { launcher.launch(arrayOf("application/pdf", "image/*")) }
}

@Composable
fun LinkedTransactionRow(tx: LedgerTransaction, today: LocalDate, category: String, onOpen: () -> Unit, onUnlink: (() -> Unit)?) {
    val refund = tx.kind == TransactionKind.REFUND
    SpineRow(
        modifier = Modifier.heightIn(min = 56.dp),
        node = { LifeNode(shape = NodeShape.DIAMOND, tone = if (refund) NodeTone.INFLOW else NodeTone.OUTFLOW, size = 12.dp) },
    ) {
        Row(Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Buchung öffnen", onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tx.payee ?: category, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(LifeFormat.dayTitle(tx.bookingDate, today) + " · " + category, style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(MoneyText.format(if (refund) tx.amount else -tx.amount, showPlus = refund), style = Continuum.type.figures, color = if (refund) ContinuumColors.Mint else ContinuumColors.Frost)
            Spacer(Modifier.width(4.dp))
        }
        if (onUnlink != null) LifeIconButton(Glyph.CLOSE, "Zuordnung entfernen", onUnlink, tint = ContinuumColors.Faint)
    }
}

@Composable
fun DocumentRow(doc: Document, onOpen: () -> Unit, onUnlink: (() -> Unit)? = null) {
    SpineRow(
        modifier = Modifier.heightIn(min = 56.dp),
        node = { LifeNode(shape = NodeShape.SQUARE, tone = NodeTone.ACTIVE, size = 12.dp) },
    ) {
        Row(Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Dokument öffnen", onClick = onOpen), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LifeGlyph(Glyph.DOCUMENT, tint = ContinuumColors.Cyan, size = 18.dp)
            Column(Modifier.weight(1f)) {
                Text(doc.title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(if (doc.isPdf) "PDF" else if (doc.isImage) "Bild" else "Datei", LifeText.fileSize(doc.sizeBytes), doc.docDate?.let(LifeFormat::numericDate)).joinToString(" · "),
                    style = Continuum.type.small,
                )
            }
        }
        if (onUnlink != null) LifeIconButton(Glyph.CLOSE, "Verknüpfung entfernen", onUnlink, tint = ContinuumColors.Faint)
    }
}

@Composable
fun RelatedRow(r: LinkedNode, onOpen: () -> Unit) {
    SpineRow(
        modifier = Modifier.heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onOpen),
        node = { LifeNode(shape = NodeShape.LINKED, tone = NodeTone.ACTIVE, size = 12.dp) },
    ) {
        Text(r.node.title, style = Continuum.type.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(LifeFormat.typeLabel(r.node.type), style = Continuum.type.small)
    }
}

@Composable
fun TransactionPickerOverlay(
    visible: Boolean,
    overline: String,
    links: LinksState,
    query: String,
    onQuery: (String) -> Unit,
    today: LocalDate,
    onPick: (LedgerTransaction) -> Unit,
    onDismiss: () -> Unit,
) {
    LifeOverlay(visible = visible, onDismiss = onDismiss, title = "Buchung zuordnen", overline = overline, fullHeight = true) {
        Text("Wähle eine bestehende Ausgabe oder Erstattung. Sie bleibt in der Haushaltskasse und erscheint zusätzlich hier – ohne Kopie.", style = Continuum.type.small)
        LifeTextField(query, onQuery, "Suchen", placeholder = "Händler, Notiz, Kategorie, Betrag", leading = Glyph.SEARCH)
        LazyColumn(Modifier.weight(1f)) {
            if (links.txCandidates.isEmpty()) item { LifeEmpty("Keine passenden Buchungen.") }
            items(links.txCandidates, key = { "c-" + it.id.value }) { tx ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Zuordnen") { onPick(tx) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LifeNode(shape = NodeShape.DIAMOND, tone = if (tx.kind == TransactionKind.REFUND) NodeTone.INFLOW else NodeTone.OUTFLOW, size = 10.dp)
                    Column(Modifier.weight(1f)) {
                        Text(tx.payee ?: links.categoryLabel(tx), style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(LifeFormat.dayTitle(tx.bookingDate, today) + " · " + links.categoryLabel(tx), style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(MoneyText.format(if (tx.kind == TransactionKind.REFUND) tx.amount else -tx.amount, showPlus = tx.kind == TransactionKind.REFUND), style = Continuum.type.figures)
                }
            }
        }
    }
}

@Composable
fun DocumentPickerOverlay(
    visible: Boolean,
    overline: String,
    candidates: List<Document>,
    onPick: (Document) -> Unit,
    onImportNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    LifeOverlay(visible = visible, onDismiss = onDismiss, title = "Dokument verknüpfen", overline = overline, fullHeight = true) {
        LifeButton("Neue Datei wählen (PDF oder Bild)", onImportNew, style = LifeButtonStyle.SECONDARY, glyph = Glyph.PLUS, fill = true)
        Text("Oder ein vorhandenes Dokument aus dem Archiv – es wird nur verknüpft, nicht kopiert.", style = Continuum.type.small)
        LazyColumn(Modifier.weight(1f)) {
            if (candidates.isEmpty()) item { LifeEmpty("Keine weiteren Dokumente im Archiv.") }
            items(candidates, key = { "d-" + it.id.value }) { d -> DocumentRow(d, onOpen = { onPick(d) }) }
        }
    }
}
