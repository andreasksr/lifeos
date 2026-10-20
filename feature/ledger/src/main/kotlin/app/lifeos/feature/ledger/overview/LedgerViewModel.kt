package app.lifeos.feature.ledger.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.ledger.LedgerMonthSnapshot
import app.lifeos.feature.ledger.LedgerDependencies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

sealed interface LedgerUiState {
    data object Loading : LedgerUiState
    data class Ready(val snapshot: LedgerMonthSnapshot, val today: LocalDate, val isCurrentMonth: Boolean) : LedgerUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(private val deps: LedgerDependencies) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(deps.clock.today()))

    val state: StateFlow<LedgerUiState> = month
        .flatMapLatest { m -> deps.observeMonth(m).map { snapshot -> snapshot to m } }
        .map { (snapshot, m) ->
            val today = deps.clock.today()
            LedgerUiState.Ready(snapshot, today, m == YearMonth.from(today))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUiState.Loading)

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun currentMonth() {
        month.value = YearMonth.from(deps.clock.today())
    }
}
