package app.lifeos.feature.ledger.presentation

import app.lifeos.core.domain.ledger.LedgerError
import app.lifeos.core.domain.ledger.SplitDraft
import app.lifeos.core.domain.ledger.TransactionDraft
import app.lifeos.core.model.Account
import app.lifeos.core.model.Category
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.TransactionKind
import java.time.LocalDate

enum class EditorField { AMOUNT, ACCOUNT, COUNTER_ACCOUNT, CATEGORY, DATE, PAYEE, NOTE, GENERAL }

/** Rohzustand des Buchungsformulars – genau das, was der Nutzer eingegeben hat. */
data class EditorForm(
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val amountText: String = "",
    val accountId: NodeId? = null,
    val counterAccountId: NodeId? = null,
    val categoryId: NodeId? = null,
    val date: LocalDate,
    val payee: String = "",
    val note: String = "",
    /**
     * Gesetzt, wenn eine bestehende Buchung auf mehrere Kategorien aufgeteilt ist. Diese Aufteilung
     * kann das einfache Formular nicht abbilden – Betrag und Kategorie bleiben dann gesperrt und die
     * Aufteilung wird unverändert übernommen.
     */
    val lockedSplits: List<SplitDraft>? = null,
)

sealed interface FormResult {
    data class Valid(val draft: TransactionDraft) : FormResult
    data class Invalid(val errors: Map<EditorField, String>) : FormResult
}

/** Reine Formularlogik: Eingabe → Entwurf, Domänenfehler → verständliche deutsche Meldungen. */
object EditorFormLogic {

    fun initial(accounts: List<Account>, today: LocalDate): EditorForm = EditorForm(
        accountId = accounts.firstOrNull { !it.archived }?.id,
        date = today,
    )

    fun fromTransaction(tx: LedgerTransaction): EditorForm = EditorForm(
        kind = tx.kind,
        amountText = MoneyText.toEditText(tx.amount.minor, tx.amount.currency),
        accountId = tx.accountId,
        counterAccountId = tx.counterAccountId,
        categoryId = tx.splits.singleOrNull()?.categoryId,
        date = tx.bookingDate,
        payee = tx.payee.orEmpty(),
        note = tx.note.orEmpty(),
        lockedSplits = if (tx.splits.size > 1) tx.splits.map { SplitDraft(it.categoryId, it.amountMinor, it.memo) } else null,
    )

    /** Beim Wechsel der Art passt eine bereits gewählte Kategorie evtl. nicht mehr → zurücksetzen. */
    fun changeKind(form: EditorForm, kind: TransactionKind, categories: List<Category>): EditorForm {
        if (form.lockedSplits != null) return form // Art einer aufgeteilten Buchung nicht änderbar
        val direction = kind.categoryDirection
        val categoryStillValid = direction != null &&
            categories.firstOrNull { it.id == form.categoryId }?.direction == direction
        return form.copy(
            kind = kind,
            categoryId = if (categoryStillValid) form.categoryId else null,
            counterAccountId = if (kind == TransactionKind.TRANSFER) form.counterAccountId else null,
        )
    }

    fun categoriesFor(kind: TransactionKind, categories: List<Category>): List<Category> {
        val direction: CategoryDirection = kind.categoryDirection ?: return emptyList()
        return categories.filter { it.direction == direction && !it.archived }.sortedBy { it.sortOrder }
    }

    fun toDraft(form: EditorForm, editId: NodeId?, currency: CurrencyCode = CurrencyCode.EUR): FormResult {
        val errors = linkedMapOf<EditorField, String>()

        val amount: Long? = when (val parsed = MoneyText.parse(form.amountText, currency)) {
            is MoneyText.ParseResult.Success -> parsed.minor
            is MoneyText.ParseResult.Failure -> {
                errors[EditorField.AMOUNT] = parseMessage(parsed.reason)
                null
            }
        }
        if (amount != null && amount <= 0) errors[EditorField.AMOUNT] = "Bitte einen Betrag größer als 0 eingeben."
        if (form.accountId == null) errors[EditorField.ACCOUNT] = "Bitte ein Konto wählen."

        when (form.kind) {
            TransactionKind.TRANSFER -> {
                if (form.counterAccountId == null) errors[EditorField.COUNTER_ACCOUNT] = "Bitte das Zielkonto wählen."
                else if (form.counterAccountId == form.accountId) errors[EditorField.COUNTER_ACCOUNT] = "Ziel- und Quellkonto müssen verschieden sein."
            }
            else -> if (form.lockedSplits == null && form.categoryId == null) {
                errors[EditorField.CATEGORY] = "Bitte eine Kategorie wählen."
            }
        }
        if (errors.isNotEmpty() || amount == null || form.accountId == null) return FormResult.Invalid(errors)

        val splits = when {
            form.kind == TransactionKind.TRANSFER -> emptyList()
            form.lockedSplits != null -> form.lockedSplits
            else -> listOf(SplitDraft(form.categoryId!!, amount))
        }
        return FormResult.Valid(
            TransactionDraft(
                id = editId,
                kind = form.kind,
                accountId = form.accountId,
                counterAccountId = if (form.kind == TransactionKind.TRANSFER) form.counterAccountId else null,
                amountMinor = amount,
                bookingDate = form.date,
                payee = form.payee,
                note = form.note,
                splits = splits,
            ),
        )
    }

    fun parseMessage(error: MoneyText.ParseError): String = when (error) {
        MoneyText.ParseError.EMPTY -> "Bitte einen Betrag eingeben."
        MoneyText.ParseError.INVALID_CHARACTER -> "Nur Ziffern, Komma und Punkt sind erlaubt."
        MoneyText.ParseError.MULTIPLE_DECIMAL_SEPARATORS -> "Bitte nur ein Komma verwenden."
        MoneyText.ParseError.INVALID_GROUPING -> "Das Zahlenformat ist nicht eindeutig (Beispiel: 1.234,56)."
        MoneyText.ParseError.TOO_MANY_DECIMALS -> "Höchstens zwei Nachkommastellen."
        MoneyText.ParseError.OUT_OF_RANGE -> "Der Betrag ist zu groß."
    }

    /** Domänenfehler (letzte Verteidigungslinie vor der DB) → Feld + Meldung. */
    fun domainMessage(error: LedgerError): Pair<EditorField, String> = when (error) {
        LedgerError.AmountNotPositive -> EditorField.AMOUNT to "Bitte einen Betrag größer als 0 eingeben."
        LedgerError.AmountOutOfRange -> EditorField.AMOUNT to "Der Betrag ist zu groß."
        LedgerError.MissingSplits -> EditorField.CATEGORY to "Bitte eine Kategorie wählen."
        LedgerError.SplitsOnTransfer -> EditorField.CATEGORY to "Umbuchungen haben keine Kategorie."
        LedgerError.SplitAmountNotPositive,
        is LedgerError.SplitSumMismatch -> EditorField.AMOUNT to "Die Aufteilung passt nicht zum Betrag."
        LedgerError.TransferNeedsCounterAccount -> EditorField.COUNTER_ACCOUNT to "Bitte das Zielkonto wählen."
        LedgerError.TransferSameAccount -> EditorField.COUNTER_ACCOUNT to "Ziel- und Quellkonto müssen verschieden sein."
        LedgerError.CounterAccountOnNonTransfer -> EditorField.COUNTER_ACCOUNT to "Ein Zielkonto gibt es nur bei Umbuchungen."
        is LedgerError.UnknownAccount -> EditorField.ACCOUNT to "Dieses Konto existiert nicht mehr."
        is LedgerError.ArchivedAccount -> EditorField.ACCOUNT to "Dieses Konto ist archiviert."
        is LedgerError.UnknownCategory -> EditorField.CATEGORY to "Diese Kategorie existiert nicht mehr."
        is LedgerError.CategoryDirectionMismatch -> EditorField.CATEGORY to "Die Kategorie passt nicht zur Buchungsart."
        LedgerError.CurrencyMismatch -> EditorField.ACCOUNT to "Die Konten haben unterschiedliche Währungen."
        LedgerError.TextTooLong -> EditorField.NOTE to "Text ist zu lang (max. 500 Zeichen)."
        LedgerError.NameRequired -> EditorField.GENERAL to "Bitte einen Namen eingeben."
        LedgerError.TransactionNotFound -> EditorField.GENERAL to "Die Buchung wurde inzwischen gelöscht."
    }

    fun domainMessages(errors: List<LedgerError>): Map<EditorField, String> =
        errors.map(::domainMessage).toMap()
}
