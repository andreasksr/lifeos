package app.lifeos.feature.ledger.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import app.lifeos.core.designsystem.component.LifeKeypad
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeSegmented
import app.lifeos.core.designsystem.component.LifeTag
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeToastMessage
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.ledger.presentation.EditorField

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorRoute(viewModel: TransactionEditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val links by viewModel.links.collectAsStateWithLifecycle(initialValue = emptyList())
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    LaunchedEffect(state.outcome) {
        when (val o = state.outcome) {
            is EditorOutcome.Saved -> {
                haptics.confirm()
                signal.confirm(o.text)
                onClose()
            }
            is EditorOutcome.Deleted -> {
                haptics.confirm()
                signal.confirm("Buchung gelöscht", "Rückgängig") {
                    viewModel.restore(o.snapshot) { ok ->
                        if (ok) signal.confirm("Buchung wiederhergestellt")
                        else signal.toast(LifeToastMessage("Wiederherstellen nicht möglich.", tone = NodeTone.ALERT))
                    }
                }
                onClose()
            }
            null -> Unit
        }
    }
    LaunchedEffect(state.errors) { if (state.errors.isNotEmpty()) haptics.reject() }

    val form = state.form
    var keypad by rememberSaveable { mutableStateOf(!state.isEdit) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val inset = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)

    Column(Modifier.fillMaxSize().imePadding()) {
        LifeTopBar(
            overline = if (state.isEdit) "Buchung bearbeiten" else "Neue Buchung",
            title = LifeFormat.kindLabel(form.kind),
            onBack = onClose,
            actions = {
                if (state.isEdit) LifeIconButton(Glyph.TRASH, "Buchung löschen", viewModel::requestDelete, tint = ContinuumColors.Alert)
            },
        )
        if (state.loading) return@Column

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.l),
        ) {
            if (!form.kind.isCorrection && form.lockedSplits == null) {
                LifeSegmented(
                    options = listOf(
                        TransactionKind.EXPENSE to "Ausgabe",
                        TransactionKind.INCOME to "Einnahme",
                        TransactionKind.REFUND to "Erstattung",
                        TransactionKind.TRANSFER to "Umbuchung",
                    ),
                    selected = form.kind,
                    onSelect = viewModel::onKind,
                    modifier = inset,
                )
            } else if (form.kind.isCorrection) {
                Row(inset) { LifeTag("Kontostand-Korrektur – zählt nicht als Einnahme oder Ausgabe", NodeTone.NEUTRAL) }
            }

            // ── Betrag ──
            Column(inset) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .semantics { contentDescription = "Betrag ${form.amountText.ifEmpty { "leer" }} Euro, antippen zum Bearbeiten" }
                        .clickable(enabled = form.lockedSplits == null, role = Role.Button) { keypad = !keypad },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        form.amountText.ifEmpty { "0" },
                        style = Continuum.type.figuresHuge,
                        color = if (form.amountText.isEmpty()) ContinuumColors.Faint else ContinuumColors.Frost,
                        modifier = Modifier.weight(1f),
                    )
                    Text("€", style = Continuum.type.figuresLarge, color = ContinuumColors.Muted)
                }
                LifeLine(
                    meaning = when (form.kind) {
                        TransactionKind.INCOME, TransactionKind.REFUND -> LineMeaning.INFLOW
                        TransactionKind.EXPENSE -> LineMeaning.OUTFLOW
                        else -> LineMeaning.TIME
                    },
                )
                state.errors[EditorField.AMOUNT]?.let { Text(it, style = Continuum.type.small, color = ContinuumColors.Alert) }
                form.lockedSplits?.let {
                    Text(
                        "Diese Buchung ist auf ${it.size} Kategorien aufgeteilt. Betrag und Aufteilung bleiben unverändert.",
                        style = Continuum.type.small,
                    )
                }
            }
            if (keypad && form.lockedSplits == null) {
                LifeKeypad(viewModel::onDigit, viewModel::onComma, viewModel::onBackspace, modifier = inset)
            }

            Section(if (form.kind == TransactionKind.TRANSFER) "Von Konto" else "Konto", state.errors[EditorField.ACCOUNT], inset) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.accounts.forEach { a -> LifeChoiceChip(a.name, a.id == form.accountId, { viewModel.onAccount(a.id) }) }
                }
            }
            if (form.kind == TransactionKind.TRANSFER) {
                Section("Auf Konto", state.errors[EditorField.COUNTER_ACCOUNT], inset) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.accounts.filter { it.id != form.accountId }.forEach { a ->
                            LifeChoiceChip(a.name, a.id == form.counterAccountId, { viewModel.onCounterAccount(a.id) })
                        }
                    }
                    if (state.accounts.size < 2) Text("Für Umbuchungen wird ein zweites Konto benötigt (Finanzen → Konten).", style = Continuum.type.small)
                }
            } else if (form.kind.requiresSplits && form.lockedSplits == null) {
                Section(
                    if (form.kind == TransactionKind.REFUND) "Erstattet für Kategorie" else "Kategorie",
                    state.errors[EditorField.CATEGORY], inset,
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.rankedCategories.forEach { c ->
                            LifeChoiceChip(c.name, c.id == form.categoryId, { viewModel.onCategory(c.id) })
                        }
                    }
                }
            }

            Section("Datum · ${LifeFormat.numericDate(form.date)}", state.errors[EditorField.DATE], inset) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeChoiceChip("Heute", form.date == state.today, { viewModel.onDate(state.today) })
                    LifeChoiceChip("Gestern", form.date == state.today.minusDays(1), { viewModel.onDate(state.today.minusDays(1)) })
                    val other = form.date != state.today && form.date != state.today.minusDays(1)
                    LifeChoiceChip(if (other) LifeFormat.dayTitle(form.date, state.today) else "Anderes Datum", other, { pickDate = true }, glyph = Glyph.CALENDAR)
                }
            }

            LifeTextField(
                value = form.payee,
                onValueChange = viewModel::onPayee,
                label = if (form.kind == TransactionKind.INCOME) "Von / Zweck" else "Händler / Zweck",
                placeholder = "z. B. Wochenmarkt",
                error = state.errors[EditorField.PAYEE],
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = inset,
            )
            LifeTextField(
                value = form.note,
                onValueChange = viewModel::onNote,
                label = "Notiz",
                placeholder = "optional",
                error = state.errors[EditorField.NOTE],
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = inset,
            )

            if (links.isNotEmpty()) {
                Section("Verknüpft mit", null, inset) {
                    links.forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LifeNode(shape = NodeShape.LINKED, tone = NodeTone.ACTIVE, size = 12.dp)
                            Text(l.node.title, style = Continuum.type.body, modifier = Modifier.weight(1f))
                            Text(LifeFormat.typeLabel(l.node.type), style = Continuum.type.small)
                        }
                    }
                }
            }

            state.errors[EditorField.GENERAL]?.let { Text(it, style = Continuum.type.body, color = ContinuumColors.Alert, modifier = inset) }
            Spacer(Modifier.height(ContinuumSpacing.l))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter, top = 8.dp, bottom = 12.dp),
        ) {
            LifeButton(
                if (state.saving) "Speichert …" else "Speichern",
                viewModel::save,
                enabled = !state.saving,
                glyph = Glyph.CHECK,
                fill = true,
            )
        }
    }

    LifeDatePickerOverlay(
        visible = pickDate,
        initial = form.date,
        today = state.today,
        onDismiss = { pickDate = false },
        onPick = viewModel::onDate,
        title = "Buchungsdatum",
    )

    LifeOverlay(visible = state.confirmDelete, onDismiss = viewModel::cancelDelete, title = "Buchung löschen?", overline = "Sicherheitsabfrage") {
        Text(
            "Die Buchung wird aus allen Ansichten entfernt (Haushaltskasse, Projekte, Fahrzeuge). " +
                "Direkt danach kannst du das Löschen noch rückgängig machen.",
            style = Continuum.type.bodyMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeButton("Abbrechen", viewModel::cancelDelete, style = LifeButtonStyle.SECONDARY)
            Spacer(Modifier.weight(1f))
            LifeButton("Löschen", viewModel::confirmDelete, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
        }
    }
}


@Composable
private fun Section(title: String, error: String?, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = Continuum.type.label, color = if (error != null) ContinuumColors.Alert else ContinuumColors.Muted)
        content()
        if (error != null) Text(error, style = Continuum.type.small, color = ContinuumColors.Alert)
    }
}
