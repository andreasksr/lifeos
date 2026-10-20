package app.lifeos.feature.ledger.structure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.ledger.LedgerMonthSnapshot
import app.lifeos.core.domain.ledger.LedgerResult
import app.lifeos.core.model.AccountKind
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.feature.ledger.LedgerDependencies
import app.lifeos.feature.ledger.presentation.EditorFormLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class StructureForms(
    val accountName: String = "",
    val accountKind: AccountKind = AccountKind.CHECKING,
    val accountOpening: String = "",
    val accountError: String? = null,
    val categoryName: String = "",
    val categoryDirection: CategoryDirection = CategoryDirection.EXPENSE,
    val categoryError: String? = null,
    val budgetEditing: NodeId? = null,
    val budgetText: String = "",
    val budgetError: String? = null,
    val message: String? = null,
)

class StructureViewModel(private val deps: LedgerDependencies) : ViewModel() {

    val month: YearMonth = YearMonth.from(deps.clock.today())

    val snapshot: StateFlow<LedgerMonthSnapshot?> = deps.observeMonth(month)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _forms = MutableStateFlow(StructureForms())
    val forms: StateFlow<StructureForms> = _forms.asStateFlow()

    fun onAccountName(v: String) = _forms.update { it.copy(accountName = v.take(80), accountError = null, message = null) }
    fun onAccountKind(v: AccountKind) = _forms.update { it.copy(accountKind = v) }
    fun onAccountOpening(v: String) = _forms.update { it.copy(accountOpening = v.take(20), accountError = null) }
    fun onCategoryName(v: String) = _forms.update { it.copy(categoryName = v.take(60), categoryError = null, message = null) }
    fun onCategoryDirection(v: CategoryDirection) = _forms.update { it.copy(categoryDirection = v) }

    fun startBudgetEdit(categoryId: NodeId, currentMinor: Long?) = _forms.update {
        it.copy(
            budgetEditing = categoryId,
            budgetText = currentMinor?.let { MoneyText.toEditText(it) }.orEmpty(),
            budgetError = null,
            message = null,
        )
    }

    fun onBudgetText(v: String) = _forms.update { it.copy(budgetText = v.take(20), budgetError = null) }
    fun cancelBudgetEdit() = _forms.update { it.copy(budgetEditing = null, budgetError = null) }

    fun createAccount() {
        val f = _forms.value
        val opening = if (f.accountOpening.isBlank()) 0L else when (val p = MoneyText.parse(f.accountOpening)) {
            is MoneyText.ParseResult.Success -> p.minor
            is MoneyText.ParseResult.Failure -> {
                _forms.update { it.copy(accountError = EditorFormLogic.parseMessage(p.reason)) }
                return
            }
        }
        launchSafely({ msg -> _forms.update { it.copy(accountError = msg) } }) {
            when (val r = deps.createAccount(f.accountName, f.accountKind, opening)) {
                is LedgerResult.Success -> _forms.update {
                    it.copy(accountName = "", accountOpening = "", message = "Konto „${f.accountName.trim()}“ angelegt.")
                }
                is LedgerResult.Invalid -> _forms.update { it.copy(accountError = EditorFormLogic.domainMessage(r.errors.first()).second) }
            }
        }
    }

    fun createCategory() {
        val f = _forms.value
        launchSafely({ msg -> _forms.update { it.copy(categoryError = msg) } }) {
            when (val r = deps.createCategory(f.categoryName, f.categoryDirection)) {
                is LedgerResult.Success -> _forms.update {
                    it.copy(categoryName = "", message = "Kategorie „${f.categoryName.trim()}“ angelegt.")
                }
                is LedgerResult.Invalid -> _forms.update { it.copy(categoryError = EditorFormLogic.domainMessage(r.errors.first()).second) }
            }
        }
    }

    fun saveBudget() {
        val f = _forms.value
        val categoryId = f.budgetEditing ?: return
        val amount = when (val p = MoneyText.parse(f.budgetText)) {
            is MoneyText.ParseResult.Success -> p.minor
            is MoneyText.ParseResult.Failure -> {
                _forms.update { it.copy(budgetError = EditorFormLogic.parseMessage(p.reason)) }
                return
            }
        }
        val budgets = snapshot.value?.budgets.orEmpty()
        launchSafely({ msg -> _forms.update { it.copy(budgetError = msg) } }) {
            when (val r = deps.setBudget(categoryId, month, amount, budgets)) {
                is LedgerResult.Success -> _forms.update { it.copy(budgetEditing = null, message = "Budget gespeichert.") }
                is LedgerResult.Invalid -> _forms.update { it.copy(budgetError = EditorFormLogic.domainMessage(r.errors.first()).second) }
            }
        }
    }

    private fun launchSafely(onError: (String) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError("Speichern fehlgeschlagen. Es wurde nichts verändert.")
            }
        }
    }
}
