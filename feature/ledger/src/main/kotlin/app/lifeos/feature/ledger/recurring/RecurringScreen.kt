package app.lifeos.feature.ledger.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeMoneyMetric
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeSegmented
import app.lifeos.core.designsystem.component.LifeTag
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTimelineRow
import app.lifeos.core.designsystem.component.LifeToastMessage
import app.lifeos.core.designsystem.component.LifeToggle
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.domain.ledger.RecurringSchedule
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.RecurringPayment
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.ledger.presentation.EditorField
import app.lifeos.feature.ledger.ui.PendingLine

@Composable
fun RecurringRoute(viewModel: RecurringViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { (text, ok) ->
            if (ok) signal.confirm(text) else signal.toast(LifeToastMessage(text, tone = NodeTone.ALERT))
        }
    }
    val s = state ?: return
    val inset = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item(key = "top") {
            LifeTopBar(
                overline = "Finanzen",
                title = "Wiederkehrende Zahlungen",
                onBack = onBack,
                actions = { LifeIconButton(Glyph.PLUS, "Neue wiederkehrende Zahlung", viewModel::create, tint = ContinuumColors.Cyan) },
            )
        }
        item(key = "sum") {
            SpineSection("Pro Monat", index = 0, shape = NodeShape.SQUARE, tone = NodeTone.ACTIVE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LifeMoneyMetric("Fixkosten", s.fixedMonthlyMinor, Modifier.weight(1f), tone = NodeTone.OUTFLOW, caption = "Monatsäquivalent")
                    LifeMoneyMetric("Feste Einnahmen", s.incomeMonthlyMinor, Modifier.weight(1f), tone = NodeTone.INFLOW)
                }
                Text(
                    "Fälligkeiten werden berechnet und angezeigt. Gebucht wird erst, wenn du „Buchen“ wählst – so bleibt der Kontostand echt.",
                    style = Continuum.type.small,
                )
            }
        }
        val pending = s.lines.flatMap { it.pending }.sortedBy { it.dueDate }
        if (pending.isNotEmpty()) {
            item(key = "pending") {
                SpineSection("Offen", index = 1, shape = NodeShape.SQUARE, tone = NodeTone.WARN, fullBleed = true) {
                    pending.forEach { o -> PendingLine(o, s.today, { viewModel.book(o) }, { viewModel.skip(o) }) }
                }
            }
        }
        item(key = "plansHeader") {
            SpineSection("Alle Pläne", index = 2, shape = NodeShape.RING, fullBleed = true) {
                if (s.lines.isEmpty()) {
                    LifeEmpty(
                        "Noch keine wiederkehrenden Zahlungen. Lege Miete, Versicherungen, Handyvertrag oder Abos an.",
                        modifier = inset, actionLabel = "Erste Zahlung anlegen", onAction = viewModel::create, glyph = Glyph.REPEAT,
                    )
                }
            }
        }
        items(s.lines, key = { it.payment.id.value }) { line -> PlanRow(line, s.today) { viewModel.edit(line.payment) } }
    }

    val e = editor
    RecurringEditorOverlay(
        visible = e != null,
        state = e,
        accounts = s.accounts,
        categories = s.categories,
        today = s.today,
        vm = viewModel,
    )
}

@Composable
private fun PlanRow(line: PlanLine, today: java.time.LocalDate, onClick: () -> Unit) {
    val p = line.payment
    LifeTimelineRow(
        nodeShape = NodeShape.SQUARE,
        nodeTone = when {
            !p.active -> NodeTone.NEUTRAL
            line.pending.any { it.dueDate.isBefore(today) } -> NodeTone.ALERT
            p.kind == TransactionKind.INCOME -> NodeTone.INFLOW
            else -> NodeTone.OUTFLOW
        },
        onClick = onClick,
        onClickLabel = "Bearbeiten",
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.name, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (!p.active) LifeTag("Pausiert")
                if (p.isFixedCost && p.kind == TransactionKind.EXPENSE) LifeTag("Fix", NodeTone.ACTIVE)
            }
            Text(
                "${RecurringSchedule.intervalLabel(p.intervalMonths)} · ${line.categoryName}" +
                    (line.next?.let { " · nächste ${LifeFormat.numericDate(it)}" } ?: ""),
                style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            MoneyText.format(if (p.kind == TransactionKind.INCOME) p.amount else -p.amount, showPlus = p.kind == TransactionKind.INCOME),
            style = Continuum.type.figures,
            color = if (p.kind == TransactionKind.INCOME) ContinuumColors.Mint else ContinuumColors.Frost,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringEditorOverlay(
    visible: Boolean,
    state: RecurringEditorState?,
    accounts: List<app.lifeos.core.model.Account>,
    categories: List<app.lifeos.core.model.Category>,
    today: java.time.LocalDate,
    vm: RecurringViewModel,
) {
    var pickAnchor by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    LifeOverlay(
        visible = visible,
        onDismiss = vm::close,
        title = if (state?.form?.id == null) "Neue Zahlung" else "Zahlung bearbeiten",
        overline = "Wiederkehrend",
        fullHeight = true,
    ) {
        val st = state ?: return@LifeOverlay
        val f = st.form
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LifeSegmented(
                listOf(TransactionKind.EXPENSE to "Ausgabe", TransactionKind.INCOME to "Einnahme"),
                f.kind,
                { k -> vm.update { it.copy(kind = k, categoryId = null) } },
            )
            LifeTextField(f.name, { v -> vm.update { it.copy(name = v.take(80)) } }, "Name", placeholder = "z. B. Miete, Handyvertrag", error = st.errors[EditorField.PAYEE])
            LifeTextField(
                f.amountText, { v -> vm.update { it.copy(amountText = v.take(16)) } }, "Betrag in €", placeholder = "0,00",
                error = st.errors[EditorField.AMOUNT], textStyle = Continuum.type.figuresMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Label("Intervall")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurringPayment.ALLOWED_INTERVALS.forEach { m ->
                    LifeChoiceChip(RecurringSchedule.intervalLabel(m), f.intervalMonths == m, { vm.update { it.copy(intervalMonths = m) } })
                }
            }
            Label("Erste Fälligkeit · ${LifeFormat.numericDate(f.anchorDate)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeChoiceChip("Datum wählen", false, { pickAnchor = true }, glyph = Glyph.CALENDAR)
                LifeChoiceChip(f.endDate?.let { "Endet ${LifeFormat.numericDate(it)}" } ?: "Ohne Ende", f.endDate != null, { pickEnd = true })
                if (f.endDate != null) LifeIconButton(Glyph.CLOSE, "Enddatum entfernen", { vm.update { it.copy(endDate = null) } })
            }
            st.errors[EditorField.DATE]?.let { Text(it, style = Continuum.type.small, color = ContinuumColors.Alert) }
            Label("Konto", st.errors[EditorField.ACCOUNT])
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a -> LifeChoiceChip(a.name, f.accountId == a.id, { vm.update { it.copy(accountId = a.id) } }) }
            }
            Label("Kategorie", st.errors[EditorField.CATEGORY])
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.filter { it.direction == f.kind.categoryDirection }.forEach { c ->
                    LifeChoiceChip(c.name, f.categoryId == c.id, { vm.update { it.copy(categoryId = c.id) } })
                }
            }
            LifeTextField(f.payee, { v -> vm.update { it.copy(payee = v.take(200)) } }, "Empfänger (optional)", placeholder = "z. B. Vermieter")
            if (f.kind == TransactionKind.EXPENSE) {
                LifeToggle(f.isFixedCost, { v -> vm.update { it.copy(isFixedCost = v) } }, "Zählt als Fixkosten", description = "Für Fixkostenquote und Monatsplanung")
            }
            LifeToggle(f.active, { v -> vm.update { it.copy(active = v) } }, "Aktiv", description = "Pausierte Pläne erzeugen keine offenen Fälligkeiten")
            st.errors[EditorField.GENERAL]?.let { Text(it, style = Continuum.type.body, color = ContinuumColors.Alert) }
            if (st.confirmDelete) {
                Text("Plan wirklich löschen? Bereits gebuchte Zahlungen bleiben als normale Buchungen erhalten.", style = Continuum.type.bodyMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeButton("Abbrechen", vm::cancelDelete, style = LifeButtonStyle.SECONDARY)
                    LifeButton("Löschen", vm::confirmDelete, style = LifeButtonStyle.DANGER)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (f.id != null && !st.confirmDelete) LifeButton("Löschen", vm::requestDelete, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
            Spacer(Modifier.weight(1f))
            LifeButton(if (st.saving) "Speichert …" else "Speichern", vm::save, enabled = !st.saving, glyph = Glyph.CHECK)
        }
        LifeDatePickerOverlay(pickAnchor, f.anchorDate, today, { pickAnchor = false }, { d -> vm.update { it.copy(anchorDate = d) } }, "Erste Fälligkeit")
        LifeDatePickerOverlay(pickEnd, f.endDate ?: f.anchorDate.plusYears(1), today, { pickEnd = false }, { d -> vm.update { it.copy(endDate = d) } }, "Letzte Fälligkeit")
    }
}

@Composable
private fun Label(text: String, error: String? = null) {
    Column {
        Text(text.uppercase(), style = Continuum.type.label, color = if (error != null) ContinuumColors.Alert else ContinuumColors.Muted)
        if (error != null) Text(error, style = Continuum.type.small, color = ContinuumColors.Alert)
    }
}
