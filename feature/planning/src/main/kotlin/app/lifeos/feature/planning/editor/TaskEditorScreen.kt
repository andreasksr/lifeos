package app.lifeos.feature.planning.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeDatePickerOverlay
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeSegmented
import app.lifeos.core.designsystem.component.LifeStepper
import app.lifeos.core.designsystem.component.LifeTag
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTimePickerOverlay
import app.lifeos.core.designsystem.component.LifeToastMessage
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.Priority
import app.lifeos.core.model.RepeatUnit
import app.lifeos.core.model.TaskStatus
import app.lifeos.feature.planning.presentation.PlanningText
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorRoute(vm: TaskEditorViewModel, onClose: () -> Unit) {
    val keyboardOnExit = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { keyboardOnExit?.hide() } }
    val state by vm.state.collectAsStateWithLifecycle()
    val allowed by vm.notificationsAllowed.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    LaunchedEffect(state.outcome) {
        when (val o = state.outcome) {
            is TaskOutcome.Saved -> {
                haptics.confirm()
                signal.confirm(o.text)
                onClose()
            }
            is TaskOutcome.Deleted -> {
                haptics.confirm()
                signal.confirm("Aufgabe gelöscht", "Rückgängig") {
                    vm.restore(o.snapshot) { ok ->
                        if (ok) signal.confirm("Aufgabe wiederhergestellt") else signal.toast(LifeToastMessage("Wiederherstellen nicht möglich.", tone = NodeTone.ALERT))
                    }
                }
                onClose()
            }
            null -> Unit
        }
    }
    LaunchedEffect(state.errors) { if (state.errors.isNotEmpty()) haptics.reject() }

    val f = state.form
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val inset = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)

    Column(Modifier.fillMaxSize().imePadding()) {
        LifeTopBar(
            overline = if (state.isEdit) "Aufgabe" else "Neue Aufgabe",
            title = f.title.ifBlank { if (state.isEdit) "Aufgabe" else "Neue Aufgabe" },
            onBack = onClose,
            actions = {
                if (state.isEdit) LifeIconButton(Glyph.TRASH, "Aufgabe löschen", vm::requestDelete, tint = ContinuumColors.Alert)
            },
        )
        if (state.loading) return@Column

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("lifeos-editor"),
            verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.l),
        ) {
            if (state.isEdit && state.status == TaskStatus.DONE) {
                Row(inset) { LifeTag("Erledigt", NodeTone.DONE) }
            }
            LifeTextField(
                value = f.title,
                onValueChange = { v -> vm.update { it.copy(title = v.take(200)) } },
                label = "Titel",
                placeholder = "Was ist zu tun?",
                error = state.errors[TaskField.TITLE],
                textStyle = Continuum.type.title,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = inset,
            )

            Section("Priorität", null, inset) {
                LifeSegmented(
                    listOf(Priority.LOW to "Niedrig", Priority.NORMAL to "Normal", Priority.HIGH to "Hoch"),
                    f.priority,
                    { p -> vm.update { it.copy(priority = p) } },
                )
            }

            Section("Fällig" + (f.dueDate?.let { " · " + LifeFormat.numericDate(it) } ?: ""), state.errors[TaskField.DUE], inset) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeChoiceChip("Ohne Datum", f.dueDate == null, { vm.update { it.copy(dueDate = null) } })
                    LifeChoiceChip("Heute", f.dueDate == state.today, { vm.update { it.copy(dueDate = state.today) } })
                    LifeChoiceChip("Morgen", f.dueDate == state.today.plusDays(1), { vm.update { it.copy(dueDate = state.today.plusDays(1)) } })
                    val other = f.dueDate != null && f.dueDate != state.today && f.dueDate != state.today.plusDays(1)
                    LifeChoiceChip(if (other) LifeFormat.dayTitle(f.dueDate!!, state.today) else "Datum …", other, { pickDate = true }, glyph = Glyph.CALENDAR)
                }
                if (f.dueDate != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LifeChoiceChip("Ohne Uhrzeit", f.dueTime == null, { vm.update { it.copy(dueTime = null) } })
                        LifeChoiceChip(f.dueTime?.let { LifeFormat.time(it) } ?: "Uhrzeit …", f.dueTime != null, { pickTime = true }, glyph = Glyph.CLOCK)
                    }
                }
            }

            if (f.dueDate != null) {
                Section("Wiederholung", null, inset) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LifeChoiceChip("Keine", f.repeatUnit == null, { vm.update { it.copy(repeatUnit = null) } })
                        RepeatUnit.entries.forEach { u ->
                            LifeChoiceChip(PlanningText.repeat(app.lifeos.core.model.Repeat(u)), f.repeatUnit == u, { vm.update { it.copy(repeatUnit = u) } }, glyph = if (f.repeatUnit == u) Glyph.REPEAT else null)
                        }
                    }
                    if (f.repeatUnit != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Intervall", style = Continuum.type.small, modifier = Modifier.weight(1f))
                            LifeStepper(
                                f.repeatInterval, { n -> vm.update { it.copy(repeatInterval = n) } }, 1..52, "Intervall",
                                format = { n -> PlanningText.repeat(app.lifeos.core.model.Repeat(f.repeatUnit, n)) },
                            )
                        }
                        Text("Beim Erledigen rückt die Aufgabe auf die nächste Fälligkeit vor.", style = Continuum.type.small)
                    }
                }

                Section("Erinnerung", state.errors[TaskField.REMINDER], inset) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanningText.reminderOptions.forEach { m ->
                            LifeChoiceChip(PlanningText.reminder(m), f.reminderMinutes == m, { vm.update { it.copy(reminderMinutes = m) } }, glyph = if (m != null && f.reminderMinutes == m) Glyph.BELL else null)
                        }
                    }
                    if (f.reminderMinutes != null && f.dueTime == null) {
                        Text("Ohne Uhrzeit gilt die Aufgabe um ${LifeFormat.time(app.lifeos.core.model.Reminder.DEFAULT_TASK_TIME)} als fällig.", style = Continuum.type.small)
                    }
                    if (f.reminderMinutes != null && !allowed) {
                        Text("Benachrichtigungen sind für LifeOS nicht erlaubt – die Erinnerung wird gespeichert, aber erst nach der Erlaubnis angezeigt.", style = Continuum.type.small, color = ContinuumColors.Amber)
                        LifeButton("Benachrichtigungen erlauben", vm::requestNotifications, style = LifeButtonStyle.SECONDARY, glyph = Glyph.BELL)
                    }
                }
            }

            if (state.projects.isNotEmpty()) {
                Section("Projekt", null, inset) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LifeChoiceChip("Kein Projekt", f.projectId == null, { vm.update { it.copy(projectId = null) } })
                        state.projects.forEach { p ->
                            LifeChoiceChip(p.name, f.projectId == p.id, { vm.update { it.copy(projectId = p.id) } }, glyph = if (f.projectId == p.id) Glyph.PROJECT else null)
                        }
                    }
                }
            }

            LifeTextField(
                value = f.description,
                onValueChange = { v -> vm.update { it.copy(description = v.take(4000)) } },
                label = "Beschreibung",
                placeholder = "optional",
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = inset,
            )

            if (state.linkedEvents.isNotEmpty()) {
                Section("Verknüpfte Termine", null, inset) {
                    state.linkedEvents.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LifeNode(shape = NodeShape.DOT, tone = NodeTone.ACTIVE, size = 10.dp)
                            Text(e.title, style = Continuum.type.body, modifier = Modifier.weight(1f))
                            Text(LifeFormat.dayTitle(e.start.toLocalDate(), state.today), style = Continuum.type.small)
                        }
                    }
                }
            }

            state.errors[TaskField.GENERAL]?.let { Text(it, style = Continuum.type.body, color = ContinuumColors.Alert, modifier = inset) }
            Spacer(Modifier.height(ContinuumSpacing.l))
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter, top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isEdit) {
                LifeButton(
                    if (state.status == TaskStatus.DONE) "Wieder öffnen" else "Erledigt",
                    vm::toggleDone,
                    style = LifeButtonStyle.SECONDARY,
                    glyph = if (state.status == TaskStatus.DONE) Glyph.UNDO else Glyph.CHECK,
                )
            }
            LifeButton(if (state.saving) "Speichert …" else "Speichern", vm::save, enabled = !state.saving, glyph = Glyph.CHECK, fill = true, modifier = Modifier.weight(1f))
        }
    }

    LifeDatePickerOverlay(pickDate, f.dueDate ?: state.today, state.today, { pickDate = false }, { d -> vm.update { it.copy(dueDate = d) } }, "Fälligkeit")
    LifeTimePickerOverlay(pickTime, f.dueTime ?: LocalTime.of(9, 0), { pickTime = false }, { t -> vm.update { it.copy(dueTime = t) } }, "Uhrzeit der Fälligkeit")

    LifeOverlay(visible = state.confirmDelete, onDismiss = vm::cancelDelete, title = "Aufgabe löschen?", overline = "Sicherheitsabfrage") {
        Text("Die Aufgabe und ihre Erinnerungen werden entfernt. Direkt danach kannst du das Löschen rückgängig machen.", style = Continuum.type.bodyMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeButton("Abbrechen", vm::cancelDelete, style = LifeButtonStyle.SECONDARY)
            Spacer(Modifier.weight(1f))
            LifeButton("Löschen", vm::confirmDelete, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
        }
    }
}

@Composable
internal fun Section(title: String, error: String?, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = Continuum.type.label, color = if (error != null) ContinuumColors.Alert else ContinuumColors.Muted)
        content()
        if (error != null) Text(error, style = Continuum.type.small, color = ContinuumColors.Alert)
    }
}
