package app.lifeos.core.domain.ledger

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.ports.LedgerRepository
import app.lifeos.core.domain.ports.SettingsRepository
import app.lifeos.core.model.Account
import app.lifeos.core.model.AccountKind
import app.lifeos.core.model.Budget
import app.lifeos.core.model.Category
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.Split
import app.lifeos.core.model.TransactionKind
import java.time.LocalDate
import java.time.YearMonth

data class SplitDraft(val categoryId: NodeId, val amountMinor: Long, val memo: String? = null)

/** Eingabe aus der Oberfläche – noch nicht validiert. */
data class TransactionDraft(
    val id: NodeId? = null,
    val kind: TransactionKind,
    val accountId: NodeId,
    val counterAccountId: NodeId? = null,
    val amountMinor: Long,
    val bookingDate: LocalDate,
    val payee: String? = null,
    val note: String? = null,
    val splits: List<SplitDraft> = emptyList(),
) {
    companion object {
        /** Häufigster Fall: eine Einnahme/Ausgabe mit genau einer Kategorie. */
        fun single(
            kind: TransactionKind,
            accountId: NodeId,
            categoryId: NodeId,
            amountMinor: Long,
            bookingDate: LocalDate,
            payee: String? = null,
            note: String? = null,
            id: NodeId? = null,
        ) = TransactionDraft(
            id = id,
            kind = kind,
            accountId = accountId,
            amountMinor = amountMinor,
            bookingDate = bookingDate,
            payee = payee,
            note = note,
            splits = listOf(SplitDraft(categoryId, amountMinor)),
        )
    }
}

/** Legt eine Buchung an oder ändert sie – immer erst nach vollständiger Validierung. */
class RecordTransaction(
    private val repository: LedgerRepository,
    private val ids: IdGenerator,
    private val clock: LifeClock,
) {
    suspend operator fun invoke(draft: TransactionDraft): LedgerResult<NodeId> {
        val accounts = repository.accounts().associateBy { it.id }
        val categories = repository.categories().associateBy { it.id }

        val existing = draft.id?.let { repository.transaction(it) }
        if (draft.id != null && existing == null) {
            return LedgerResult.Invalid(listOf(LedgerError.TransactionNotFound))
        }
        val currency = accounts[draft.accountId]?.currency ?: CurrencyCode.EUR

        val tx = LedgerTransaction(
            id = draft.id ?: ids.newId(),
            kind = draft.kind,
            accountId = draft.accountId,
            counterAccountId = draft.counterAccountId,
            amount = Money(draft.amountMinor, currency),
            bookingDate = draft.bookingDate,
            payee = draft.payee.cleaned(),
            note = draft.note.cleaned(),
            splits = draft.splits.map { Split(ids.newId(), it.categoryId, it.amountMinor, it.memo.cleaned()) },
            isSample = existing?.isSample ?: false,
        )

        val errors = TransactionValidator.validate(tx, accounts, categories)
        if (errors.isNotEmpty()) return LedgerResult.Invalid(errors)

        repository.saveTransaction(tx, clock.now())
        return LedgerResult.Success(tx.id)
    }
}

class DeleteTransaction(private val repository: LedgerRepository) {
    suspend operator fun invoke(id: NodeId): LedgerResult<Unit> {
        if (repository.transaction(id) == null) {
            return LedgerResult.Invalid(listOf(LedgerError.TransactionNotFound))
        }
        repository.deleteTransaction(id)
        return LedgerResult.Success(Unit)
    }
}

/** Setzt das Monatsbudget einer Ausgabenkategorie ab einem Monat (ersetzt ein bestehendes für denselben Monat). */
class SetBudget(
    private val repository: LedgerRepository,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        categoryId: NodeId,
        validFrom: YearMonth,
        amountMinor: Long,
        existing: List<Budget>,
    ): LedgerResult<NodeId> {
        val category = repository.categories().firstOrNull { it.id == categoryId }
            ?: return LedgerResult.Invalid(listOf(LedgerError.UnknownCategory(categoryId)))
        if (category.direction != CategoryDirection.EXPENSE) {
            return LedgerResult.Invalid(listOf(LedgerError.CategoryDirectionMismatch(categoryId)))
        }
        if (amountMinor <= 0) return LedgerResult.Invalid(listOf(LedgerError.AmountNotPositive))
        if (amountMinor > TransactionValidator.MAX_AMOUNT_MINOR) {
            return LedgerResult.Invalid(listOf(LedgerError.AmountOutOfRange))
        }
        val same = existing.firstOrNull { it.categoryId == categoryId && it.validFrom == validFrom }
        val budget = Budget(
            id = same?.id ?: ids.newId(),
            categoryId = categoryId,
            validFrom = validFrom,
            amountMinor = amountMinor,
            currency = CurrencyCode.EUR,
        )
        repository.saveBudget(budget)
        return LedgerResult.Success(budget.id)
    }
}

/** Legt ein neues Konto an. */
class CreateAccount(
    private val repository: LedgerRepository,
    private val ids: IdGenerator,
    private val clock: LifeClock,
) {
    suspend operator fun invoke(
        name: String,
        kind: AccountKind,
        openingBalanceMinor: Long,
        currency: CurrencyCode = CurrencyCode.EUR,
    ): LedgerResult<NodeId> {
        val clean = name.trim()
        if (clean.isEmpty()) return LedgerResult.Invalid(listOf(LedgerError.NameRequired))
        if (clean.length > 80) return LedgerResult.Invalid(listOf(LedgerError.TextTooLong))
        if (kotlin.math.abs(openingBalanceMinor) > TransactionValidator.MAX_AMOUNT_MINOR) {
            return LedgerResult.Invalid(listOf(LedgerError.AmountOutOfRange))
        }
        val order = (repository.accounts().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val account = Account(ids.newId(), clean, kind, currency, openingBalanceMinor, order)
        repository.saveAccount(account, clock.now())
        return LedgerResult.Success(account.id)
    }
}

/** Legt eine neue Kategorie an. */
class CreateCategory(
    private val repository: LedgerRepository,
    private val ids: IdGenerator,
    private val clock: LifeClock,
) {
    suspend operator fun invoke(name: String, direction: CategoryDirection): LedgerResult<NodeId> {
        val clean = name.trim()
        if (clean.isEmpty()) return LedgerResult.Invalid(listOf(LedgerError.NameRequired))
        if (clean.length > 60) return LedgerResult.Invalid(listOf(LedgerError.TextTooLong))
        val order = (repository.categories().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val category = Category(ids.newId(), clean, direction, sortOrder = order)
        repository.saveCategory(category, clock.now())
        return LedgerResult.Success(category.id)
    }
}

/**
 * Legt beim allerersten Start ein Startkonto und Standardkategorien an.
 * Das sind Konfigurationsdaten ohne Beträge – keine Beispieldaten.
 * Läuft genau einmal (Marker in den Einstellungen), auch wenn der Nutzer später alles umbenennt.
 */
class EnsureLedgerDefaults(
    private val repository: LedgerRepository,
    private val settings: SettingsRepository,
    private val ids: IdGenerator,
    private val clock: LifeClock,
) {
    suspend operator fun invoke(): Boolean {
        if (settings.get(MARKER_KEY) != null) return false
        val now = clock.now()
        if (repository.accounts().isEmpty()) {
            repository.saveAccount(
                Account(ids.newId(), "Haushaltskasse", AccountKind.CHECKING, CurrencyCode.EUR, 0L, 0),
                now,
            )
        }
        if (repository.categories().isEmpty()) {
            DEFAULT_EXPENSE.forEachIndexed { i, name ->
                repository.saveCategory(Category(ids.newId(), name, CategoryDirection.EXPENSE, sortOrder = i), now)
            }
            DEFAULT_INCOME.forEachIndexed { i, name ->
                repository.saveCategory(
                    Category(ids.newId(), name, CategoryDirection.INCOME, sortOrder = 100 + i),
                    now,
                )
            }
        }
        settings.put(MARKER_KEY, "1")
        return true
    }

    companion object {
        const val MARKER_KEY = "ledger.defaults.v1"

        val DEFAULT_EXPENSE = listOf(
            "Lebensmittel", "Wohnen", "Energie", "Mobilität", "Fahrzeug", "Versicherungen",
            "Verträge & Abos", "Haushalt", "Gesundheit", "Weiterbildung", "Freizeit", "Kleidung",
            "Werkzeug & Technik", "Geschenke", "Sonstiges",
        )
        val DEFAULT_INCOME = listOf("Gehalt", "Erstattungen", "Sonstige Einnahmen")
    }
}

private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
