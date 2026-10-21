package app.lifeos.feature.ledger.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeIconButton
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
import app.lifeos.core.domain.ledger.LedgerMonthSnapshot
import app.lifeos.core.domain.ledger.LedgerResult
import app.lifeos.core.model.AccountKind
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.feature.ledger.LedgerDependencies
import app.lifeos.feature.ledger.presentation.EditorFormLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Bearbeitungszustand: Konto oder Kategorie, neu oder bestehend. */
data class StructureEdit(
    val target: Target,
    val name: String = "",
    val archived: Boolean = false,
    val kind: AccountKind = AccountKind.CHECKING,
    val direction: CategoryDirection = CategoryDirection.EXPENSE,
    val amountText: String = "",
    val error: String? = null,
) {
    sealed interface Target {
        data object NewAccount : Target
        data class Account(val id: NodeId, val computedBalanceMinor: Long) : Target
        data object NewCategory : Target
        data class Category(val id: NodeId) : Target
    }
}

class AccountsViewModel(private val deps: LedgerDependencies) : ViewModel() {
    val snapshot: StateFlow<LedgerMonthSnapshot?> = deps.observeMonth(YearMonth.from(deps.clock.today()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val edit = MutableStateFlow<StructureEdit?>(null)
    private val _events = Channel<Pair<String, Boolean>>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun newAccount() { edit.value = StructureEdit(StructureEdit.Target.NewAccount) }
    fun newCategory() { edit.value = StructureEdit(StructureEdit.Target.NewCategory) }
    fun editAccount(id: NodeId) {
        val s = snapshot.value ?: return
        val ab = s.balances.firstOrNull { it.account.id == id } ?: return
        edit.value = StructureEdit(
            StructureEdit.Target.Account(id, ab.balance.minor), name = ab.account.name, archived = ab.account.archived,
            kind = ab.account.kind, amountText = MoneyText.toEditText(ab.balance.minor),
        )
    }
    fun editCategory(id: NodeId) {
        val c = snapshot.value?.categories?.firstOrNull { it.id == id } ?: return
        edit.value = StructureEdit(StructureEdit.Target.Category(id), name = c.name, archived = c.archived, direction = c.direction)
    }
    fun update(change: (StructureEdit) -> StructureEdit) = edit.update { it?.let { e -> change(e).copy(error = null) } }
    fun cancel() { edit.value = null }

    fun save() {
        val e = edit.value ?: return
        viewModelScope.launch {
            try {
                val message: String? = when (val t = e.target) {
                    StructureEdit.Target.NewAccount -> {
                        val opening = if (e.amountText.isBlank()) 0L else when (val p = MoneyText.parse(e.amountText)) {
                            is MoneyText.ParseResult.Success -> p.minor
                            is MoneyText.ParseResult.Failure -> { edit.value = e.copy(error = EditorFormLogic.parseMessage(p.reason)); return@launch }
                        }
                        result(deps.createAccount(e.name, e.kind, opening), e)?.let { "Konto „${e.name.trim()}“ angelegt" }
                    }
                    is StructureEdit.Target.Account -> {
                        val ok = result(deps.updateAccount(t.id, e.name, e.archived), e) ?: return@launch
                        // Kontostand abgleichen, falls ein abweichender tatsächlicher Stand eingegeben wurde
                        val actual = when (val p = MoneyText.parse(e.amountText)) {
                            is MoneyText.ParseResult.Success -> p.minor
                            is MoneyText.ParseResult.Failure -> { edit.value = e.copy(error = EditorFormLogic.parseMessage(p.reason)); return@launch }
                        }
                        if (actual != t.computedBalanceMinor) {
                            result(deps.reconcile(t.id, actual, deps.clock.today()), e) ?: return@launch
                            "Konto gespeichert · Korrektur ${MoneyText.format(actual - t.computedBalanceMinor, showPlus = true)} gebucht"
                        } else {
                            ok.let { "Konto gespeichert" }
                        }
                    }
                    StructureEdit.Target.NewCategory -> result(deps.createCategory(e.name, e.direction), e)?.let { "Kategorie „${e.name.trim()}“ angelegt" }
                    is StructureEdit.Target.Category -> result(deps.updateCategory(t.id, e.name, e.archived), e)?.let { "Kategorie gespeichert" }
                }
                if (message != null) {
                    edit.value = null
                    _events.send(message to true)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                edit.value = e.copy(error = "Speichern fehlgeschlagen. Es wurde nichts verändert.")
            }
        }
    }

    private fun <T> result(r: LedgerResult<T>, e: StructureEdit): T? = when (r) {
        is LedgerResult.Success -> r.value
        is LedgerResult.Invalid -> { edit.value = e.copy(error = EditorFormLogic.domainMessage(r.errors.first()).second); null }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsRoute(vm: AccountsViewModel, onBack: () -> Unit) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val edit by vm.edit.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    LaunchedEffect(vm) { vm.events.collect { (t, ok) -> if (ok) signal.confirm(t) else signal.toast(LifeToastMessage(t, tone = NodeTone.ALERT)) } }
    val s = snap ?: return

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item { LifeTopBar(overline = "Finanzen", title = "Konten & Kategorien", onBack = onBack) }
        item {
            SpineSection("Konten", index = 0, shape = NodeShape.RING, tone = NodeTone.ACTIVE, fullBleed = true,
                trailing = { LifeIconButton(Glyph.PLUS, "Konto anlegen", vm::newAccount, tint = ContinuumColors.Cyan) }) {}
        }
        items(s.balances, key = { "a-" + it.account.id.value }) { ab ->
            LifeTimelineRow(nodeShape = NodeShape.RING, nodeTone = if (ab.account.archived) NodeTone.NEUTRAL else NodeTone.ACTIVE, onClick = { vm.editAccount(ab.account.id) }, onClickLabel = "Konto bearbeiten") {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ab.account.name, style = Continuum.type.body)
                        if (ab.account.archived) LifeTag("Archiviert")
                    }
                    Text(LifeFormat.accountKindLabel(ab.account.kind), style = Continuum.type.small)
                }
                Text(MoneyText.format(ab.balance), style = Continuum.type.figures, color = if (ab.balance.isNegative) ContinuumColors.Alert else ContinuumColors.Frost)
            }
        }
        item {
            SpineSection("Kategorien", index = 1, shape = NodeShape.DIAMOND, fullBleed = true,
                trailing = { LifeIconButton(Glyph.PLUS, "Kategorie anlegen", vm::newCategory, tint = ContinuumColors.Cyan) }) {}
        }
        items(s.categories, key = { "c-" + it.id.value }) { c ->
            LifeTimelineRow(
                nodeShape = NodeShape.DOT,
                nodeTone = if (c.archived) NodeTone.NEUTRAL else if (c.direction == CategoryDirection.INCOME) NodeTone.INFLOW else NodeTone.OUTFLOW,
                onClick = { vm.editCategory(c.id) }, onClickLabel = "Kategorie bearbeiten",
            ) {
                Text(c.name, style = Continuum.type.body, modifier = Modifier.weight(1f), color = if (c.archived) ContinuumColors.Faint else ContinuumColors.Frost)
                Text(if (c.direction == CategoryDirection.INCOME) "Einnahme" else "Ausgabe", style = Continuum.type.small)
            }
        }
    }

    val e = edit
    val title = when (e?.target) {
        StructureEdit.Target.NewAccount -> "Neues Konto"
        is StructureEdit.Target.Account -> "Konto bearbeiten"
        StructureEdit.Target.NewCategory -> "Neue Kategorie"
        is StructureEdit.Target.Category -> "Kategorie bearbeiten"
        null -> null
    }
    LifeOverlay(visible = e != null, onDismiss = vm::cancel, title = title) {
        val cur = e ?: return@LifeOverlay
        LifeTextField(cur.name, { v -> vm.update { it.copy(name = v.take(80)) } }, "Name")
        when (val t = cur.target) {
            StructureEdit.Target.NewAccount -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountKind.entries.forEach { k -> LifeChoiceChip(LifeFormat.accountKindLabel(k), cur.kind == k, { vm.update { it.copy(kind = k) } }) }
                }
                LifeTextField(cur.amountText, { v -> vm.update { it.copy(amountText = v.take(16)) } }, "Anfangsbestand in €", placeholder = "0,00",
                    textStyle = Continuum.type.figuresMedium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            is StructureEdit.Target.Account -> {
                LifeTextField(cur.amountText, { v -> vm.update { it.copy(amountText = v.take(16)) } }, "Tatsächlicher Kontostand in €",
                    textStyle = Continuum.type.figuresMedium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("Berechnet: ${MoneyText.format(t.computedBalanceMinor)}. Weicht der echte Stand ab, wird eine Korrekturbuchung angelegt (zählt nicht als Einnahme/Ausgabe).", style = Continuum.type.small)
                LifeToggle(cur.archived, { v -> vm.update { it.copy(archived = v) } }, "Archiviert", description = "Ausgeblendet bei neuen Buchungen; Saldo zählt weiter")
            }
            StructureEdit.Target.NewCategory -> LifeSegmented(
                listOf(CategoryDirection.EXPENSE to "Ausgabe", CategoryDirection.INCOME to "Einnahme"), cur.direction,
                { d -> vm.update { it.copy(direction = d) } },
            )
            is StructureEdit.Target.Category -> LifeToggle(cur.archived, { v -> vm.update { it.copy(archived = v) } }, "Archiviert", description = "Nicht mehr zur Auswahl; vorhandene Buchungen bleiben")
        }
        cur.error?.let { Text(it, style = Continuum.type.small, color = ContinuumColors.Alert) }
        Row(Modifier.fillMaxWidth()) {
            LifeButton("Abbrechen", vm::cancel, style = LifeButtonStyle.GHOST)
            Spacer(Modifier.weight(1f))
            LifeButton("Speichern", vm::save, glyph = Glyph.CHECK)
        }
    }
}
