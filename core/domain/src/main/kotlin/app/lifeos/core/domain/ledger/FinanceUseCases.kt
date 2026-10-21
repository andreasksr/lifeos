package app.lifeos.core.domain.ledger

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.ports.LedgerRepository
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.RecurringPayment
import app.lifeos.core.model.RecurringStatus
import app.lifeos.core.model.Split
import app.lifeos.core.model.TotalBudget
import app.lifeos.core.model.TransactionKind
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

// ───────────────────────────── Budgets ─────────────────────────────

class SetTotalBudget(private val repository: LedgerRepository, private val ids: IdGenerator) {
    suspend operator fun invoke(validFrom: YearMonth, amountMinor: Long): LedgerResult<NodeId> {
        if (amountMinor <= 0) return LedgerResult.Invalid(listOf(LedgerError.AmountNotPositive))
        if (amountMinor > TransactionValidator.MAX_AMOUNT_MINOR) return LedgerResult.Invalid(listOf(LedgerError.AmountOutOfRange))
        val existing = repository.observeTotalBudgets().first().firstOrNull { it.validFrom == validFrom }
        val budget = TotalBudget(existing?.id ?: ids.newId(), validFrom, amountMinor, CurrencyCode.EUR)
        repository.saveTotalBudget(budget)
        return LedgerResult.Success(budget.id)
    }
}

class RemoveTotalBudget(private val repository: LedgerRepository) {
    suspend operator fun invoke(validFrom: YearMonth) {
        repository.observeTotalBudgets().first().filter { it.validFrom == validFrom }.forEach { repository.deleteTotalBudget(it.id) }
    }
}

class RemoveCategoryBudget(private val repository: LedgerRepository) {
    suspend operator fun invoke(categoryId: NodeId, validFrom: YearMonth) {
        repository.observeBudgets().first()
            .filter { it.categoryId == categoryId && it.validFrom == validFrom }
            .forEach { repository.deleteBudget(it.id) }
    }
}

// ───────────────────────────── Konten & Kategorien ─────────────────────────────

class UpdateAccount(private val repository: LedgerRepository, private val clock: LifeClock) {
    suspend operator fun invoke(id: NodeId, name: String, archived: Boolean): LedgerResult<Unit> {
        val account = repository.accounts().firstOrNull { it.id == id }
            ?: return LedgerResult.Invalid(listOf(LedgerError.AccountNotFound))
        val clean = name.trim()
        if (clean.isEmpty()) return LedgerResult.Invalid(listOf(LedgerError.NameRequired))
        if (clean.length > 80) return LedgerResult.Invalid(listOf(LedgerError.TextTooLong))
        repository.saveAccount(account.copy(name = clean, archived = archived), clock.now())
        return LedgerResult.Success(Unit)
    }
}

class UpdateCategory(private val repository: LedgerRepository, private val clock: LifeClock) {
    suspend operator fun invoke(id: NodeId, name: String, archived: Boolean): LedgerResult<Unit> {
        val category = repository.categories().firstOrNull { it.id == id }
            ?: return LedgerResult.Invalid(listOf(LedgerError.CategoryNotFound))
        val clean = name.trim()
        if (clean.isEmpty()) return LedgerResult.Invalid(listOf(LedgerError.NameRequired))
        if (clean.length > 60) return LedgerResult.Invalid(listOf(LedgerError.TextTooLong))
        repository.saveCategory(category.copy(name = clean, archived = archived), clock.now())
        return LedgerResult.Success(Unit)
    }
}

/**
 * Kontostand abgleichen: erzeugt eine Korrekturbuchung über die Differenz zwischen berechnetem und
 * tatsächlichem Stand. Korrekturen verändern keine Einnahmen-/Ausgabenstatistik.
 */
class ReconcileBalance(
    private val repository: LedgerRepository,
    private val ids: IdGenerator,
    private val clock: LifeClock,
) {
    suspend operator fun invoke(accountId: NodeId, actualBalanceMinor: Long, date: LocalDate): LedgerResult<NodeId> {
        val accounts = repository.accounts()
        val account = accounts.firstOrNull { it.id == accountId }
            ?: return LedgerResult.Invalid(listOf(LedgerError.AccountNotFound))
        val all = repository.observeAllTransactions().first()
        val computed = LedgerMath.balances(listOf(account), all, until = date).getValue(accountId).minor
        val diff = Math.subtractExact(actualBalanceMinor, computed)
        if (diff == 0L) return LedgerResult.Invalid(listOf(LedgerError.NothingToCorrect))
        val tx = LedgerTransaction(
            id = ids.newId(),
            kind = if (diff > 0) TransactionKind.CORRECTION_IN else TransactionKind.CORRECTION_OUT,
            accountId = accountId,
            counterAccountId = null,
            amount = Money(kotlin.math.abs(diff), account.currency),
            bookingDate = date,
            payee = "Kontostand abgeglichen",
            note = null,
            splits = emptyList(),
        )
        val errors = TransactionValidator.validate(tx, accounts.associateBy { it.id }, emptyMap())
        if (errors.isNotEmpty()) return LedgerResult.Invalid(errors)
        repository.saveTransaction(tx, clock.now())
        return LedgerResult.Success(tx.id)
    }
}

// ───────────────────────────── Wiederkehrende Zahlungen ─────────────────────────────

data class RecurringDraft(
    val id: NodeId? = null,
    val name: String,
    val kind: TransactionKind,
    val accountId: NodeId,
    val categoryId: NodeId,
    val amountMinor: Long,
    val intervalMonths: Int,
    val anchorDate: LocalDate,
    val endDate: LocalDate? = null,
    val payee: String? = null,
    val note: String? = null,
    val active: Boolean = true,
    val isFixedCost: Boolean = true,
)

class SaveRecurring(
    private val repository: LedgerRepository,
    private val ids: IdGenerator,
    private val clock: LifeClock,
) {
    suspend operator fun invoke(draft: RecurringDraft): LedgerResult<NodeId> {
        val errors = mutableListOf<LedgerError>()
        val name = draft.name.trim()
        if (name.isEmpty()) errors += LedgerError.NameRequired
        if (name.length > 80) errors += LedgerError.TextTooLong
        if (draft.kind != TransactionKind.EXPENSE && draft.kind != TransactionKind.INCOME) {
            errors += LedgerError.CategoryDirectionMismatch(draft.categoryId)
        }
        if (draft.amountMinor <= 0) errors += LedgerError.AmountNotPositive
        if (draft.amountMinor > TransactionValidator.MAX_AMOUNT_MINOR) errors += LedgerError.AmountOutOfRange
        if (draft.intervalMonths !in RecurringPayment.ALLOWED_INTERVALS) errors += LedgerError.InvalidInterval
        if (draft.endDate != null && draft.endDate.isBefore(draft.anchorDate)) errors += LedgerError.EndBeforeStart
        val account = repository.accounts().firstOrNull { it.id == draft.accountId }
        if (account == null) errors += LedgerError.UnknownAccount(draft.accountId)
        val category = repository.categories().firstOrNull { it.id == draft.categoryId }
        when {
            category == null -> errors += LedgerError.UnknownCategory(draft.categoryId)
            category.direction != draft.kind.categoryDirection -> errors += LedgerError.CategoryDirectionMismatch(draft.categoryId)
        }
        if (draft.id != null && repository.recurring(draft.id) == null) errors += LedgerError.RecurringNotFound
        if (errors.isNotEmpty() || account == null) return LedgerResult.Invalid(errors.distinct())

        val payment = RecurringPayment(
            id = draft.id ?: ids.newId(),
            name = name,
            kind = draft.kind,
            accountId = draft.accountId,
            categoryId = draft.categoryId,
            amount = Money(draft.amountMinor, account.currency),
            intervalMonths = draft.intervalMonths,
            anchorDate = draft.anchorDate,
            endDate = draft.endDate,
            payee = draft.payee?.trim()?.takeIf { it.isNotEmpty() },
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            active = draft.active,
            isFixedCost = draft.isFixedCost,
        )
        repository.saveRecurring(payment, clock.now())
        return LedgerResult.Success(payment.id)
    }
}

class DeleteRecurring(private val repository: LedgerRepository) {
    suspend operator fun invoke(id: NodeId): LedgerResult<Unit> {
        repository.recurring(id) ?: return LedgerResult.Invalid(listOf(LedgerError.RecurringNotFound))
        repository.deleteRecurring(id)
        return LedgerResult.Success(Unit)
    }
}

/**
 * Führt genau eine Fälligkeit aus: legt eine echte Buchung an und vermerkt die Fälligkeit.
 * Doppelte Ausführung derselben Fälligkeit ist unmöglich (Domäne + Primärschlüssel).
 * Betrag und Buchungsdatum dürfen abweichen (z. B. Stromabschlag geändert, später abgebucht).
 */
class BookOccurrence(
    private val repository: LedgerRepository,
    private val ids: IdGenerator,
    private val clock: LifeClock,
) {
    suspend operator fun invoke(
        recurringId: NodeId,
        dueDate: LocalDate,
        bookingDate: LocalDate = dueDate,
        amountMinor: Long? = null,
    ): LedgerResult<NodeId> {
        val payment = repository.recurring(recurringId)
            ?: return LedgerResult.Invalid(listOf(LedgerError.RecurringNotFound))
        val dueDates = RecurringSchedule.dueDates(payment, dueDate, dueDate)
        if (dueDates.isEmpty()) return LedgerResult.Invalid(listOf(LedgerError.OccurrenceNotDue))
        val bookings = repository.observeRecurringBookings().first()
        if (bookings.any { it.recurringId == recurringId && it.dueDate == dueDate }) {
            return LedgerResult.Invalid(listOf(LedgerError.OccurrenceAlreadyHandled))
        }
        val amount = amountMinor ?: payment.amount.minor
        val tx = LedgerTransaction(
            id = ids.newId(),
            kind = payment.kind,
            accountId = payment.accountId,
            counterAccountId = null,
            amount = Money(amount, payment.amount.currency),
            bookingDate = bookingDate,
            payee = payment.payee ?: payment.name,
            note = payment.note,
            splits = listOf(Split(ids.newId(), payment.categoryId, amount)),
        )
        val errors = TransactionValidator.validate(
            tx, repository.accounts().associateBy { it.id }, repository.categories().associateBy { it.id },
        )
        if (errors.isNotEmpty()) return LedgerResult.Invalid(errors)
        repository.bookRecurring(tx, recurringId, dueDate, clock.now())
        return LedgerResult.Success(tx.id)
    }
}

class SkipOccurrence(private val repository: LedgerRepository) {
    suspend operator fun invoke(recurringId: NodeId, dueDate: LocalDate): LedgerResult<Unit> {
        val payment = repository.recurring(recurringId)
            ?: return LedgerResult.Invalid(listOf(LedgerError.RecurringNotFound))
        if (RecurringSchedule.dueDates(payment, dueDate, dueDate).isEmpty()) {
            return LedgerResult.Invalid(listOf(LedgerError.OccurrenceNotDue))
        }
        if (repository.observeRecurringBookings().first().any { it.recurringId == recurringId && it.dueDate == dueDate }) {
            return LedgerResult.Invalid(listOf(LedgerError.OccurrenceAlreadyHandled))
        }
        repository.skipRecurring(recurringId, dueDate)
        return LedgerResult.Success(Unit)
    }
}

class UnskipOccurrence(private val repository: LedgerRepository) {
    suspend operator fun invoke(recurringId: NodeId, dueDate: LocalDate) {
        val booking = repository.observeRecurringBookings().first()
            .firstOrNull { it.recurringId == recurringId && it.dueDate == dueDate }
        if (booking?.status == RecurringStatus.SKIPPED) repository.clearRecurringBooking(recurringId, dueDate)
    }
}

// ───────────────────────────── Vorschläge & Filter ─────────────────────────────

object CategorySuggestions {
    /**
     * Häufig und zuletzt verwendete Kategorien einer Richtung: Rang nach Anzahl in den letzten
     * [windowDays] Tagen, bei Gleichstand nach letzter Verwendung. Danach alle übrigen in Sortierreihenfolge.
     */
    fun ranked(
        categories: List<app.lifeos.core.model.Category>,
        transactions: List<LedgerTransaction>,
        direction: CategoryDirection,
        today: LocalDate,
        windowDays: Long = 60,
    ): List<app.lifeos.core.model.Category> {
        val relevant = categories.filter { it.direction == direction && !it.archived }
        val since = today.minusDays(windowDays)
        val counts = HashMap<NodeId, Int>()
        val last = HashMap<NodeId, LocalDate>()
        transactions.forEach { tx ->
            if (tx.kind.categoryDirection != direction || tx.kind == TransactionKind.REFUND) return@forEach
            tx.splits.forEach { s ->
                if (!tx.bookingDate.isBefore(since)) counts[s.categoryId] = (counts[s.categoryId] ?: 0) + 1
                val prev = last[s.categoryId]
                if (prev == null || tx.bookingDate.isAfter(prev)) last[s.categoryId] = tx.bookingDate
            }
        }
        val used = relevant.filter { counts.containsKey(it.id) }
            .sortedWith(
                compareByDescending<app.lifeos.core.model.Category> { counts[it.id] ?: 0 }
                    .thenByDescending { last[it.id] }
                    .thenBy { it.sortOrder },
            )
        return used + relevant.filter { !counts.containsKey(it.id) }.sortedBy { it.sortOrder }
    }
}

data class TransactionQuery(
    val text: String = "",
    val kinds: Set<TransactionKind> = emptySet(),
    val categoryId: NodeId? = null,
    val accountId: NodeId? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    val isEmpty: Boolean
        get() = text.isBlank() && kinds.isEmpty() && categoryId == null && accountId == null && from == null && to == null
}

object TransactionFilter {
    /**
     * Filtert Buchungen. Text sucht (ohne Groß/Kleinschreibung) in Empfänger, Notiz, Kategorie- und
     * Kontonamen sowie im formatierten Betrag („89,90").
     */
    fun apply(
        transactions: List<LedgerTransaction>,
        query: TransactionQuery,
        categoryNames: Map<NodeId, String>,
        accountNames: Map<NodeId, String>,
    ): List<LedgerTransaction> {
        val needle = query.text.trim().lowercase()
        return transactions.filter { tx ->
            (query.kinds.isEmpty() || tx.kind in query.kinds) &&
                (query.accountId == null || tx.accountId == query.accountId || tx.counterAccountId == query.accountId) &&
                (query.categoryId == null || tx.splits.any { it.categoryId == query.categoryId }) &&
                (query.from == null || !tx.bookingDate.isBefore(query.from)) &&
                (query.to == null || !tx.bookingDate.isAfter(query.to)) &&
                (needle.isEmpty() || haystack(tx, categoryNames, accountNames).contains(needle))
        }
    }

    private fun haystack(tx: LedgerTransaction, categories: Map<NodeId, String>, accounts: Map<NodeId, String>): String =
        buildString {
            tx.payee?.let { append(it).append(' ') }
            tx.note?.let { append(it).append(' ') }
            tx.splits.forEach { s -> categories[s.categoryId]?.let { append(it).append(' ') }; s.memo?.let { append(it).append(' ') } }
            accounts[tx.accountId]?.let { append(it).append(' ') }
            tx.counterAccountId?.let { id -> accounts[id]?.let { append(it).append(' ') } }
            append(app.lifeos.core.model.MoneyText.format(tx.amount.minor, tx.amount.currency, withSymbol = false))
        }.lowercase()
}
