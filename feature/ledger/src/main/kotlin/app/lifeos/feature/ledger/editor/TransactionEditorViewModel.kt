package app.lifeos.feature.ledger.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.ledger.CategorySuggestions
import app.lifeos.core.domain.ledger.DeletedTransaction
import app.lifeos.core.domain.ledger.LedgerResult
import app.lifeos.core.domain.ports.LinkedNode
import app.lifeos.core.model.Account
import app.lifeos.core.model.AmountInput
import app.lifeos.core.model.Category
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.ledger.LedgerDependencies
import app.lifeos.feature.ledger.presentation.EditorField
import app.lifeos.feature.ledger.presentation.EditorForm
import app.lifeos.feature.ledger.presentation.EditorFormLogic
import app.lifeos.feature.ledger.presentation.FormResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface EditorOutcome {
    data class Saved(val text: String) : EditorOutcome
    data class Deleted(val snapshot: DeletedTransaction) : EditorOutcome
}

data class EditorUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val form: EditorForm,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Kategorien der aktuellen Richtung, nach Häufigkeit/Aktualität sortiert. */
    val rankedCategories: List<Category> = emptyList(),
    val errors: Map<EditorField, String> = emptyMap(),
    val saving: Boolean = false,
    val confirmDelete: Boolean = false,
    val outcome: EditorOutcome? = null,
    val today: LocalDate,
)

class TransactionEditorViewModel(
    private val deps: LedgerDependencies,
    private val editId: NodeId?,
    initialKind: TransactionKind = TransactionKind.EXPENSE,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditorUiState(isEdit = editId != null, form = EditorForm(date = deps.clock.today(), kind = initialKind), today = deps.clock.today()),
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    /** Verknüpfte Objekte (Projekte, Fahrzeuge …) – dieselbe Buchung, nur verknüpft. */
    val links: Flow<List<LinkedNode>> = editId?.let { deps.links.observeNeighbors(it) } ?: emptyFlow()

    /** Mögliche Zuordnungen: Projekte, Fahrzeuge, Inventar. */
    val assignTargets: Flow<List<app.lifeos.core.model.NodeSummary>> =
        deps.links.observeNodes(setOf(app.lifeos.core.model.NodeType.PROJECT, app.lifeos.core.model.NodeType.VEHICLE, app.lifeos.core.model.NodeType.ASSET))

    /** Bei neuen Buchungen: gewählte Zuordnungen, die nach dem Speichern verknüpft werden. */
    private val _pendingTargets = MutableStateFlow<Set<NodeId>>(emptySet())
    val pendingTargets: StateFlow<Set<NodeId>> = _pendingTargets.asStateFlow()

    /** Ordnet zu oder entfernt die Zuordnung. Bestehende Buchung: sofort; neue: nach dem Speichern. */
    fun toggleTarget(target: app.lifeos.core.model.NodeSummary, currentlyLinked: Boolean) {
        val id = editId
        if (id == null) {
            _pendingTargets.update { if (target.id in it) it - target.id else it + target.id }
            return
        }
        viewModelScope.launch {
            try {
                if (currentlyLinked) deps.unlinkNodes(id, target.id) else deps.linkNodes(id, target.id, relationFor(target.type))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(errors = mapOf(EditorField.GENERAL to "Zuordnung fehlgeschlagen.")) }
            }
        }
    }

    private fun relationFor(type: app.lifeos.core.model.NodeType) =
        if (type == app.lifeos.core.model.NodeType.PROJECT) app.lifeos.core.model.LinkRelation.PART_OF else app.lifeos.core.model.LinkRelation.CONCERNS

    init {
        viewModelScope.launch {
            try {
                val accounts = deps.repository.accounts()
                val categories = deps.repository.categories()
                val existing = editId?.let { deps.repository.transaction(it) }
                val form = existing?.let(EditorFormLogic::fromTransaction)
                    ?: EditorFormLogic.initial(accounts, deps.clock.today()).copy(kind = initialKind)
                val all = deps.repository.observeAllTransactions().first()
                _state.update {
                    it.copy(
                        loading = false,
                        form = form,
                        accounts = accounts.filter { a -> !a.archived || a.id == form.accountId || a.id == form.counterAccountId },
                        categories = categories,
                        rankedCategories = rank(categories, all, form.kind),
                        errors = if (editId != null && existing == null) mapOf(EditorField.GENERAL to "Diese Buchung existiert nicht mehr.") else emptyMap(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, errors = mapOf(EditorField.GENERAL to "Daten konnten nicht geladen werden.")) }
            }
        }
    }

    private var allTransactionsCache: List<app.lifeos.core.model.LedgerTransaction> = emptyList()

    private fun rank(categories: List<Category>, all: List<app.lifeos.core.model.LedgerTransaction>, kind: TransactionKind): List<Category> {
        allTransactionsCache = all
        val direction = kind.categoryDirection ?: return emptyList()
        return CategorySuggestions.ranked(categories, all, direction, deps.clock.today())
    }

    private fun edit(field: EditorField, change: (EditorForm) -> EditorForm) {
        _state.update { it.copy(form = change(it.form), errors = it.errors - field - EditorField.GENERAL) }
    }

    fun onKind(kind: TransactionKind) = _state.update {
        val form = EditorFormLogic.changeKind(it.form, kind, it.categories)
        it.copy(
            form = form,
            rankedCategories = rank(it.categories, allTransactionsCache, form.kind),
            errors = it.errors - EditorField.CATEGORY - EditorField.COUNTER_ACCOUNT,
        )
    }

    fun onDigit(d: Char) = edit(EditorField.AMOUNT) { it.copy(amountText = AmountInput.digit(it.amountText, d)) }
    fun onComma() = edit(EditorField.AMOUNT) { it.copy(amountText = AmountInput.comma(it.amountText)) }
    fun onBackspace() = edit(EditorField.AMOUNT) { it.copy(amountText = AmountInput.backspace(it.amountText)) }
    fun onAccount(id: NodeId) = edit(EditorField.ACCOUNT) { it.copy(accountId = id) }
    fun onCounterAccount(id: NodeId) = edit(EditorField.COUNTER_ACCOUNT) { it.copy(counterAccountId = id) }
    fun onCategory(id: NodeId) = edit(EditorField.CATEGORY) { it.copy(categoryId = id) }
    fun onDate(date: LocalDate) = edit(EditorField.DATE) { it.copy(date = date) }
    fun onPayee(text: String) = edit(EditorField.PAYEE) { it.copy(payee = text.take(500)) }
    fun onNote(text: String) = edit(EditorField.NOTE) { it.copy(note = text.take(500)) }

    fun save() {
        val current = _state.value
        if (current.saving || current.loading) return
        when (val result = EditorFormLogic.toDraft(current.form, editId)) {
            is FormResult.Invalid -> _state.update { it.copy(errors = result.errors) }
            is FormResult.Valid -> {
                _state.update { it.copy(saving = true) }
                viewModelScope.launch {
                    try {
                        when (val saved = deps.record(result.draft)) {
                            is LedgerResult.Success -> {
                                if (editId == null && _pendingTargets.value.isNotEmpty()) {
                                    val targets = deps.links.observeNodes(emptySet()).first().filter { it.id in _pendingTargets.value }
                                    targets.forEach { t -> deps.linkNodes(saved.value, t.id, relationFor(t.type)) }
                                }
                                _state.update {
                                val label = app.lifeos.core.model.LifeFormat.kindLabel(result.draft.kind)
                                val amount = app.lifeos.core.model.MoneyText.format(result.draft.amountMinor)
                                it.copy(saving = false, outcome = EditorOutcome.Saved(if (current.isEdit) "$label geändert · $amount" else "$label gespeichert · $amount"))
                                }
                            }
                            is LedgerResult.Invalid -> _state.update {
                                it.copy(saving = false, errors = EditorFormLogic.domainMessages(saved.errors))
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.update {
                            it.copy(saving = false, errors = mapOf(EditorField.GENERAL to "Speichern fehlgeschlagen. Es wurde nichts verändert."))
                        }
                    }
                }
            }
        }
    }

    fun requestDelete() = _state.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _state.update { it.copy(confirmDelete = false) }

    fun confirmDelete() {
        val id = editId ?: return
        viewModelScope.launch {
            try {
                when (val r = deps.delete(id)) {
                    is LedgerResult.Success -> _state.update { it.copy(confirmDelete = false, outcome = EditorOutcome.Deleted(r.value)) }
                    is LedgerResult.Invalid -> _state.update { it.copy(confirmDelete = false, errors = EditorFormLogic.domainMessages(r.errors)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(confirmDelete = false, errors = mapOf(EditorField.GENERAL to "Löschen fehlgeschlagen. Es wurde nichts verändert."))
                }
            }
        }
    }

    /** Läuft im langlebigen Scope: das Formular ist zu diesem Zeitpunkt meist schon geschlossen. */
    fun restore(snapshot: DeletedTransaction, onDone: (Boolean) -> Unit) {
        deps.background.launch {
            val ok = try {
                deps.restore(snapshot) is LedgerResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            onDone(ok)
        }
    }
}
