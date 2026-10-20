package app.lifeos.feature.ledger.editor

import androidx.compose.foundation.background
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeSegmented
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.ledger.presentation.EditorField
import app.lifeos.feature.ledger.presentation.LedgerFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun TransactionEditorRoute(viewModel: TransactionEditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    TransactionEditorScreen(
        state = state,
        today = remember { viewModel.today() },
        onBack = onClose,
        onKind = viewModel::onKind,
        onAmount = viewModel::onAmount,
        onAccount = { viewModel.onAccount(it) },
        onCounterAccount = { viewModel.onCounterAccount(it) },
        onCategory = { viewModel.onCategory(it) },
        onDate = viewModel::onDate,
        onPayee = viewModel::onPayee,
        onNote = viewModel::onNote,
        onSave = viewModel::save,
        onRequestDelete = viewModel::requestDelete,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    state: EditorUiState,
    today: LocalDate,
    onBack: () -> Unit,
    onKind: (TransactionKind) -> Unit,
    onAmount: (String) -> Unit,
    onAccount: (app.lifeos.core.model.NodeId) -> Unit,
    onCounterAccount: (app.lifeos.core.model.NodeId) -> Unit,
    onCategory: (app.lifeos.core.model.NodeId) -> Unit,
    onDate: (LocalDate) -> Unit,
    onPayee: (String) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val form = state.form
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(ContinuumColors.Obsidian)
            .imePadding(),
    ) {
        LifeTopBar(
            overline = "Haushaltskasse",
            title = if (state.isEdit) "Buchung bearbeiten" else "Buchung erfassen",
            onBack = onBack,
        )
        if (state.loading) return@Column

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ContinuumSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.xl),
        ) {
            if (form.lockedSplits == null) {
                LifeSegmented(
                    options = listOf(
                        TransactionKind.EXPENSE to "Ausgabe",
                        TransactionKind.INCOME to "Einnahme",
                        TransactionKind.TRANSFER to "Umbuchung",
                    ),
                    selected = form.kind,
                    onSelect = onKind,
                )
            }

            LifeTextField(
                value = form.amountText,
                onValueChange = onAmount,
                label = "Betrag in €",
                placeholder = "0,00",
                error = state.errors[EditorField.AMOUNT],
                textStyle = Continuum.type.figuresLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )
            val lockedSplits = form.lockedSplits
            if (lockedSplits != null) {
                Text(
                    "Diese Buchung ist auf ${lockedSplits.size} Kategorien aufgeteilt. Betrag und Aufteilung " +
                        "bleiben unverändert; Datum, Konto und Texte kannst du ändern.",
                    style = Continuum.type.bodyMuted,
                )
            }

            Section(if (form.kind == TransactionKind.TRANSFER) "Von Konto" else "Konto", state.errors[EditorField.ACCOUNT]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.s), verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.s)) {
                    state.accounts.forEach { a ->
                        LifeChoiceChip(a.name, selected = a.id == form.accountId, onClick = { onAccount(a.id) })
                    }
                }
            }

            if (form.kind == TransactionKind.TRANSFER) {
                Section("Auf Konto", state.errors[EditorField.COUNTER_ACCOUNT]) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.s), verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.s)) {
                        state.accounts.filter { it.id != form.accountId }.forEach { a ->
                            LifeChoiceChip(a.name, selected = a.id == form.counterAccountId, onClick = { onCounterAccount(a.id) })
                        }
                    }
                    if (state.accounts.size < 2) {
                        Text("Für Umbuchungen wird ein zweites Konto benötigt (Konten verwalten).", style = Continuum.type.bodyMuted)
                    }
                }
            } else if (form.lockedSplits == null) {
                Section("Kategorie", state.errors[EditorField.CATEGORY]) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.s), verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.s)) {
                        state.visibleCategories.forEach { c ->
                            LifeChoiceChip(c.name, selected = c.id == form.categoryId, onClick = { onCategory(c.id) })
                        }
                    }
                }
            }

            Section("Datum · ${LedgerFormat.numericDate(form.date)}", state.errors[EditorField.DATE]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.s), verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.s)) {
                    LifeChoiceChip("Heute", selected = form.date == today, onClick = { onDate(today) })
                    LifeChoiceChip("Gestern", selected = form.date == today.minusDays(1), onClick = { onDate(today.minusDays(1)) })
                    val other = form.date != today && form.date != today.minusDays(1)
                    LifeChoiceChip(
                        if (other) LedgerFormat.dayTitle(form.date, today) else "Anderes Datum …",
                        selected = other,
                        onClick = { showDatePicker = true },
                    )
                }
            }

            LifeTextField(
                value = form.payee,
                onValueChange = onPayee,
                label = if (form.kind == TransactionKind.INCOME) "Von / Zweck" else "Empfänger / Zweck",
                placeholder = "z. B. Wochenmarkt",
                error = state.errors[EditorField.PAYEE],
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            LifeTextField(
                value = form.note,
                onValueChange = onNote,
                label = "Notiz",
                placeholder = "optional",
                error = state.errors[EditorField.NOTE],
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            state.errors[EditorField.GENERAL]?.let {
                Text(it, style = Continuum.type.body, color = ContinuumColors.Flare)
            }
            Spacer(Modifier.height(ContinuumSpacing.l))
        }

        LifeLine(meaning = LineMeaning.TIME, pulse = state.saving)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = ContinuumSpacing.gutter, vertical = ContinuumSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.m),
        ) {
            if (state.isEdit) {
                if (state.confirmDelete) {
                    LifeButton("Wirklich löschen", onConfirmDelete, style = LifeButtonStyle.DANGER)
                    LifeButton("Abbrechen", onCancelDelete, style = LifeButtonStyle.GHOST)
                } else {
                    LifeButton("Löschen", onRequestDelete, style = LifeButtonStyle.DANGER)
                }
            }
            if (!state.confirmDelete) {
                Spacer(Modifier.weight(1f))
                LifeButton(if (state.saving) "Speichert …" else "Speichern", onSave, enabled = !state.saving)
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                LifeButton("Übernehmen", onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                })
            },
            dismissButton = {
                LifeButton("Abbrechen", onClick = { showDatePicker = false }, style = LifeButtonStyle.GHOST)
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun Section(title: String, error: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.s)) {
        Text(title.uppercase(), style = Continuum.type.label, color = if (error != null) ContinuumColors.Flare else ContinuumColors.IceMuted)
        content()
        if (error != null) Text(error, style = Continuum.type.bodyMuted, color = ContinuumColors.Flare)
    }
}
