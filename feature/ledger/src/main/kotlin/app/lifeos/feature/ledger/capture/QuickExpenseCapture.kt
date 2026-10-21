package app.lifeos.feature.ledger.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeKeypad
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeSegmented
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.domain.ledger.CategorySuggestions
import app.lifeos.core.domain.ledger.LedgerResult
import app.lifeos.core.model.Account
import app.lifeos.core.model.AmountInput
import app.lifeos.core.model.Category
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.ledger.LedgerDependencies
import app.lifeos.feature.ledger.presentation.EditorField
import app.lifeos.feature.ledger.presentation.EditorForm
import app.lifeos.feature.ledger.presentation.EditorFormLogic
import app.lifeos.feature.ledger.presentation.FormResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class QuickCaptureState(
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val amountText: String = "",
    val accounts: List<Account> = emptyList(),
    val accountId: NodeId? = null,
    val categories: List<Category> = emptyList(),
    val allCategories: List<Category> = emptyList(),
    val categoryId: NodeId? = null,
    val date: LocalDate,
    val payee: String = "",
    val errors: Map<EditorField, String> = emptyMap(),
    val saving: Boolean = false,
    val savedText: String? = null,
    val today: LocalDate,
)

/**
 * Schnellerfassung: Betrag (eigene Zifferntastatur) → häufige Kategorie → Speichern.
 * Drei Berührungen genügen für eine typische Ausgabe.
 */
class QuickCaptureViewModel(private val deps: LedgerDependencies) : ViewModel() {
    private val _state = MutableStateFlow(QuickCaptureState(date = deps.clock.today(), today = deps.clock.today()))
    val state: StateFlow<QuickCaptureState> = _state.asStateFlow()
    private var all = emptyList<app.lifeos.core.model.LedgerTransaction>()

    fun reset(kind: TransactionKind = TransactionKind.EXPENSE) {
        viewModelScope.launch {
            val accounts = deps.repository.accounts().filter { !it.archived }
            val categories = deps.repository.categories()
            all = deps.repository.observeAllTransactions().first()
            val today = deps.clock.today()
            _state.value = QuickCaptureState(
                kind = kind,
                accounts = accounts,
                accountId = accounts.firstOrNull()?.id,
                allCategories = categories,
                categories = ranked(categories, kind, today),
                date = today,
                today = today,
            )
        }
    }

    private fun ranked(categories: List<Category>, kind: TransactionKind, today: LocalDate): List<Category> {
        val dir = kind.categoryDirection ?: return emptyList()
        return CategorySuggestions.ranked(categories, all, dir, today)
    }

    fun kind(k: TransactionKind) = _state.update {
        it.copy(kind = k, categoryId = null, categories = ranked(it.allCategories, k, it.today), errors = emptyMap())
    }
    fun digit(d: Char) = _state.update { it.copy(amountText = AmountInput.digit(it.amountText, d), errors = it.errors - EditorField.AMOUNT) }
    fun comma() = _state.update { it.copy(amountText = AmountInput.comma(it.amountText)) }
    fun backspace() = _state.update { it.copy(amountText = AmountInput.backspace(it.amountText)) }
    fun category(id: NodeId) = _state.update { it.copy(categoryId = id, errors = it.errors - EditorField.CATEGORY) }
    fun account(id: NodeId) = _state.update { it.copy(accountId = id, errors = it.errors - EditorField.ACCOUNT) }
    fun date(d: LocalDate) = _state.update { it.copy(date = d) }
    fun payee(p: String) = _state.update { it.copy(payee = p.take(200)) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val form = EditorForm(kind = s.kind, amountText = s.amountText, accountId = s.accountId, categoryId = s.categoryId, date = s.date, payee = s.payee)
        when (val r = EditorFormLogic.toDraft(form, null)) {
            is FormResult.Invalid -> _state.update { it.copy(errors = r.errors) }
            is FormResult.Valid -> {
                _state.update { it.copy(saving = true) }
                viewModelScope.launch {
                    try {
                        when (val res = deps.record(r.draft)) {
                            is LedgerResult.Success -> {
                                val cat = s.categories.firstOrNull { it.id == s.categoryId }?.name.orEmpty()
                                _state.update {
                                    it.copy(saving = false, savedText = "${LifeFormat.kindLabel(s.kind)} gespeichert · ${MoneyText.format(r.draft.amountMinor)} · $cat")
                                }
                            }
                            is LedgerResult.Invalid -> _state.update { it.copy(saving = false, errors = EditorFormLogic.domainMessages(res.errors)) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.update { it.copy(saving = false, errors = mapOf(EditorField.GENERAL to "Speichern fehlgeschlagen. Es wurde nichts verändert.")) }
                    }
                }
            }
        }
    }

    fun consumeSaved() = _state.update { it.copy(savedText = null) }
}

/** Inhalt der Schnellerfassung (wird im LifeOverlay der App angezeigt). */
@Composable
fun ColumnScope.QuickExpenseCapture(vm: QuickCaptureViewModel, onDone: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    LaunchedEffect(s.savedText) {
        s.savedText?.let {
            haptics.confirm()
            signal.confirm(it)
            vm.consumeSaved()
            onDone()
        }
    }
    LaunchedEffect(s.errors) { if (s.errors.isNotEmpty()) haptics.reject() }

    LifeSegmented(listOf(TransactionKind.EXPENSE to "Ausgabe", TransactionKind.INCOME to "Einnahme"), s.kind, vm::kind)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Betrag ${s.amountText.ifEmpty { "0" }} Euro"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            s.amountText.ifEmpty { "0" },
            style = Continuum.type.figuresHuge,
            color = if (s.amountText.isEmpty()) ContinuumColors.Faint else ContinuumColors.Frost,
            modifier = Modifier.weight(1f),
        )
        Text("€", style = Continuum.type.figuresLarge, color = ContinuumColors.Muted)
    }
    LifeLine(meaning = if (s.kind == TransactionKind.INCOME) LineMeaning.INFLOW else LineMeaning.OUTFLOW)
    s.errors[EditorField.AMOUNT]?.let { Text(it, style = Continuum.type.small, color = ContinuumColors.Alert) }

    Text("KATEGORIE", style = Continuum.type.label, color = if (s.errors.containsKey(EditorField.CATEGORY)) ContinuumColors.Alert else ContinuumColors.Muted)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s.categories, key = { it.id.value }) { c -> LifeChoiceChip(c.name, c.id == s.categoryId, { vm.category(c.id) }) }
    }
    if (s.accounts.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.accounts, key = { it.id.value }) { a -> LifeChoiceChip(a.name, a.id == s.accountId, { vm.account(a.id) }, glyph = Glyph.WALLET) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LifeChoiceChip("Heute", s.date == s.today, { vm.date(s.today) })
        LifeChoiceChip("Gestern", s.date == s.today.minusDays(1), { vm.date(s.today.minusDays(1)) })
    }
    LifeTextField(s.payee, vm::payee, "", placeholder = "Händler / Zweck (optional)")
    LifeKeypad(vm::digit, vm::comma, vm::backspace)
    s.errors[EditorField.GENERAL]?.let { Text(it, style = Continuum.type.small, color = ContinuumColors.Alert) }
    Column {
        Spacer(Modifier.heightIn(min = 4.dp))
        LifeButton(if (s.saving) "Speichert …" else "Speichern", vm::save, enabled = !s.saving, glyph = Glyph.CHECK, fill = true)
    }
}
