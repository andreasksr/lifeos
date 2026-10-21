package app.lifeos.feature.planning.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.domain.planning.PlanningResult
import app.lifeos.core.domain.planning.TaskDraft
import app.lifeos.core.model.Priority
import app.lifeos.feature.planning.PlanningDependencies
import app.lifeos.feature.planning.presentation.PlanningText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuickDue { NONE, TODAY, TOMORROW }

data class QuickTaskState(
    val title: String = "",
    val due: QuickDue = QuickDue.TODAY,
    val important: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false,
    /** Zähler – jede erfolgreiche Speicherung erhöht ihn (Signal an die Oberfläche). */
    val savedCount: Int = 0,
    val lastSaved: String? = null,
)

/** Aufgabe in Sekunden: Titel, Fälligkeit, „Wichtig" – alles Weitere später im Editor. */
class QuickTaskViewModel(private val deps: PlanningDependencies) : ViewModel() {
    private val _state = MutableStateFlow(QuickTaskState())
    val state: StateFlow<QuickTaskState> = _state.asStateFlow()

    fun reset() {
        _state.value = QuickTaskState(savedCount = _state.value.savedCount)
    }

    fun title(v: String) = _state.update { it.copy(title = v.take(200), error = null) }
    fun due(d: QuickDue) = _state.update { it.copy(due = d) }
    fun important(v: Boolean) = _state.update { it.copy(important = v) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val today = deps.clock.today()
                val draft = TaskDraft(
                    title = s.title,
                    priority = if (s.important) Priority.HIGH else Priority.NORMAL,
                    dueDate = when (s.due) {
                        QuickDue.NONE -> null
                        QuickDue.TODAY -> today
                        QuickDue.TOMORROW -> today.plusDays(1)
                    },
                )
                when (val r = deps.saveTask(draft)) {
                    is PlanningResult.Success -> _state.value = QuickTaskState(savedCount = s.savedCount + 1, lastSaved = s.title.trim())
                    is PlanningResult.Invalid -> _state.update { it.copy(saving = false, error = PlanningText.error(r.errors.first())) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = "Speichern fehlgeschlagen. Es wurde nichts verändert.") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickTaskCapture(vm: QuickTaskViewModel, onDone: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    val focus = androidx.compose.runtime.remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(s.savedCount) {
        if (s.savedCount > 0 && s.lastSaved != null) {
            haptics.confirm()
            signal.confirm("Aufgabe „${s.lastSaved}“ gespeichert")
            onDone()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LifeTextField(
            value = s.title,
            onValueChange = vm::title,
            label = "Aufgabe",
            placeholder = "Was ist zu tun?",
            error = s.error,
            textStyle = Continuum.type.title,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.save() }),
            modifier = Modifier.focusRequester(focus),
        )
        Text("FÄLLIG", style = Continuum.type.label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeChoiceChip("Heute", s.due == QuickDue.TODAY, { vm.due(QuickDue.TODAY) })
            LifeChoiceChip("Morgen", s.due == QuickDue.TOMORROW, { vm.due(QuickDue.TOMORROW) })
            LifeChoiceChip("Ohne Datum", s.due == QuickDue.NONE, { vm.due(QuickDue.NONE) })
            LifeChoiceChip("Wichtig", s.important, { vm.important(!s.important) }, glyph = if (s.important) Glyph.CHECK else null, accent = app.lifeos.core.designsystem.theme.ContinuumColors.Amber)
        }
        Text("Uhrzeit, Wiederholung, Erinnerung und Projekt legst du bei Bedarf in der Aufgabe fest.", style = Continuum.type.small)
    }
}

@Composable
fun QuickTaskSaveBar(vm: QuickTaskViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifeButton(if (s.saving) "Speichert …" else "Aufgabe speichern", vm::save, enabled = !s.saving, glyph = Glyph.CHECK, fill = true)
}
