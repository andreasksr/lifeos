package app.lifeos.core.domain.testing

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.ports.LedgerRepository
import app.lifeos.core.domain.ports.LinkRepository
import app.lifeos.core.domain.ports.SettingsRepository
import app.lifeos.core.model.Account
import app.lifeos.core.model.Budget
import app.lifeos.core.model.Category
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeLink
import app.lifeos.core.model.NodeSummary
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.RecurringBooking
import app.lifeos.core.model.RecurringPayment
import app.lifeos.core.model.RecurringStatus
import app.lifeos.core.model.TotalBudget
import app.lifeos.core.domain.ports.LinkedNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

fun id(n: Int): NodeId = NodeId("00000000-0000-4000-8000-%012d".format(n))

class SequentialIds(start: Int = 1000) : IdGenerator {
    private var next = start
    override fun newId(): NodeId = id(next++)
}

class FixedClock(
    var instant: Instant = Instant.parse("2026-10-20T10:00:00Z"),
    var date: LocalDate = LocalDate.of(2026, 10, 20),
) : LifeClock {
    override fun now(): Instant = instant
    override fun today(): LocalDate = date
}

/** In-Memory-Implementierung mit derselben Semantik wie die Room-Implementierung. */
class FakeLedgerRepository : LedgerRepository {
    val accounts = MutableStateFlow<List<Account>>(emptyList())
    val categories = MutableStateFlow<List<Category>>(emptyList())
    val budgets = MutableStateFlow<List<Budget>>(emptyList())
    val transactions = MutableStateFlow<List<LedgerTransaction>>(emptyList())
    val nodes = linkedMapOf<NodeId, NodeSummary>()
    val totalBudgets = MutableStateFlow<List<TotalBudget>>(emptyList())
    val recurring = MutableStateFlow<List<RecurringPayment>>(emptyList())
    val bookings = MutableStateFlow<List<RecurringBooking>>(emptyList())

    override fun observeAccounts(): Flow<List<Account>> = accounts
    override fun observeCategories(): Flow<List<Category>> = categories
    override fun observeBudgets(): Flow<List<Budget>> = budgets
    override fun observeTransactions(from: LocalDate, to: LocalDate) = transactions.map { list ->
        list.filter { !it.bookingDate.isBefore(from) && !it.bookingDate.isAfter(to) }
            .sortedByDescending { it.bookingDate }
    }
    override fun observeAllTransactions(): Flow<List<LedgerTransaction>> = transactions.map { list ->
        val byTx = bookings.value.filter { it.transactionId != null }.associateBy { it.transactionId }
        list.map { tx -> byTx[tx.id]?.let { tx.copy(recurringId = it.recurringId) } ?: tx }
    }

    override suspend fun accounts() = accounts.value
    override suspend fun categories() = categories.value
    override suspend fun transaction(id: NodeId) = transactions.value.firstOrNull { it.id == id }

    override suspend fun saveTransaction(tx: LedgerTransaction, now: Instant) {
        transactions.value = transactions.value.filterNot { it.id == tx.id } + tx
        nodes[tx.id] = NodeSummary(tx.id, NodeType.TRANSACTION, tx.payee ?: "Buchung", tx.isSample, false)
    }

    override suspend fun deleteTransaction(id: NodeId) {
        transactions.value = transactions.value.filterNot { it.id == id }
        bookings.value = bookings.value.filterNot { it.transactionId == id } // Kaskade wie in der DB
        nodes.remove(id)
        onNodeDeleted(id)
    }

    /** Wird vom FakeLinkRepository gesetzt, um die Link-Kaskade nachzubilden. */
    var onNodeDeleted: (NodeId) -> Unit = {}

    override suspend fun saveAccount(account: Account, now: Instant) {
        accounts.value = accounts.value.filterNot { it.id == account.id } + account
        nodes[account.id] = NodeSummary(account.id, NodeType.ACCOUNT, account.name, false, account.archived)
    }

    override suspend fun saveCategory(category: Category, now: Instant) {
        categories.value = categories.value.filterNot { it.id == category.id } + category
        nodes[category.id] = NodeSummary(category.id, NodeType.CATEGORY, category.name, false, category.archived)
    }

    override suspend fun saveBudget(budget: Budget) {
        budgets.value = budgets.value.filterNot { it.id == budget.id } + budget
    }

    override suspend fun deleteBudget(id: NodeId) {
        budgets.value = budgets.value.filterNot { it.id == id }
    }

    override fun observeTotalBudgets(): Flow<List<TotalBudget>> = totalBudgets
    override suspend fun saveTotalBudget(budget: TotalBudget) {
        totalBudgets.value = totalBudgets.value.filterNot { it.id == budget.id } + budget
    }
    override suspend fun deleteTotalBudget(id: NodeId) {
        totalBudgets.value = totalBudgets.value.filterNot { it.id == id }
    }

    override fun observeRecurring(): Flow<List<RecurringPayment>> = recurring
    override fun observeRecurringBookings(): Flow<List<RecurringBooking>> = bookings
    override suspend fun recurring(id: NodeId) = recurring.value.firstOrNull { it.id == id }
    override suspend fun saveRecurring(payment: RecurringPayment, now: Instant) {
        recurring.value = recurring.value.filterNot { it.id == payment.id } + payment
        nodes[payment.id] = NodeSummary(payment.id, NodeType.RECURRING, payment.name, false, !payment.active)
    }
    override suspend fun deleteRecurring(id: NodeId) {
        recurring.value = recurring.value.filterNot { it.id == id }
        bookings.value = bookings.value.filterNot { it.recurringId == id }
        nodes.remove(id)
    }
    override suspend fun bookRecurring(tx: LedgerTransaction, recurringId: NodeId, dueDate: LocalDate, now: Instant) {
        check(bookings.value.none { it.recurringId == recurringId && it.dueDate == dueDate }) { "PK-Verletzung" }
        saveTransaction(tx, now)
        bookings.value = bookings.value + RecurringBooking(recurringId, dueDate, RecurringStatus.BOOKED, tx.id)
    }
    override suspend fun skipRecurring(recurringId: NodeId, dueDate: LocalDate) {
        check(bookings.value.none { it.recurringId == recurringId && it.dueDate == dueDate }) { "PK-Verletzung" }
        bookings.value = bookings.value + RecurringBooking(recurringId, dueDate, RecurringStatus.SKIPPED, null)
    }
    override suspend fun clearRecurringBooking(recurringId: NodeId, dueDate: LocalDate) {
        bookings.value = bookings.value.filterNot { it.recurringId == recurringId && it.dueDate == dueDate }
    }
    override suspend fun recurringBookingOf(transactionId: NodeId) =
        bookings.value.firstOrNull { it.transactionId == transactionId }
    override suspend fun restoreRecurringBooking(booking: RecurringBooking) {
        bookings.value = bookings.value + booking
    }
}

class FakeLinkRepository(private val nodes: MutableMap<NodeId, NodeSummary>) : LinkRepository {
    val links = MutableStateFlow<List<NodeLink>>(emptyList())

    /** Bildet `ON DELETE CASCADE` der link-Tabelle nach. */
    fun cascade(id: NodeId) {
        links.value = links.value.filterNot { it.from == id || it.to == id }
    }

    override suspend fun link(link: NodeLink, now: Instant) {
        links.value = links.value + link
    }

    override suspend fun unlink(id: NodeId) {
        links.value = links.value.filterNot { it.id == id }
    }

    override fun observeLinksOf(nodeId: NodeId) =
        links.map { list -> list.filter { it.from == nodeId || it.to == nodeId } }

    override suspend fun node(id: NodeId): NodeSummary? = nodes[id]

    override suspend fun linkExists(from: NodeId, to: NodeId, relation: LinkRelation) =
        links.value.any { it.from == from && it.to == to && it.relation == relation }

    override suspend fun linksOf(nodeId: NodeId) = links.value.filter { it.from == nodeId || it.to == nodeId }

    override fun observeNeighbors(nodeId: NodeId) = links.map { list ->
        list.filter { it.from == nodeId || it.to == nodeId }.mapNotNull { l ->
            val other = if (l.from == nodeId) l.to else l.from
            nodes[other]?.let { LinkedNode(l, it, outgoing = l.from == nodeId) }
        }
    }

    override suspend fun search(query: String, types: Set<NodeType>, limit: Int) =
        nodes.values.filter { (types.isEmpty() || it.type in types) && it.title.contains(query, ignoreCase = true) }.take(limit)

    override fun observeAllLinks() = links

    override fun observeNodes(types: Set<NodeType>) = links.map { nodes.values.filter { types.isEmpty() || it.type in types } }
}

class FakeSettingsRepository : SettingsRepository {
    val values = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observe(key: String) = values.map { it[key] }
    override suspend fun get(key: String) = values.value[key]
    override suspend fun put(key: String, value: String) {
        values.value = values.value + (key to value)
    }
}
