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
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeStepper
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTimePickerOverlay
import app.lifeos.core.designsystem.component.LifeToastMessage
import app.lifeos.core.designsystem.component.LifeToggle
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.Repeat
import app.lifeos.core.model.RepeatUnit
import app.lifeos.feature.planning.presentation.PlanningText

private enum class PickTarget { START_DATE, END_DATE, START_TIME, END_TIME }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditorRoute(vm: EventEditorViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val allowed by vm.notificationsAllowed.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    LaunchedEffect(state.outcome) {
        when (val o = state.outcome) {
            is EventOutcome.Saved -> {
                haptics.confirm()
                signal.confirm(o.text)
                onClose()
            }
            is EventOutcome.Deleted -> {
                haptics.confirm()
                signal.confirm("Termin gelöscht", "Rückgängig") {
                    vm.restore(o.snapshot) { ok ->
                        if (ok) signal.confirm("Termin wiederhergestellt") else signal.toast(LifeToastMessage("Wiederherstellen nicht möglich.", tone = NodeTone.ALERT))
                    }
                }
                onClose()
            }
            null -> Unit
        }
    }
    LaunchedEffect(state.errors) { if (state.errors.isNotEmpty()) haptics.reject() }

    val f = state.form
    var pick by rememberSaveable { mutableStateOf<PickTarget?>(null) }
    val inset = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)

    Column(Modifier.fillMaxSize().imePadding()) {
        LifeTopBar(
            overline = if (state.isEdit) "Termin" else "Neuer Termin",
            title = f.title.ifBlank { if (state.isEdit) "Termin" else "Neuer Termin" },
            onBack = onClose,
            actions = { if (state.isEdit) LifeIconButton(Glyph.TRASH, "Termin löschen", vm::requestDelete, tint = ContinuumColors.Alert) },
        )
        if (state.loading) return@Column

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("lifeos-editor"),
            verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.l),
        ) {
            LifeTextField(
                value = f.title,
                onValueChange = { v -> vm.update { it.copy(title = v.take(200)) } },
                label = "Titel",
                placeholder = "z. B. Unterricht, Werkstatttermin",
                error = state.errors[EventField.TITLE],
                textStyle = Continuum.type.title,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = inset,
            )
            LifeToggle(f.allDay, { v -> vm.update { it.copy(allDay = v) } }, "Ganztägig", modifier = inset)

            Section("Beginn", null, inset) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeChoiceChip(LifeFormat.dayTitle(f.startDate, state.today), true, { pick = PickTarget.START_DATE }, glyph = Glyph.CALENDAR)
                    if (!f.allDay) LifeChoiceChip(LifeFormat.time(f.startTime), true, { pick = PickTarget.START_TIME }, glyph = Glyph.CLOCK)
                }
            }
            Section("Ende", state.errors[EventField.TIME], inset) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeChoiceChip(LifeFormat.dayTitle(f.endDate, state.today), f.endDate != f.startDate, { pick = PickTarget.END_DATE }, glyph = Glyph.CALENDAR)
                    if (!f.allDay) LifeChoiceChip(LifeFormat.time(f.endTime), true, { pick = PickTarget.END_TIME }, glyph = Glyph.CLOCK)
                }
            }

            LifeTextField(
                value = f.location,
                onValueChange = { v -> vm.update { it.copy(location = v.take(200)) } },
                label = "Ort",
                placeholder = "optional",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = inset,
            )

            Section("Wiederholung", null, inset) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeChoiceChip("Keine", f.repeatUnit == null, { vm.update { it.copy(repeatUnit = null) } })
                    RepeatUnit.entries.forEach { u ->
                        LifeChoiceChip(PlanningText.repeat(Repeat(u)), f.repeatUnit == u, { vm.update { it.copy(repeatUnit = u) } })
                    }
                }
                f.repeatUnit?.let { unit ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Intervall", style = Continuum.type.small, modifier = Modifier.weight(1f))
                        LifeStepper(f.repeatInterval, { n -> vm.update { it.copy(repeatInterval = n) } }, 1..52, "Intervall", format = { n -> PlanningText.repeat(Repeat(unit, n)) })
                    }
                }
            }

            Section("Erinnerung", null, inset) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanningText.reminderOptions.forEach { m ->
                        LifeChoiceChip(
                            if (m == 0) "Zum Beginn" else PlanningText.reminder(m),
                            f.reminderMinutes == m,
                            { vm.update { it.copy(reminderMinutes = m) } },
                            glyph = if (m != null && f.reminderMinutes == m) Glyph.BELL else null,
                        )
                    }
                }
                if (f.reminderMinutes != null && !allowed) {
                    Text("Benachrichtigungen sind für LifeOS nicht erlaubt.", style = Continuum.type.small, color = ContinuumColors.Amber)
                    LifeButton("Benachrichtigungen erlauben", vm::requestNotifications, style = LifeButtonStyle.SECONDARY, glyph = Glyph.BELL)
                }
            }

            if (state.openTasks.isNotEmpty()) {
                Section("Mit Aufgabe verknüpfen", null, inset) {
                    Text("Der Termin bleibt ein Termin, die Aufgabe eine Aufgabe – beide verweisen nur aufeinander.", style = Continuum.type.small)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LifeChoiceChip("Keine", f.linkedTaskId == null, { vm.update { it.copy(linkedTaskId = null) } })
                        state.openTasks.take(12).forEach { t ->
                            LifeChoiceChip(t.title, f.linkedTaskId == t.id, { vm.update { it.copy(linkedTaskId = t.id) } }, glyph = if (f.linkedTaskId == t.id) Glyph.LINK else null)
                        }
                    }
                }
            }

            LifeTextField(
                value = f.note,
                onValueChange = { v -> vm.update { it.copy(note = v.take(4000)) } },
                label = "Notiz",
                placeholder = "optional",
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = inset,
            )
            state.errors[EventField.GENERAL]?.let { Text(it, style = Continuum.type.body, color = ContinuumColors.Alert, modifier = inset) }
            Spacer(Modifier.height(ContinuumSpacing.l))
        }

        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter, top = 8.dp, bottom = 12.dp)) {
            LifeButton(if (state.saving) "Speichert …" else "Speichern", vm::save, enabled = !state.saving, glyph = Glyph.CHECK, fill = true)
        }
    }

    LifeDatePickerOverlay(pick == PickTarget.START_DATE, f.startDate, state.today, { pick = null }, { d ->
        vm.update { it.copy(startDate = d, endDate = d.plusDays(java.time.temporal.ChronoUnit.DAYS.between(it.startDate, it.endDate))) }
    }, "Beginn")
    LifeDatePickerOverlay(pick == PickTarget.END_DATE, f.endDate, state.today, { pick = null }, { d -> vm.update { it.copy(endDate = d) } }, "Ende")
    LifeTimePickerOverlay(pick == PickTarget.START_TIME, f.startTime, { pick = null }, { t ->
        vm.update {
            val duration = java.time.Duration.between(it.start, it.end)
            val newStart = it.startDate.atTime(t)
            val newEnd = newStart.plus(duration)
            it.copy(startTime = t, endDate = newEnd.toLocalDate(), endTime = newEnd.toLocalTime())
        }
    }, "Beginn")
    LifeTimePickerOverlay(pick == PickTarget.END_TIME, f.endTime, { pick = null }, { t -> vm.update { it.copy(endTime = t) } }, "Ende")

    LifeOverlay(visible = state.confirmDelete, onDismiss = vm::cancelDelete, title = "Termin löschen?", overline = "Sicherheitsabfrage") {
        Text(
            if (f.repeatUnit != null) "Der Termin wird mit allen Wiederholungen entfernt. Direkt danach kannst du das Löschen rückgängig machen."
            else "Der Termin wird entfernt. Direkt danach kannst du das Löschen rückgängig machen.",
            style = Continuum.type.bodyMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeButton("Abbrechen", vm::cancelDelete, style = LifeButtonStyle.SECONDARY)
            Spacer(Modifier.weight(1f))
            LifeButton("Löschen", vm::confirmDelete, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
        }
    }
}
