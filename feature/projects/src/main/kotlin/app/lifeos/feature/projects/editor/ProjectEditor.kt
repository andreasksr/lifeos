package app.lifeos.feature.projects.editor

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeDatePickerOverlay
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeSegmented
import app.lifeos.core.designsystem.component.LifeStepper
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.domain.projects.ProjectDraft
import app.lifeos.core.domain.projects.ProjectError
import app.lifeos.core.domain.projects.ProjectResult
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.ProgressMode
import app.lifeos.core.model.ProjectStatus
import app.lifeos.feature.projects.ProjectsDependencies
import app.lifeos.feature.projects.ProjectsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ProjectField { NAME, DATES, PROGRESS, BUDGET, GENERAL }

data class ProjectForm(
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val startDate: LocalDate,
    val targetDate: LocalDate? = null,
    val mode: ProgressMode = ProgressMode.TASKS,
    /** Manueller Fortschritt in 5-%-Schritten (0..20). */
    val manualSteps: Int = 0,
    val budgetText: String = "",
    val notes: String = "",
)

data class ProjectEditorState(
    val loading: Boolean = true,
    val isEdit: Boolean,
    val form: ProjectForm,
    val categories: List<String> = emptyList(),
    val today: LocalDate,
    val errors: Map<ProjectField, String> = emptyMap(),
    val saving: Boolean = false,
    /** Gesetzt nach dem Speichern: ID des Projekts (für die Navigation zur Detailansicht). */
    val savedId: NodeId? = null,
)

class ProjectEditorViewModel(private val deps: ProjectsDependencies, private val projectId: NodeId?) : ViewModel() {
    private val _state = MutableStateFlow(
        ProjectEditorState(isEdit = projectId != null, form = ProjectForm(startDate = deps.clock.today()), today = deps.clock.today()),
    )
    val state: StateFlow<ProjectEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val all = deps.projects.projects()
                val p = projectId?.let { id -> all.firstOrNull { it.id == id } }
                val cats = (ProjectsText.suggestedCategories + all.mapNotNull { it.category }).distinct()
                _state.update {
                    it.copy(
                        loading = false,
                        categories = cats,
                        form = p?.let { x ->
                            ProjectForm(
                                name = x.name,
                                description = x.description.orEmpty(),
                                category = x.category.orEmpty(),
                                status = x.status,
                                startDate = x.startDate,
                                targetDate = x.targetDate,
                                mode = x.progressMode,
                                manualSteps = (x.manualProgressPermille ?: 0) / 50,
                                budgetText = x.budgetMinor?.let { b -> MoneyText.toEditText(b) }.orEmpty(),
                                notes = x.notes.orEmpty(),
                            )
                        } ?: it.form,
                        errors = if (projectId != null && p == null) mapOf(ProjectField.GENERAL to "Das Projekt existiert nicht mehr.") else emptyMap(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, errors = mapOf(ProjectField.GENERAL to "Daten konnten nicht geladen werden.")) }
            }
        }
    }

    fun update(change: (ProjectForm) -> ProjectForm) = _state.update { it.copy(form = change(it.form), errors = emptyMap()) }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        val f = s.form
        val budget: Long? = if (f.budgetText.isBlank()) null else when (val r = MoneyText.parse(f.budgetText)) {
            is MoneyText.ParseResult.Success -> r.minor
            is MoneyText.ParseResult.Failure -> {
                _state.update { it.copy(errors = mapOf(ProjectField.BUDGET to "Ungültiger Betrag (Beispiel: 1.500,00).")) }
                return
            }
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val draft = ProjectDraft(
                    id = projectId,
                    name = f.name,
                    description = f.description,
                    category = f.category,
                    status = f.status,
                    startDate = f.startDate,
                    targetDate = f.targetDate,
                    progressMode = f.mode,
                    manualProgressPermille = if (f.mode == ProgressMode.MANUAL) f.manualSteps * 50 else null,
                    budgetMinor = budget,
                    notes = f.notes,
                )
                when (val r = deps.saveProject(draft)) {
                    is ProjectResult.Success -> _state.update { it.copy(saving = false, savedId = r.value) }
                    is ProjectResult.Invalid -> _state.update { it.copy(saving = false, errors = r.errors.associate { e -> fieldOf(e) to ProjectsText.error(e) }) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, errors = mapOf(ProjectField.GENERAL to "Speichern fehlgeschlagen. Es wurde nichts verändert.")) }
            }
        }
    }

    private fun fieldOf(e: ProjectError) = when (e) {
        ProjectError.NameRequired, ProjectError.NameTooLong -> ProjectField.NAME
        ProjectError.TargetBeforeStart -> ProjectField.DATES
        ProjectError.ManualProgressMissing -> ProjectField.PROGRESS
        ProjectError.InvalidBudget -> ProjectField.BUDGET
        else -> ProjectField.GENERAL
    }
}

private enum class DateTarget { START, TARGET }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectEditorRoute(vm: ProjectEditorViewModel, onClose: () -> Unit, onSaved: (NodeId, Boolean) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    LaunchedEffect(state.savedId) {
        state.savedId?.let {
            haptics.confirm()
            signal.confirm(if (state.isEdit) "Projekt gespeichert" else "Projekt „${state.form.name.trim()}“ angelegt")
            onSaved(it, state.isEdit)
        }
    }
    LaunchedEffect(state.errors) { if (state.errors.isNotEmpty()) haptics.reject() }
    val f = state.form
    var pick by rememberSaveable { mutableStateOf<DateTarget?>(null) }
    val inset = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)

    Column(Modifier.fillMaxSize().imePadding()) {
        LifeTopBar(overline = if (state.isEdit) "Projekt bearbeiten" else "Neues Projekt", title = f.name.ifBlank { "Projekt" }, onBack = onClose)
        if (state.loading) return@Column
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("lifeos-editor"), verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.l)) {
            LifeTextField(
                f.name, { v -> vm.update { it.copy(name = v.take(120)) } }, "Name", placeholder = "z. B. Suzuki Samurai Restauration",
                error = state.errors[ProjectField.NAME], textStyle = Continuum.type.title,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next), modifier = inset,
            )
            Group("Kategorie", null, inset) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.categories.forEach { c -> LifeChoiceChip(c, f.category == c, { vm.update { it.copy(category = if (it.category == c) "" else c) } }) }
                }
                LifeTextField(f.category, { v -> vm.update { it.copy(category = v.take(40)) } }, "Eigene Kategorie", placeholder = "optional")
            }
            Group("Status", null, inset) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectStatus.entries.forEach { st -> LifeChoiceChip(ProjectsText.status(st), f.status == st, { vm.update { it.copy(status = st) } }) }
                }
            }
            Group("Zeitraum", state.errors[ProjectField.DATES], inset) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeChoiceChip("Start " + LifeFormat.numericDate(f.startDate), true, { pick = DateTarget.START }, glyph = Glyph.CALENDAR)
                    LifeChoiceChip(f.targetDate?.let { "Ziel " + LifeFormat.numericDate(it) } ?: "Zieltermin …", f.targetDate != null, { pick = DateTarget.TARGET }, glyph = Glyph.CALENDAR)
                    if (f.targetDate != null) LifeIconButton(Glyph.CLOSE, "Zieltermin entfernen", { vm.update { it.copy(targetDate = null) } })
                }
            }
            Group("Fortschritt", state.errors[ProjectField.PROGRESS], inset) {
                LifeSegmented(
                    listOf(ProgressMode.TASKS to "Aufgaben", ProgressMode.MILESTONES to "Meilensteine", ProgressMode.MANUAL to "Manuell"),
                    f.mode, { m -> vm.update { it.copy(mode = m) } },
                )
                Text(
                    when (f.mode) {
                        ProgressMode.TASKS -> "Anteil erledigter Projektaufgaben – automatisch und nachvollziehbar."
                        ProgressMode.MILESTONES -> "Anteil erreichter Meilensteine."
                        ProgressMode.MANUAL -> "Du legst den Stand bewusst fest."
                    },
                    style = Continuum.type.small,
                )
                if (f.mode == ProgressMode.MANUAL) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LifeLine(progress = f.manualSteps / 20f, meaning = LineMeaning.PROGRESS, modifier = Modifier.weight(1f))
                        LifeStepper(f.manualSteps, { n -> vm.update { it.copy(manualSteps = n) } }, 0..20, "Fortschritt", format = { "${it * 5} %" })
                    }
                }
            }
            LifeTextField(
                f.budgetText, { v -> vm.update { it.copy(budgetText = v.take(16)) } }, "Budget in € (optional)", placeholder = "0,00",
                error = state.errors[ProjectField.BUDGET], textStyle = Continuum.type.figuresMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = inset,
            )
            LifeTextField(f.description, { v -> vm.update { it.copy(description = v.take(2000)) } }, "Beschreibung", placeholder = "Worum geht es?", singleLine = false, modifier = inset)
            LifeTextField(f.notes, { v -> vm.update { it.copy(notes = v.take(8000)) } }, "Notizen", placeholder = "Teilenummern, Maße, Ideen …", singleLine = false, modifier = inset)
            state.errors[ProjectField.GENERAL]?.let { Text(it, style = Continuum.type.body, color = ContinuumColors.Alert, modifier = inset) }
            Spacer(Modifier.height(ContinuumSpacing.l))
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter, top = 8.dp, bottom = 12.dp)) {
            LifeButton(if (state.saving) "Speichert …" else "Speichern", vm::save, enabled = !state.saving, glyph = Glyph.CHECK, fill = true)
        }
    }
    LifeDatePickerOverlay(pick == DateTarget.START, f.startDate, state.today, { pick = null }, { d -> vm.update { it.copy(startDate = d) } }, "Projektstart")
    LifeDatePickerOverlay(pick == DateTarget.TARGET, f.targetDate ?: f.startDate.plusMonths(1), state.today, { pick = null }, { d -> vm.update { it.copy(targetDate = d) } }, "Zieltermin")
}

@Composable
private fun Group(title: String, error: String?, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = Continuum.type.label, color = if (error != null) ContinuumColors.Alert else ContinuumColors.Muted)
        content()
        if (error != null) Text(error, style = Continuum.type.small, color = ContinuumColors.Alert)
    }
}
