package app.lifeos.feature.ledger.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.ledger.LedgerResult
import app.lifeos.core.domain.ledger.Occurrence
import app.lifeos.core.domain.ledger.OccurrenceState
import app.lifeos.core.domain.ledger.RecurringSchedule
import app.lifeos.core.model.Account
import app.lifeos.core.model.Category
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.RecurringPayment
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.ledger.LedgerDependencies
import app.lifeos.feature.ledger.presentation.EditorField
import app.lifeos.feature.ledger.presentation.EditorFormLogic
import app.lifeos.feature.ledger.presentation.RecurringForm
import app.lifeos.feature.ledger.presentation.RecurringFormLogic
import app.lifeos.feature.ledger.presentation.RecurringFormResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PlanLine(
    val payment: RecurringPayment,
    val next: LocalDate?,
    val pending: List<Occurrence>,
    val monthlyEquivalentMinor: Long,
    val categoryName: String,
    val accountName: String,
)

data class RecurringUiState(
    val lines: List<PlanLine>,
    val accounts: List<Account>,
    val categories: List<Category>,
    val fixedMonthlyMinor: Long,
    val incomeMonthlyMinor: Long,
    val today: LocalDate,
)

data class RecurringEditorState(
    val form: RecurringForm,
    val errors: Map<EditorField, String> = emptyMap(),
    val saving: Boolean = false,
    val confirmDelete: Boolean = false,
)

class RecurringViewModel(private val deps: LedgerDependencies) : ViewModel() {

    val state: StateFlow<RecurringUiState?> = combine(
        deps.repository.observeRecurring(),
        deps.repository.observeRecurringBookings(),
        deps.repository.observeAccounts(),
        deps.repository.observeCategories(),
    ) { plans, bookings, accounts, categories ->
        val today = deps.clock.today()
        val cat = categories.associateBy { it.id }
        val acc = accounts.associateBy { it.id }
        val lines = plans.map { p ->
            PlanLine(
                payment = p,
                next = if (p.active) RecurringSchedule.nextDue(p, today) else null,
                pending = RecurringSchedule.occurrences(p, bookings, today.minusMonths(3), today.plusDays(30), today)
                    .filter { it.state == OccurrenceState.OPEN || it.state == OccurrenceState.OVERDUE },
                monthlyEquivalentMinor = RecurringSchedule.monthlyEquivalentMinor(p),
                categoryName = cat[p.categoryId]?.name ?: "?",
                accountName = acc[p.accountId]?.name ?: "?",
            )
        }.sortedWith(compareBy<PlanLine> { !it.payment.active }.thenBy { it.next ?: LocalDate.MAX })
        RecurringUiState(
            lines = lines,
            accounts = accounts.filter { !it.archived },
            categories = categories.filter { !it.archived },
            fixedMonthlyMinor = plans.filter { it.active && it.isFixedCost && it.kind == TransactionKind.EXPENSE }.sumOf { RecurringSchedule.monthlyEquivalentMinor(it) },
            incomeMonthlyMinor = plans.filter { it.active && it.kind == TransactionKind.INCOME }.sumOf { RecurringSchedule.monthlyEquivalentMinor(it) },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _editor = MutableStateFlow<RecurringEditorState?>(null)
    val editor: StateFlow<RecurringEditorState?> = _editor.asStateFlow()

    private val _events = Channel<Pair<String, Boolean>>(Channel.BUFFERED)
    val events: Flow<Pair<String, Boolean>> = _events.receiveAsFlow()

    fun create() {
        val s = state.value
        _editor.value = RecurringEditorState(
            RecurringForm(
                accountId = s?.accounts?.firstOrNull()?.id,
                anchorDate = deps.clock.today(),
            ),
        )
    }

    fun edit(payment: RecurringPayment) {
        _editor.value = RecurringEditorState(RecurringFormLogic.fromPayment(payment))
    }

    fun close() { _editor.value = null }

    fun update(change: (RecurringForm) -> RecurringForm) = _editor.update { it?.copy(form = change(it.form), errors = emptyMap()) }

    fun save() {
        val e = _editor.value ?: return
        if (e.saving) return
        when (val r = RecurringFormLogic.toDraft(e.form)) {
            is RecurringFormResult.Invalid -> _editor.update { it?.copy(errors = r.errors) }
            is RecurringFormResult.Valid -> {
                _editor.update { it?.copy(saving = true) }
                viewModelScope.launch {
                    try {
                        when (val res = deps.saveRecurring(r.draft)) {
                            is LedgerResult.Success -> {
                                _editor.value = null
                                _events.send("„${r.draft.name.trim()}“ gespeichert – Fälligkeiten werden angezeigt, nie automatisch gebucht." to true)
                            }
                            is LedgerResult.Invalid -> _editor.update { it?.copy(saving = false, errors = EditorFormLogic.domainMessages(res.errors)) }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        _editor.update { it?.copy(saving = false, errors = mapOf(EditorField.GENERAL to "Speichern fehlgeschlagen. Es wurde nichts verändert.")) }
                    }
                }
            }
        }
    }

    fun requestDelete() = _editor.update { it?.copy(confirmDelete = true) }
    fun cancelDelete() = _editor.update { it?.copy(confirmDelete = false) }

    fun confirmDelete() {
        val id: NodeId = _editor.value?.form?.id ?: return
        viewModelScope.launch {
            try {
                deps.deleteRecurring(id)
                _editor.value = null
                _events.send("Wiederkehrende Zahlung gelöscht – bereits gebuchte Buchungen bleiben erhalten." to true)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _events.send("Löschen fehlgeschlagen." to false)
            }
        }
    }

    fun book(o: Occurrence) {
        viewModelScope.launch {
            try {
                when (val r = deps.bookOccurrence(o.payment.id, o.dueDate)) {
                    is LedgerResult.Success -> _events.send("${o.payment.name} gebucht" to true)
                    is LedgerResult.Invalid -> _events.send(EditorFormLogic.domainMessage(r.errors.first()).second to false)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _events.send("Buchen fehlgeschlagen." to false)
            }
        }
    }

    fun skip(o: Occurrence) {
        viewModelScope.launch {
            try {
                when (val r = deps.skipOccurrence(o.payment.id, o.dueDate)) {
                    is LedgerResult.Success -> _events.send("Termin übersprungen" to true)
                    is LedgerResult.Invalid -> _events.send(EditorFormLogic.domainMessage(r.errors.first()).second to false)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _events.send("Überspringen fehlgeschlagen." to false)
            }
        }
    }
}
