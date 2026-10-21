package app.lifeos.feature.projects.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextDecoration
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
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeLink
import app.lifeos.core.designsystem.component.LifeMoneyMetric
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeProgress
import app.lifeos.core.designsystem.component.LifeTag
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTimelineHeader
import app.lifeos.core.designsystem.component.LifeToastMessage
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.MetricSize
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.ProgressCenter
import app.lifeos.core.designsystem.component.SpineRow
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.domain.projects.ProjectLineItem
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.Milestone
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.ProjectStatus
import app.lifeos.core.model.Task
import app.lifeos.core.model.TaskStatus
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.projects.ProjectsText
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Navigation aus der Projektansicht in andere Bereiche – die App setzt sie um. */
data class ProjectDetailNavigation(
    val back: () -> Unit,
    val edit: () -> Unit,
    val openTask: (NodeId) -> Unit,
    val newTask: () -> Unit,
    val openTransaction: (NodeId) -> Unit,
    val openNode: (NodeId) -> Unit,
    val openMap: (() -> Unit)? = null,
)

@Composable
fun ProjectDetailRoute(vm: ProjectDetailViewModel, nav: ProjectDetailNavigation) {
    val state by vm.state.collectAsStateWithLifecycle()
    val draft by vm.milestoneDraft.collectAsStateWithLifecycle()
    val confirmDelete by vm.confirmDelete.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val query by vm.pickerQuery.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    var picker by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { m ->
            if (m.ok) {
                haptics.confirm()
                signal.confirm(m.text, m.undoLabel, m.undo)
            } else {
                haptics.reject()
                signal.toast(LifeToastMessage(m.text, tone = NodeTone.ALERT))
            }
        }
    }
    LaunchedEffect(deleted) {
        if (deleted) {
            signal.confirm("Projekt gelöscht – Aufgaben und Buchungen bleiben erhalten")
            nav.back()
        }
    }

    val s = state ?: return
    val snap = s.snapshot
    if (snap == null) {
        Column(Modifier.fillMaxSize()) {
            LifeTopBar(title = "Projekt", onBack = nav.back)
            if (s.missing) LifeEmpty("Dieses Projekt existiert nicht mehr.", modifier = Modifier.padding(start = ContinuumSpacing.spineRail))
        }
        return
    }
    val p = snap.project
    val inset = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)
    val done = p.status == ProjectStatus.DONE || p.status == ProjectStatus.CANCELLED

    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 150.dp)) {
        item(key = "top") {
            LifeTopBar(
                overline = listOfNotNull("Projekt", p.category).joinToString(" · "),
                title = p.name,
                subtitle = ProjectsText.status(p.status) + " · seit " + LifeFormat.numericDate(p.startDate),
                onBack = nav.back,
                actions = {
                    nav.openMap?.let { LifeIconButton(Glyph.MAP, "In der LifeMap zeigen", it) }
                    LifeIconButton(Glyph.EDIT, "Projekt bearbeiten", nav.edit)
                },
            )
        }

        // ── Stand: Fortschritt, Ziel, Kosten ──
        item(key = "status") {
            SpineSection("Stand", shape = NodeShape.LINKED, tone = if (done) NodeTone.DONE else NodeTone.ACTIVE) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    val pr = snap.progress.permille
                    if (pr != null) {
                        LifeProgress(pr / 1000f, size = 96.dp, meaning = if (done) LineMeaning.DONE else LineMeaning.PROGRESS, description = snap.progress.basis) {
                            ProgressCenter(LifeFormat.percent(pr.toLong()), "Fortschritt")
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(snap.progress.basis, style = Continuum.type.body)
                        p.targetDate?.let { t ->
                            val days = ChronoUnit.DAYS.between(s.today, t)
                            Text(
                                when {
                                    done -> "Zieltermin ${LifeFormat.numericDate(t)}"
                                    days > 0 -> "Noch $days Tage bis ${LifeFormat.numericDate(t)}"
                                    days == 0L -> "Zieltermin ist heute"
                                    else -> "Zieltermin seit ${-days} Tagen überschritten"
                                },
                                style = Continuum.type.small,
                                color = if (!done && days < 0) ContinuumColors.Alert else ContinuumColors.Muted,
                            )
                        }
                        if (p.progressMode == app.lifeos.core.model.ProgressMode.MANUAL) Text("Fortschritt bewusst festgelegt", style = Continuum.type.small)
                    }
                }
                p.description?.let { Text(it, style = Continuum.type.bodyMuted) }
            }
        }

        item(key = "costs") {
            val c = snap.costs
            SpineSection(
                "Kosten", shape = NodeShape.DIAMOND,
                tone = if (c.remainingMinor?.let { it < 0 } == true) NodeTone.ALERT else NodeTone.OUTFLOW,
                trailing = { LifeLink("Buchung zuordnen", { picker = true }) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LifeMoneyMetric("Projektkosten", c.netMinor, Modifier.weight(1f), size = MetricSize.LARGE, tone = NodeTone.OUTFLOW, caption = if (c.transactions.size == 1) "1 verknüpfte Buchung" else "${c.transactions.size} verknüpfte Buchungen")
                    c.budgetMinor?.let { b ->
                        LifeMoneyMetric(
                            if ((c.remainingMinor ?: 0) < 0) "Über Budget" else "Budget übrig",
                            kotlin.math.abs(c.remainingMinor ?: 0), Modifier.weight(1f),
                            tone = if ((c.remainingMinor ?: 0) < 0) NodeTone.ALERT else NodeTone.ACTIVE,
                            caption = "von " + MoneyText.format(b),
                        )
                    }
                }
                c.usedPermille?.let { LifeLine(progress = it / 1000f, meaning = LineMeaning.PROGRESS, progressDescription = "${LifeFormat.percent(it)} des Budgets") }
                Text("Kosten sind die Buchungen aus der Haushaltskasse, die diesem Projekt zugeordnet sind – ohne Kopie. Änderst du eine Buchung, ändert sich der Betrag hier sofort.", style = Continuum.type.small)
            }
        }

        // ── Projekt-LifeLine ──
        item(key = "lineHeader") { LifeTimelineHeader("Projektverlauf", highlighted = true) }
        var todayShown = false
        snap.line.forEach { li ->
            val date = li.date
            if (!todayShown && date != null && !date.isBefore(s.today) && li !is ProjectLineItem.StartItem) {
                todayShown = true
                item(key = "today") { LifeTimelineHeader("Heute · " + LifeFormat.numericDate(s.today), highlighted = true) }
            }
            item(key = "li-" + lineKey(li)) {
                LineRow(li, s.today, vm, nav, s.categoryNames)
            }
        }

        // ── Aufgaben ──
        item(key = "tasks") {
            val open = snap.tasks.count { it.status == TaskStatus.OPEN }
            SpineSection(
                "Aufgaben · $open offen", shape = NodeShape.RING, fullBleed = true,
                trailing = { LifeLink("Neue Aufgabe", nav.newTask) },
            ) {
                if (snap.tasks.isEmpty()) {
                    LifeEmpty("Noch keine Aufgaben in diesem Projekt.", modifier = inset, actionLabel = "Aufgabe hinzufügen", onAction = nav.newTask)
                }
            }
        }
        items(snap.tasks.sortedWith(compareBy<Task> { it.status }.thenBy { it.dueDate ?: LocalDate.MAX }), key = { "t-" + it.id.value }) { t ->
            ProjectTaskRow(t, s.today, onToggle = { vm.toggleTask(t) }, onOpen = { nav.openTask(t.id) })
        }

        // ── Meilensteine ──
        item(key = "ms") {
            SpineSection("Meilensteine", shape = NodeShape.SQUARE, fullBleed = true, trailing = { LifeLink("Neuer Meilenstein", { vm.editMilestone(null) }) }) {
                if (snap.milestones.isEmpty()) {
                    LifeEmpty("Meilensteine markieren wichtige Etappen auf der Projektlinie.", modifier = inset, actionLabel = "Meilenstein anlegen", onAction = { vm.editMilestone(null) })
                }
            }
        }
        items(snap.milestones, key = { "m-" + it.id.value }) { m -> MilestoneRow(m, s.today, { vm.toggleMilestone(m) }, { vm.editMilestone(m) }) }

        // ── Verknüpfte Buchungen ──
        item(key = "txh") {
            SpineSection("Zugeordnete Buchungen", shape = NodeShape.DIAMOND, fullBleed = true) {
                if (snap.costs.transactions.isEmpty()) {
                    LifeEmpty("Noch keine Buchungen zugeordnet.", modifier = inset, actionLabel = "Buchung zuordnen", onAction = { picker = true })
                }
            }
        }
        items(snap.costs.transactions, key = { "x-" + it.id.value }) { tx ->
            TransactionRow(tx, s.today, vm.categoryLabel(tx, s.categoryNames), onOpen = { nav.openTransaction(tx.id) }, onUnlink = { vm.unlink(tx) })
        }

        if (s.related.isNotEmpty()) {
            item(key = "rel") {
                SpineSection("Verbunden mit", shape = NodeShape.LINKED, fullBleed = true) {}
            }
            items(s.related, key = { "r-" + it.link.id.value }) { r ->
                SpineRow(
                    modifier = Modifier.heightIn(min = 52.dp).clickable(role = Role.Button) { nav.openNode(r.node.id) },
                    node = { LifeNode(shape = NodeShape.LINKED, tone = NodeTone.ACTIVE, size = 12.dp) },
                ) {
                    Text(r.node.title, style = Continuum.type.body, modifier = Modifier.weight(1f))
                    Text(LifeFormat.typeLabel(r.node.type), style = Continuum.type.small)
                }
            }
        }

        p.notes?.let { notes ->
            item(key = "notes") { SpineSection("Notizen", shape = NodeShape.DOT) { Text(notes, style = Continuum.type.body) } }
        }

        item(key = "danger") {
            Row(inset.padding(top = 24.dp)) {
                LifeButton("Projekt löschen", vm::requestDelete, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
            }
        }
    }

    // ── Buchung zuordnen ──
    LifeOverlay(visible = picker, onDismiss = { picker = false }, title = "Buchung zuordnen", overline = p.name, fullHeight = true) {
        Text("Wähle eine bestehende Ausgabe oder Erstattung. Sie bleibt in der Haushaltskasse und erscheint zusätzlich hier.", style = Continuum.type.small)
        LifeTextField(query, vm::setQuery, "Suchen", placeholder = "Händler, Notiz, Kategorie, Betrag", leading = Glyph.SEARCH)
        LazyColumn(Modifier.weight(1f)) {
            if (s.candidates.isEmpty()) item { LifeEmpty("Keine passenden Buchungen.") }
            items(s.candidates, key = { "c-" + it.id.value }) { tx ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Zuordnen") {
                        vm.assign(tx)
                        picker = false
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LifeNode(shape = NodeShape.DIAMOND, tone = if (tx.kind == TransactionKind.REFUND) NodeTone.INFLOW else NodeTone.OUTFLOW, size = 10.dp)
                    Column(Modifier.weight(1f)) {
                        Text(tx.payee ?: vm.categoryLabel(tx, s.categoryNames), style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(LifeFormat.dayTitle(tx.bookingDate, s.today) + " · " + vm.categoryLabel(tx, s.categoryNames), style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(MoneyText.format(if (tx.kind == TransactionKind.REFUND) tx.amount else -tx.amount, showPlus = tx.kind == TransactionKind.REFUND), style = Continuum.type.figures)
                }
            }
        }
    }

    // ── Meilenstein bearbeiten ──
    val d = draft
    LifeOverlay(visible = d != null, onDismiss = vm::closeMilestone, title = if (d?.id == null) "Neuer Meilenstein" else "Meilenstein", overline = p.name) {
        if (d == null) return@LifeOverlay
        LifeTextField(d.title, { v -> vm.updateMilestone { it.copy(title = v.take(120)) } }, "Titel", placeholder = "z. B. TÜV bestanden", error = d.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeChoiceChip(d.dueDate?.let { "Bis " + LifeFormat.numericDate(it) } ?: "Ohne Datum", d.dueDate != null, { pickDate = true }, glyph = Glyph.CALENDAR)
            if (d.dueDate != null) LifeIconButton(Glyph.CLOSE, "Datum entfernen", { vm.updateMilestone { it.copy(dueDate = null) } })
            if (d.id != null) LifeChoiceChip("Erreicht", d.done, { vm.updateMilestone { it.copy(done = !it.done) } }, glyph = if (d.done) Glyph.CHECK else null, accent = ContinuumColors.Mint)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (d.id != null) LifeButton("Löschen", vm::deleteMilestone, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
            Spacer(Modifier.weight(1f))
            LifeButton("Speichern", vm::saveMilestone, glyph = Glyph.CHECK)
        }
        LifeDatePickerOverlay(pickDate, d.dueDate ?: s.today, s.today, { pickDate = false }, { date -> vm.updateMilestone { it.copy(dueDate = date) } }, "Meilenstein-Datum")
    }

    LifeOverlay(visible = confirmDelete, onDismiss = vm::cancelDelete, title = "Projekt löschen?", overline = "Sicherheitsabfrage") {
        Text(
            "„${p.name}“ und seine Meilensteine werden gelöscht. Aufgaben bleiben erhalten (ohne Projekt), zugeordnete Buchungen bleiben unverändert in der Haushaltskasse.",
            style = Continuum.type.bodyMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeButton("Abbrechen", vm::cancelDelete, style = LifeButtonStyle.SECONDARY)
            Spacer(Modifier.weight(1f))
            LifeButton("Löschen", vm::confirmDeleteProject, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
        }
    }
}

private fun lineKey(li: ProjectLineItem): String = when (li) {
    is ProjectLineItem.StartItem -> "start"
    is ProjectLineItem.TargetItem -> "target"
    is ProjectLineItem.MilestoneItem -> "m" + li.milestone.id.value
    is ProjectLineItem.TaskItem -> "t" + li.task.id.value
    is ProjectLineItem.CostItem -> "c" + li.tx.id.value
}

@Composable
private fun LineRow(li: ProjectLineItem, today: LocalDate, vm: ProjectDetailViewModel, nav: ProjectDetailNavigation, cats: Map<NodeId, String>) {
    val dateText = li.date?.let { LifeFormat.dayTitle(it, today) } ?: "ohne Datum"
    when (li) {
        is ProjectLineItem.StartItem -> LineText(NodeShape.RING, NodeTone.ACTIVE, "Start", dateText, null, null)
        is ProjectLineItem.TargetItem -> LineText(NodeShape.SQUARE, if (li.date.isBefore(today)) NodeTone.ALERT else NodeTone.WARN, "Zieltermin", dateText, null, null)
        is ProjectLineItem.MilestoneItem -> {
            val m = li.milestone
            LineText(NodeShape.SQUARE, if (m.doneAt != null) NodeTone.DONE else NodeTone.NEUTRAL, m.title, "Meilenstein · $dateText", null) { vm.editMilestone(m) }
        }
        is ProjectLineItem.TaskItem -> {
            val t = li.task
            LineText(NodeShape.RING, if (t.status == TaskStatus.DONE) NodeTone.DONE else NodeTone.NEUTRAL, t.title, "Aufgabe · $dateText", null) { nav.openTask(t.id) }
        }
        is ProjectLineItem.CostItem -> {
            val tx = li.tx
            val signed = if (tx.kind == TransactionKind.REFUND) tx.amount else -tx.amount
            LineText(NodeShape.DIAMOND, if (tx.kind == TransactionKind.REFUND) NodeTone.INFLOW else NodeTone.OUTFLOW, tx.payee ?: vm.categoryLabel(tx, cats), "Kosten · $dateText", MoneyText.format(signed, showPlus = tx.kind == TransactionKind.REFUND)) {
                nav.openTransaction(tx.id)
            }
        }
    }
}

@Composable
private fun LineText(shape: NodeShape, tone: NodeTone, title: String, subtitle: String, trailing: String?, onClick: (() -> Unit)?) {
    SpineRow(
        modifier = Modifier.heightIn(min = 52.dp).then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        node = { LifeNode(shape = shape, tone = tone, size = 12.dp, active = tone == NodeTone.ACTIVE) },
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Continuum.type.small, maxLines = 1)
        }
        if (trailing != null) Text(trailing, style = Continuum.type.figures)
    }
}

@Composable
private fun ProjectTaskRow(t: Task, today: LocalDate, onToggle: () -> Unit, onOpen: () -> Unit) {
    val done = t.status == TaskStatus.DONE
    SpineRow(
        modifier = Modifier.heightIn(min = 56.dp),
        node = {
            LifeNode(
                shape = NodeShape.RING,
                tone = when {
                    done -> NodeTone.DONE
                    t.dueDate?.isBefore(today) == true -> NodeTone.ALERT
                    else -> NodeTone.NEUTRAL
                },
                size = 16.dp, onClick = onToggle,
                onClickLabel = if (done) "„${t.title}“ wieder öffnen" else "„${t.title}“ erledigen",
            )
        },
    ) {
        Row(Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Aufgabe öffnen", onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    t.title,
                    style = Continuum.type.body.copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                    color = if (done) ContinuumColors.Faint else ContinuumColors.Frost,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                t.dueDate?.let { Text(LifeFormat.dayTitle(it, today), style = Continuum.type.small) }
            }
            if (t.priority == app.lifeos.core.model.Priority.HIGH && !done) LifeTag("Hoch", NodeTone.WARN)
        }
    }
}

@Composable
private fun MilestoneRow(m: Milestone, today: LocalDate, onToggle: () -> Unit, onEdit: () -> Unit) {
    val done = m.doneAt != null
    SpineRow(
        modifier = Modifier.heightIn(min = 56.dp),
        node = {
            LifeNode(
                shape = NodeShape.SQUARE,
                tone = when {
                    done -> NodeTone.DONE
                    m.dueDate?.isBefore(today) == true -> NodeTone.ALERT
                    else -> NodeTone.WARN
                },
                size = 16.dp, onClick = onToggle,
                onClickLabel = if (done) "Meilenstein „${m.title}“ wieder öffnen" else "Meilenstein „${m.title}“ erreicht",
            )
        },
    ) {
        Row(Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Meilenstein bearbeiten", onClick = onEdit), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.title, style = Continuum.type.body.copy(textDecoration = if (done) TextDecoration.LineThrough else null), color = if (done) ContinuumColors.Faint else ContinuumColors.Frost)
                Text(m.dueDate?.let { LifeFormat.dayTitle(it, today) } ?: "ohne Datum", style = Continuum.type.small)
            }
            LifeGlyph(Glyph.EDIT, tint = ContinuumColors.Faint, size = 16.dp)
        }
    }
}

@Composable
private fun TransactionRow(tx: LedgerTransaction, today: LocalDate, category: String, onOpen: () -> Unit, onUnlink: () -> Unit) {
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
        LifeIconButton(Glyph.CLOSE, "Zuordnung entfernen", onUnlink, tint = ContinuumColors.Faint)
    }
}
