package app.lifeos.feature.life.vehicles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeDatePickerOverlay
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeGlyph
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeLink
import app.lifeos.core.designsystem.component.LifeMetric
import app.lifeos.core.designsystem.component.LifeMoneyMetric
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeTag
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTimelineHeader
import app.lifeos.core.designsystem.component.LifeTimelineRow
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.MetricSize
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineRow
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.domain.life.VehicleLineItem
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.TransactionKind
import app.lifeos.core.model.VehicleEvent
import app.lifeos.core.model.VehicleEventKind
import app.lifeos.feature.life.LifeText
import app.lifeos.feature.life.shared.CollectLifeMessages
import app.lifeos.feature.life.shared.DocumentPickerOverlay
import app.lifeos.feature.life.shared.DocumentRow
import app.lifeos.feature.life.shared.EditorScaffold
import app.lifeos.feature.life.shared.FieldGroup
import app.lifeos.feature.life.shared.LinkedTransactionRow
import app.lifeos.feature.life.shared.RelatedRow
import app.lifeos.feature.life.shared.SpineInset
import app.lifeos.feature.life.shared.TransactionPickerOverlay
import app.lifeos.feature.life.shared.rememberDocumentPicker
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ───────────────────────────── Liste ─────────────────────────────

@Composable
fun VehiclesRoute(vm: VehiclesViewModel, onBack: () -> Unit, onOpen: (NodeId) -> Unit, onNew: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 150.dp)) {
        item { LifeTopBar(overline = "Mein Leben", title = "Fahrzeuge", onBack = onBack, actions = { LifeIconButton(Glyph.PLUS, "Neues Fahrzeug", onNew, highlighted = true) }) }
        if (s.cards.isEmpty()) {
            item { LifeEmpty("Noch kein Fahrzeug erfasst. Lege dein Fahrzeug an – Wartung, Reparaturen und Kosten laufen dann auf seiner eigenen Linie zusammen.", SpineInset, "Fahrzeug anlegen", onNew, Glyph.CAR) }
        }
        items(s.cards, key = { it.vehicle.id.value }) { c ->
            val due = c.next
            LifeTimelineRow(
                nodeShape = NodeShape.HEX,
                nodeTone = when {
                    due == null -> NodeTone.NEUTRAL
                    due.date.isBefore(s.today) -> NodeTone.ALERT
                    ChronoUnit.DAYS.between(s.today, due.date) <= 30 -> NodeTone.WARN
                    else -> NodeTone.ACTIVE
                },
                onClick = { onOpen(c.vehicle.id) },
                onClickLabel = "${c.vehicle.name} öffnen",
            ) {
                LifeGlyph(Glyph.CAR, tint = ContinuumColors.Cyan)
                Column(Modifier.weight(1f)) {
                    Text(c.vehicle.name, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(c.vehicle.plate, c.odometerKm?.let(LifeText::km), due?.let { "${it.title} ${LifeFormat.relativeDue(it.date, s.today)}" }).joinToString(" · ").ifEmpty { "Keine Angaben" },
                        style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(MoneyText.format(c.costsThisYearMinor), style = Continuum.type.figures)
                    Text("Kosten ${s.today.year}", style = Continuum.type.small)
                }
                if (c.vehicle.isSample) LifeTag("Beispiel", NodeTone.WARN)
            }
        }
    }
}

// ───────────────────────────── Detail ─────────────────────────────

data class VehicleDetailNavigation(
    val back: () -> Unit,
    val edit: () -> Unit,
    val openTransaction: (NodeId) -> Unit,
    val openDocument: (NodeId) -> Unit,
    val openNode: (NodeId) -> Unit,
    val openMap: (() -> Unit)? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleDetailRoute(vm: VehicleDetailViewModel, nav: VehicleDetailNavigation) {
    val state by vm.state.collectAsStateWithLifecycle()
    val draft by vm.eventDraft.collectAsStateWithLifecycle()
    val confirmDelete by vm.confirmDelete.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val query by vm.links.txQuery.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    var txPicker by rememberSaveable { mutableStateOf(false) }
    var docPicker by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val importDoc = rememberDocumentPicker { open, name, mime -> vm.links.importAndLink(open, name, mime) }

    CollectLifeMessages(vm.events)
    LaunchedEffect(deleted) {
        if (deleted) {
            haptics.confirm()
            signal.confirm("Fahrzeug gelöscht – Buchungen bleiben in der Haushaltskasse")
            nav.back()
        }
    }

    val s = state ?: return
    val snap = s.snapshot
    if (snap == null) {
        Column(Modifier.fillMaxSize()) {
            LifeTopBar(title = "Fahrzeug", onBack = nav.back)
            if (s.missing) LifeEmpty("Dieses Fahrzeug existiert nicht mehr.", SpineInset)
        }
        return
    }
    val v = snap.vehicle
    val links = s.links

    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 150.dp)) {
        item(key = "top") {
            LifeTopBar(
                overline = listOfNotNull("Fahrzeug", v.buildYear?.let { "Baujahr $it" }).joinToString(" · "),
                title = v.name,
                subtitle = listOfNotNull(v.plate, listOf(v.make, v.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }).joinToString(" · ").ifEmpty { null },
                onBack = nav.back,
                actions = {
                    nav.openMap?.let { LifeIconButton(Glyph.MAP, "In der LifeMap zeigen", it) }
                    LifeIconButton(Glyph.EDIT, "Fahrzeug bearbeiten", nav.edit)
                },
            )
        }
        item(key = "stand") {
            SpineSection("Stand", shape = NodeShape.HEX, tone = NodeTone.ACTIVE) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LifeMetric("Kilometerstand", snap.odometerKm?.let(LifeText::km) ?: "–", Modifier.weight(1f), size = MetricSize.LARGE, caption = if (snap.odometerKm == null) "noch nicht erfasst" else "zuletzt bekannt")
                    LifeMoneyMetric("Kosten ${s.today.year}", snap.costsThisYearMinor, Modifier.weight(1f), tone = NodeTone.OUTFLOW, caption = "gesamt " + MoneyText.format(snap.runningCostsMinor))
                }
                v.purchasePriceMinor?.let { Text("Kaufpreis ${MoneyText.format(it)}" + (v.purchaseDate?.let { d -> " am " + LifeFormat.numericDate(d) } ?: "") + " – nicht in den laufenden Kosten enthalten.", style = Continuum.type.small) }
                Text("Kosten sind Buchungen aus der Haushaltskasse, die diesem Fahrzeug oder einem seiner Einträge zugeordnet sind – jede genau einmal, ohne Kopie.", style = Continuum.type.small)
            }
        }

        // ── Fällig / geplant ──
        item(key = "due") {
            SpineSection("Geplant & fällig", shape = NodeShape.SQUARE, tone = if (snap.upcoming.any { it.date.isBefore(s.today) }) NodeTone.ALERT else NodeTone.WARN, fullBleed = true, trailing = { LifeLink("Planen", { vm.newEvent(planned = true) }) }) {
                if (snap.upcoming.isEmpty()) LifeEmpty("Nichts geplant. Trage z. B. die nächste Hauptuntersuchung ein – sie erscheint dann auf Home, sobald sie näher rückt.", SpineInset)
            }
        }
        items(snap.upcoming, key = { "u-" + it.id.value }) { e -> UpcomingRow(e, s.today, onDone = { vm.markDone(e) }, onEdit = { vm.editEvent(e) }) }

        // ── Fahrzeug-LifeLine ──
        item(key = "lineHeader") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LifeTimelineHeader("Fahrzeughistorie", Modifier.weight(1f), highlighted = true)
                LifeLink("Eintrag", { vm.newEvent(planned = false) }, Modifier.padding(end = 16.dp))
            }
        }
        val history = snap.line.filter { it !is VehicleLineItem.Event || it.event.done }
        if (history.isEmpty()) item(key = "noHistory") { LifeEmpty("Noch keine Historie. Wartung, Reparaturen, Teile und Reifen landen hier chronologisch.", SpineInset, "Eintrag hinzufügen", { vm.newEvent(planned = false) }) }
        items(history, key = { li ->
            when (li) {
                is VehicleLineItem.Purchase -> "purchase"
                is VehicleLineItem.Event -> "e-" + li.event.id.value
                is VehicleLineItem.Cost -> "c-" + li.tx.id.value
            }
        }) { li ->
            when (li) {
                is VehicleLineItem.Purchase -> LifeTimelineRow(nodeShape = NodeShape.HEX, nodeTone = NodeTone.ACTIVE) {
                    Column(Modifier.weight(1f)) {
                        Text("Kauf", style = Continuum.type.body)
                        Text(LifeFormat.dayTitle(li.date, s.today), style = Continuum.type.small)
                    }
                    li.priceMinor?.let { Text(MoneyText.format(it), style = Continuum.type.figures) }
                }
                is VehicleLineItem.Event -> LifeTimelineRow(nodeShape = NodeShape.SQUARE, nodeTone = NodeTone.DONE, onClick = { vm.editEvent(li.event) }, onClickLabel = "Eintrag bearbeiten") {
                    Column(Modifier.weight(1f)) {
                        Text(li.event.title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(LifeText.eventKind(li.event.kind), LifeFormat.dayTitle(li.date, s.today), li.event.odometerKm?.let(LifeText::km)).joinToString(" · "), style = Continuum.type.small, maxLines = 1)
                    }
                }
                is VehicleLineItem.Cost -> {
                    val tx = li.tx
                    val refund = tx.kind == TransactionKind.REFUND
                    LifeTimelineRow(nodeShape = NodeShape.DIAMOND, nodeTone = if (refund) NodeTone.INFLOW else NodeTone.OUTFLOW, onClick = { nav.openTransaction(tx.id) }, onClickLabel = "Buchung öffnen") {
                        Column(Modifier.weight(1f)) {
                            Text(tx.payee ?: (links?.categoryLabel(tx) ?: "Buchung"), style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Kosten · " + LifeFormat.dayTitle(li.date, s.today), style = Continuum.type.small)
                        }
                        Text(MoneyText.format(if (refund) tx.amount else -tx.amount, showPlus = refund), style = Continuum.type.figures)
                    }
                }
            }
        }

        // ── Direkt zugeordnete Buchungen ──
        if (links != null) {
            item(key = "txh") {
                SpineSection("Zugeordnete Buchungen", shape = NodeShape.DIAMOND, fullBleed = true, trailing = { LifeLink("Buchung zuordnen", { txPicker = true }) }) {
                    if (links.transactions.isEmpty()) LifeEmpty("Ordne Tankquittungen, Ersatzteile oder Werkstattrechnungen aus der Haushaltskasse zu.", SpineInset, "Buchung zuordnen", { txPicker = true })
                }
            }
            items(links.transactions, key = { "x-" + it.id.value }) { tx ->
                LinkedTransactionRow(tx, s.today, links.categoryLabel(tx), onOpen = { nav.openTransaction(tx.id) }, onUnlink = { vm.links.unassign(tx) })
            }
            item(key = "docs") {
                SpineSection("Dokumente", shape = NodeShape.SQUARE, fullBleed = true, trailing = { LifeLink("Verknüpfen", { docPicker = true }) }) {
                    if (links.documents.isEmpty()) LifeEmpty("Fahrzeugschein, Rechnungen, HU-Bericht – als PDF oder Foto.", SpineInset, "Dokument hinzufügen", { docPicker = true })
                }
            }
            items(links.documents, key = { "d-" + it.id.value }) { d -> DocumentRow(d, onOpen = { nav.openDocument(d.id) }, onUnlink = { vm.links.unlinkDocument(d) }) }
            if (links.other.isNotEmpty()) {
                item(key = "rel") { SpineSection("Verbunden mit", shape = NodeShape.LINKED, fullBleed = true) {} }
                items(links.other, key = { "r-" + it.link.id.value }) { r -> RelatedRow(r) { nav.openNode(r.node.id) } }
            }
        }
        v.note?.let { n -> item(key = "note") { SpineSection("Notizen", shape = NodeShape.DOT) { Text(n, style = Continuum.type.body) } } }
        item(key = "danger") {
            Row(SpineInset.padding(top = 24.dp)) { LifeButton("Fahrzeug löschen", vm::requestDelete, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH) }
        }
    }

    if (links != null) {
        TransactionPickerOverlay(txPicker, v.name, links, query, vm.links::setQuery, s.today, onPick = { vm.links.assign(it); txPicker = false }, onDismiss = { txPicker = false })
        DocumentPickerOverlay(docPicker, v.name, links.docCandidates, onPick = { vm.links.linkDocument(it); docPicker = false }, onImportNew = { docPicker = false; importDoc() }, onDismiss = { docPicker = false })
    }

    // ── Eintrag bearbeiten ──
    val d = draft
    LifeOverlay(visible = d != null, onDismiss = vm::closeEvent, title = when {
        d?.id != null -> "Eintrag bearbeiten"
        d?.done == false -> "Planen"
        else -> "Neuer Eintrag"
    }, overline = v.name) {
        if (d == null) return@LifeOverlay
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VehicleEventKind.entries.filter { it != VehicleEventKind.PURCHASE }.forEach { k ->
                LifeChoiceChip(LifeText.eventKind(k), d.kind == k, { vm.updateEvent { it.copy(kind = k) } })
            }
        }
        LifeTextField(d.title, { t -> vm.updateEvent { it.copy(title = t.take(120)) } }, "Bezeichnung", placeholder = LifeText.eventKind(d.kind), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LifeChoiceChip((if (d.done) "Am " else "Fällig ") + LifeFormat.numericDate(d.date), true, { pickDate = true }, glyph = Glyph.CALENDAR)
            LifeChoiceChip("Erledigt", d.done, { vm.updateEvent { it.copy(done = !it.done) } }, glyph = if (d.done) Glyph.CHECK else null, accent = ContinuumColors.Mint)
        }
        LifeTextField(d.odometerText, { t -> vm.updateEvent { it.copy(odometerText = t.filter { c -> c.isDigit() || c == '.' }.take(9)) } }, "Kilometerstand (optional)", placeholder = "z. B. 151200", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), error = d.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (d.id != null) LifeButton("Löschen", vm::deleteEvent, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
            Spacer(Modifier.weight(1f))
            LifeButton("Speichern", vm::saveEvent, glyph = Glyph.CHECK)
        }
        LifeDatePickerOverlay(pickDate, d.date, s.today, { pickDate = false }, { date -> vm.updateEvent { it.copy(date = date) } }, "Datum")
    }

    LifeOverlay(visible = confirmDelete, onDismiss = vm::cancelDelete, title = "Fahrzeug löschen?", overline = "Sicherheitsabfrage") {
        Text("„${v.name}“ und seine Historie werden gelöscht. Zugeordnete Buchungen und Dokumente bleiben unverändert erhalten.", style = Continuum.type.bodyMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeButton("Abbrechen", vm::cancelDelete, style = LifeButtonStyle.SECONDARY)
            Spacer(Modifier.weight(1f))
            LifeButton("Löschen", vm::confirmDeleteVehicle, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
        }
    }
}

@Composable
private fun UpcomingRow(e: VehicleEvent, today: LocalDate, onDone: () -> Unit, onEdit: () -> Unit) {
    val overdue = e.date.isBefore(today)
    SpineRow(
        modifier = Modifier.heightIn(min = 56.dp),
        node = {
            LifeNode(
                shape = NodeShape.SQUARE, tone = if (overdue) NodeTone.ALERT else NodeTone.WARN, size = 16.dp,
                onClick = onDone, onClickLabel = "„${e.title}“ als erledigt markieren",
            )
        },
    ) {
        Row(Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Bearbeiten", onClick = onEdit), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(LifeText.eventKind(e.kind) + " · " + LifeFormat.relativeDue(e.date, today), style = Continuum.type.small, color = if (overdue) ContinuumColors.Alert else ContinuumColors.Muted)
            }
            LifeGlyph(Glyph.EDIT, tint = ContinuumColors.Faint, size = 16.dp)
        }
    }
}

// ───────────────────────────── Editor ─────────────────────────────

@Composable
fun VehicleEditorRoute(vm: VehicleEditorViewModel, onClose: () -> Unit, onSaved: (NodeId, Boolean) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    var pick by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.savedId) {
        state.savedId?.let {
            haptics.confirm()
            signal.confirm(if (state.isEdit) "Fahrzeug gespeichert" else "Fahrzeug angelegt")
            onSaved(it, state.isEdit)
        }
    }
    LaunchedEffect(state.errors) { if (state.errors.isNotEmpty()) haptics.reject() }
    val f = state.form
    val e = state.errors
    EditorScaffold(
        overline = if (state.isEdit) "Fahrzeug bearbeiten" else "Neues Fahrzeug",
        title = f.name.ifBlank { listOf(f.make, f.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Fahrzeug" } },
        onBack = onClose, saving = state.saving, onSave = vm::save, generalError = e["general"],
    ) {
        if (state.loading) return@EditorScaffold
        LifeTextField(f.make, { t -> vm.update { it.copy(make = t.take(60)) } }, "Hersteller", placeholder = "z. B. Suzuki", modifier = SpineInset, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), error = e["name"])
        LifeTextField(f.model, { t -> vm.update { it.copy(model = t.take(60)) } }, "Modell", placeholder = "z. B. Samurai", modifier = SpineInset, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        LifeTextField(f.name, { t -> vm.update { it.copy(name = t.take(120)) } }, "Anzeigename (optional)", placeholder = "sonst Hersteller + Modell", modifier = SpineInset)
        Row(SpineInset, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeTextField(f.year, { t -> vm.update { it.copy(year = t.filter(Char::isDigit).take(4)) } }, "Baujahr", Modifier.weight(1f), placeholder = "1993", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), error = e["year"])
            LifeTextField(f.plate, { t -> vm.update { it.copy(plate = t.take(15)) } }, "Kennzeichen", Modifier.weight(1f), placeholder = "AB-CD 123", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
        }
        LifeTextField(f.odometer, { t -> vm.update { it.copy(odometer = t.filter { c -> c.isDigit() || c == '.' }.take(9)) } }, "Kilometerstand", placeholder = "z. B. 150000", modifier = SpineInset, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), error = e["odometer"])
        LifeTextField(f.vin, { t -> vm.update { it.copy(vin = t.take(17)) } }, "Fahrgestellnummer (optional)", modifier = SpineInset, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
        FieldGroup("Kauf (optional)", e["price"]) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LifeChoiceChip(f.purchaseDate?.let { "Gekauft " + LifeFormat.numericDate(it) } ?: "Kaufdatum …", f.purchaseDate != null, { pick = true }, glyph = Glyph.CALENDAR)
                if (f.purchaseDate != null) LifeIconButton(Glyph.CLOSE, "Kaufdatum entfernen", { vm.update { it.copy(purchaseDate = null) } })
            }
            LifeTextField(f.price, { t -> vm.update { it.copy(price = t.take(16)) } }, "Kaufpreis in €", placeholder = "0,00", textStyle = Continuum.type.figuresMedium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        LifeTextField(f.note, { t -> vm.update { it.copy(note = t.take(4000)) } }, "Notizen", placeholder = "Reifengröße, Ölsorte, Teilenummern …", singleLine = false, modifier = SpineInset)
    }
    LifeDatePickerOverlay(pick, f.purchaseDate ?: state.today, state.today, { pick = false }, { d -> vm.update { it.copy(purchaseDate = d) } }, "Kaufdatum")
}
