package app.lifeos.feature.ledger.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeMoneyMetric
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTimelineRow
import app.lifeos.core.designsystem.component.LifeToastMessage
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.domain.ledger.LedgerMath
import app.lifeos.core.domain.ledger.LedgerMonthSnapshot
import app.lifeos.core.domain.ledger.LedgerResult
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.feature.ledger.LedgerDependencies
import app.lifeos.feature.ledger.presentation.EditorFormLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Was gerade bearbeitet wird: das Gesamtbudget (`categoryId == null`) oder eine Kategorie. */
data class BudgetEdit(val categoryId: NodeId?, val name: String, val text: String, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModel(private val deps: LedgerDependencies) : ViewModel() {
    val month = MutableStateFlow(YearMonth.from(deps.clock.today()))
    val snapshot: StateFlow<LedgerMonthSnapshot?> = month.flatMapLatest { deps.observeMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val edit = MutableStateFlow<BudgetEdit?>(null)
    private val _events = Channel<Pair<String, Boolean>>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun previous() { month.value = month.value.minusMonths(1) }
    fun next() { month.value = month.value.plusMonths(1) }

    fun start(categoryId: NodeId?, name: String, currentMinor: Long?) {
        edit.value = BudgetEdit(categoryId, name, currentMinor?.let { MoneyText.toEditText(it) }.orEmpty())
    }

    fun text(v: String) { edit.value = edit.value?.copy(text = v.take(16), error = null) }
    fun cancel() { edit.value = null }

    fun save() {
        val e = edit.value ?: return
        val amount = when (val p = MoneyText.parse(e.text)) {
            is MoneyText.ParseResult.Success -> p.minor
            is MoneyText.ParseResult.Failure -> { edit.value = e.copy(error = EditorFormLogic.parseMessage(p.reason)); return }
        }
        viewModelScope.launch {
            try {
                val r = if (e.categoryId == null) deps.setTotalBudget(month.value, amount)
                else deps.setBudget(e.categoryId, month.value, amount, snapshot.value?.budgets.orEmpty())
                when (r) {
                    is LedgerResult.Success -> {
                        edit.value = null
                        _events.send("Budget ${e.name} ab ${LifeFormat.monthTitle(month.value)}: ${MoneyText.format(amount)}" to true)
                    }
                    is LedgerResult.Invalid -> edit.value = e.copy(error = EditorFormLogic.domainMessage(r.errors.first()).second)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                edit.value = e.copy(error = "Speichern fehlgeschlagen.")
            }
        }
    }

    /** Entfernt das ab diesem Monat gesetzte Budget (frühere Budgets gelten dann weiter). */
    fun remove() {
        val e = edit.value ?: return
        viewModelScope.launch {
            try {
                if (e.categoryId == null) deps.removeTotalBudget(month.value) else deps.removeBudget(e.categoryId, month.value)
                edit.value = null
                _events.send("Budget-Änderung ab ${LifeFormat.monthTitle(month.value)} entfernt" to true)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _events.send("Entfernen fehlgeschlagen." to false)
            }
        }
    }
}

@Composable
fun BudgetsRoute(vm: BudgetsViewModel, onBack: () -> Unit) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val edit by vm.edit.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    LaunchedEffect(vm) { vm.events.collect { (t, ok) -> if (ok) signal.confirm(t) else signal.toast(LifeToastMessage(t, tone = NodeTone.ALERT)) } }
    val s = snap ?: return
    val expenseTotals = s.expenseLines.associate { it.category.id to it.totalMinor }
    val effective = LedgerMath.effectiveBudgets(s.budgets, s.month, s.currency)

    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            LifeTopBar(
                overline = "Budgets", title = LifeFormat.monthTitle(s.month), onBack = onBack,
                actions = {
                    LifeIconButton(Glyph.CHEVRON_LEFT, "Vorheriger Monat", vm::previous)
                    LifeIconButton(Glyph.CHEVRON_RIGHT, "Nächster Monat", vm::next)
                },
            )
        }
        item {
            val tb = s.totalBudget
            SpineSection(
                "Monatsbudget", index = 0, shape = NodeShape.RING,
                tone = when { tb == null -> NodeTone.NEUTRAL; tb.isExceeded -> NodeTone.ALERT; else -> NodeTone.ACTIVE },
                onOpen = { vm.start(null, "gesamt", tb?.limitMinor) },
                trailing = { LifeIconButton(Glyph.EDIT, "Monatsbudget bearbeiten", { vm.start(null, "gesamt", tb?.limitMinor) }) },
            ) {
                if (tb == null) {
                    Text("Kein Gesamtbudget für diesen Monat.", style = Continuum.type.bodyMuted)
                    LifeButton("Monatsbudget festlegen", { vm.start(null, "gesamt", null) }, style = LifeButtonStyle.SECONDARY, glyph = Glyph.PLUS)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LifeMoneyMetric("Verbraucht", tb.spentMinor, Modifier.weight(1f), tone = NodeTone.OUTFLOW)
                        LifeMoneyMetric(if (tb.isExceeded) "Überschritten" else "Übrig", if (tb.isExceeded) -tb.remainingMinor else tb.remainingMinor, Modifier.weight(1f), tone = if (tb.isExceeded) NodeTone.ALERT else NodeTone.INFLOW)
                    }
                    LifeLine(progress = tb.usedPermille / 1000f, meaning = LineMeaning.PROGRESS, progressDescription = "${LifeFormat.percent(tb.usedPermille)} verbraucht")
                    Text("${LifeFormat.percent(tb.usedPermille)} von ${MoneyText.format(tb.limitMinor)} · Ausgaben nach Erstattungen", style = Continuum.type.small)
                }
            }
        }
        item {
            SpineSection("Kategorien", index = 1, shape = NodeShape.DIAMOND, fullBleed = true) {}
        }
        items(s.categories.filter { it.direction == CategoryDirection.EXPENSE && !it.archived }, key = { it.id.value }) { c ->
            val budget = effective[c.id]
            val spent = expenseTotals[c.id] ?: 0L
            val permille = budget?.let { LedgerMath.permille(spent, it.amountMinor) }
            LifeTimelineRow(
                nodeShape = NodeShape.DIAMOND,
                nodeTone = when { budget == null -> NodeTone.NEUTRAL; spent > budget.amountMinor -> NodeTone.ALERT; else -> NodeTone.ACTIVE },
                onClick = { vm.start(c.id, c.name, budget?.amountMinor) },
                onClickLabel = "Budget bearbeiten",
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, style = Continuum.type.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (budget != null) "${MoneyText.format(spent)} / ${MoneyText.format(budget.amountMinor)}" else MoneyText.format(spent),
                            style = Continuum.type.figuresSmall,
                            color = if (budget != null && spent > budget.amountMinor) ContinuumColors.Alert else ContinuumColors.Muted,
                        )
                    }
                    if (budget != null && permille != null) {
                        LifeLine(progress = permille / 1000f, meaning = LineMeaning.PROGRESS, progressDescription = "${LifeFormat.percent(permille)} verbraucht")
                        if (budget.validFrom != s.month) Text("gilt seit ${LifeFormat.monthTitle(budget.validFrom)}", style = Continuum.type.small, color = ContinuumColors.Faint)
                    } else {
                        Text("kein Budget", style = Continuum.type.small, color = ContinuumColors.Faint)
                    }
                }
            }
        }
    }

    val e = edit
    LifeOverlay(visible = e != null, onDismiss = vm::cancel, title = e?.let { if (it.categoryId == null) "Monatsbudget" else it.name }, overline = "Budget ab ${LifeFormat.monthTitle(s.month)}") {
        val cur = e ?: return@LifeOverlay
        Text("Gilt ab diesem Monat für alle folgenden Monate, bis du es erneut änderst.", style = Continuum.type.small)
        LifeTextField(
            cur.text, vm::text, "Betrag in €", placeholder = "0,00", error = cur.error,
            textStyle = Continuum.type.figuresMedium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeButton("Ab hier entfernen", vm::remove, style = LifeButtonStyle.GHOST)
            Spacer(Modifier.weight(1f))
            LifeButton("Speichern", vm::save, glyph = Glyph.CHECK)
        }
    }
}
