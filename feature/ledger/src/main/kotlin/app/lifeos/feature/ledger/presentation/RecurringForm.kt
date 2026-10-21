package app.lifeos.feature.ledger.presentation

import app.lifeos.core.domain.ledger.RecurringDraft
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.RecurringPayment
import app.lifeos.core.model.TransactionKind
import java.time.LocalDate

/** Rohzustand des Formulars „Wiederkehrende Zahlung". */
data class RecurringForm(
    val id: NodeId? = null,
    val name: String = "",
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val accountId: NodeId? = null,
    val categoryId: NodeId? = null,
    val amountText: String = "",
    val intervalMonths: Int = 1,
    val anchorDate: LocalDate,
    val endDate: LocalDate? = null,
    val payee: String = "",
    val note: String = "",
    val active: Boolean = true,
    val isFixedCost: Boolean = true,
)

sealed interface RecurringFormResult {
    data class Valid(val draft: RecurringDraft) : RecurringFormResult
    data class Invalid(val errors: Map<EditorField, String>) : RecurringFormResult
}

object RecurringFormLogic {

    fun fromPayment(p: RecurringPayment) = RecurringForm(
        id = p.id,
        name = p.name,
        kind = p.kind,
        accountId = p.accountId,
        categoryId = p.categoryId,
        amountText = MoneyText.toEditText(p.amount.minor, p.amount.currency),
        intervalMonths = p.intervalMonths,
        anchorDate = p.anchorDate,
        endDate = p.endDate,
        payee = p.payee.orEmpty(),
        note = p.note.orEmpty(),
        active = p.active,
        isFixedCost = p.isFixedCost,
    )

    fun toDraft(form: RecurringForm): RecurringFormResult {
        val errors = linkedMapOf<EditorField, String>()
        if (form.name.isBlank()) errors[EditorField.PAYEE] = "Bitte einen Namen eingeben (z. B. Miete)."
        val amount = when (val p = MoneyText.parse(form.amountText)) {
            is MoneyText.ParseResult.Success -> p.minor.also { if (it <= 0) errors[EditorField.AMOUNT] = "Bitte einen Betrag größer als 0 eingeben." }
            is MoneyText.ParseResult.Failure -> {
                errors[EditorField.AMOUNT] = EditorFormLogic.parseMessage(p.reason)
                null
            }
        }
        if (form.accountId == null) errors[EditorField.ACCOUNT] = "Bitte ein Konto wählen."
        if (form.categoryId == null) errors[EditorField.CATEGORY] = "Bitte eine Kategorie wählen."
        if (form.endDate != null && form.endDate.isBefore(form.anchorDate)) errors[EditorField.DATE] = "Das Enddatum liegt vor der ersten Fälligkeit."
        if (errors.isNotEmpty() || amount == null || form.accountId == null || form.categoryId == null) {
            return RecurringFormResult.Invalid(errors)
        }
        return RecurringFormResult.Valid(
            RecurringDraft(
                id = form.id,
                name = form.name,
                kind = form.kind,
                accountId = form.accountId,
                categoryId = form.categoryId,
                amountMinor = amount,
                intervalMonths = form.intervalMonths,
                anchorDate = form.anchorDate,
                endDate = form.endDate,
                payee = form.payee,
                note = form.note,
                active = form.active,
                isFixedCost = form.isFixedCost,
            ),
        )
    }
}
