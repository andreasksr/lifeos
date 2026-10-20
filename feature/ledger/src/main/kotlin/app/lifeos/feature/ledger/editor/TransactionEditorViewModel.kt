package app.lifeos.feature.ledger.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.ledger.LedgerResult
import app.lifeos.core.model.Account
import app.lifeos.core.model.Category
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EditorUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val form: EditorForm,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val errors: Map<EditorField, String> = emptyMap(),
    val saving: Boolean = false,
    val confirmDelete: Boolean = false,
    /** true → Bildschirm schließen. */
    val finished: Boolean = false,
) {
    val visibleCategories: List<Category> get() = EditorFormLogic.categoriesFor(form.kind, categories)
}

class TransactionEditorViewModel(
    private val deps: LedgerDependencies,
    private val editId: NodeId?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditorUiState(isEdit = editId != null, form = EditorForm(date = deps.clock.today())),
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val accounts = deps.repository.accounts()
                val categories = deps.repository.categories()
                val existing = editId?.let { deps.repository.transaction(it) }
                val form = existing?.let(EditorFormLogic::fromTransaction)
                    ?: EditorFormLogic.initial(accounts, deps.clock.today())
                _state.update {
                    it.copy(
                        loading = false,
                        form = form,
                        // Archivierte Konten nur zeigen, wenn die Buchung sie bereits verwendet
                        accounts = accounts.filter { a -> !a.archived || a.id == form.accountId || a.id == form.counterAccountId },
                        categories = categories,
                        errors = if (editId != null && existing == null) {
                            mapOf(EditorField.GENERAL to "Diese Buchung existiert nicht mehr.")
                        } else {
                            emptyMap()
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, errors = mapOf(EditorField.GENERAL to "Daten konnten nicht geladen werden.")) }
            }
        }
    }

    fun today(): LocalDate = deps.clock.today()

    private fun edit(field: EditorField, change: (EditorForm) -> EditorForm) {
        _state.update { it.copy(form = change(it.form), errors = it.errors - field - EditorField.GENERAL) }
    }

    fun onKind(kind: TransactionKind) = _state.update {
        it.copy(
            form = EditorFormLogic.changeKind(it.form, kind, it.categories),
            errors = it.errors - EditorField.CATEGORY - EditorField.COUNTER_ACCOUNT,
        )
    }

    fun onAmount(text: String) = edit(EditorField.AMOUNT) { it.copy(amountText = text.take(20)) }
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
                            is LedgerResult.Success -> _state.update { it.copy(saving = false, finished = true) }
                            is LedgerResult.Invalid -> _state.update {
                                it.copy(saving = false, errors = EditorFormLogic.domainMessages(saved.errors))
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Letzte Verteidigung: DB-Trigger o. Ä. – nichts wurde gespeichert (atomare Transaktion).
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
                deps.delete(id)
                _state.update { it.copy(finished = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(confirmDelete = false, errors = mapOf(EditorField.GENERAL to "Löschen fehlgeschlagen. Es wurde nichts verändert."))
                }
            }
        }
    }
}
