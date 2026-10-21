package app.lifeos.feature.ledger.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.ledger.LedgerMonthSnapshot
import app.lifeos.core.domain.ledger.LedgerResult
import app.lifeos.core.domain.ledger.Occurrence
import app.lifeos.core.model.MoneyText
import app.lifeos.feature.ledger.LedgerDependencies
import app.lifeos.feature.ledger.presentation.EditorFormLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Einmalige Rückmeldungen an die Oberfläche (Toast + Lichtimpuls). */
data class FinanceEvent(val text: String, val success: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(private val deps: LedgerDependencies) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(deps.clock.today()))

    val snapshot: StateFlow<LedgerMonthSnapshot?> = month
        .flatMapLatest { deps.observeMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<FinanceEvent>(Channel.BUFFERED)
    val events: Flow<FinanceEvent> = _events.receiveAsFlow()

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }
    fun currentMonth() { month.value = YearMonth.from(deps.clock.today()) }
    fun showMonth(m: YearMonth) { month.value = m }

    fun book(occurrence: Occurrence) = launchSafe {
        when (val r = deps.bookOccurrence(occurrence.payment.id, occurrence.dueDate)) {
            is LedgerResult.Success -> _events.send(FinanceEvent("${occurrence.payment.name} gebucht · ${MoneyText.format(occurrence.payment.amount)}", true))
            is LedgerResult.Invalid -> _events.send(FinanceEvent(EditorFormLogic.domainMessage(r.errors.first()).second, false))
        }
    }

    fun skip(occurrence: Occurrence) = launchSafe {
        when (val r = deps.skipOccurrence(occurrence.payment.id, occurrence.dueDate)) {
            is LedgerResult.Success -> _events.send(FinanceEvent("${occurrence.payment.name} für diesen Termin übersprungen", true))
            is LedgerResult.Invalid -> _events.send(FinanceEvent(EditorFormLogic.domainMessage(r.errors.first()).second, false))
        }
    }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(FinanceEvent("Aktion fehlgeschlagen. Es wurde nichts verändert.", false))
            }
        }
    }
}
