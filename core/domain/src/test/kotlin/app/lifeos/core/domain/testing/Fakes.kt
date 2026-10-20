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

    override fun observeAccounts(): Flow<List<Account>> = accounts
    override fun observeCategories(): Flow<List<Category>> = categories
    override fun observeBudgets(): Flow<List<Budget>> = budgets
    override fun observeTransactions(from: LocalDate, to: LocalDate) = transactions.map { list ->
        list.filter { !it.bookingDate.isBefore(from) && !it.bookingDate.isAfter(to) }
            .sortedByDescending { it.bookingDate }
    }
    override fun observeAllTransactions(): Flow<List<LedgerTransaction>> = transactions

    override suspend fun accounts() = accounts.value
    override suspend fun categories() = categories.value
    override suspend fun transaction(id: NodeId) = transactions.value.firstOrNull { it.id == id }

    override suspend fun saveTransaction(tx: LedgerTransaction, now: Instant) {
        transactions.value = transactions.value.filterNot { it.id == tx.id } + tx
        nodes[tx.id] = NodeSummary(tx.id, NodeType.TRANSACTION, tx.payee ?: "Buchung", tx.isSample, false)
    }

    override suspend fun deleteTransaction(id: NodeId) {
        transactions.value = transactions.value.filterNot { it.id == id }
        nodes.remove(id)
    }

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
}

class FakeLinkRepository(private val nodes: MutableMap<NodeId, NodeSummary>) : LinkRepository {
    val links = MutableStateFlow<List<NodeLink>>(emptyList())

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
}

class FakeSettingsRepository : SettingsRepository {
    val values = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observe(key: String) = values.map { it[key] }
    override suspend fun get(key: String) = values.value[key]
    override suspend fun put(key: String, value: String) {
        values.value = values.value + (key to value)
    }
}
