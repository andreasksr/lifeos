package app.lifeos.core.database.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import app.lifeos.core.database.LifeOsDatabase
import app.lifeos.core.database.entity.LinkEntity
import app.lifeos.core.database.entity.SettingEntity
import app.lifeos.core.domain.ports.IntegrityChecker
import app.lifeos.core.domain.ports.IntegrityReport
import app.lifeos.core.domain.ports.IntegrityViolation
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class RoomLedgerRepository(private val db: LifeOsDatabase) : LedgerRepository {
    private val dao get() = db.ledgerDao()

    override fun observeAccounts(): Flow<List<Account>> =
        dao.observeAccounts().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeCategories(): Flow<List<Category>> =
        dao.observeCategories().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeBudgets(): Flow<List<Budget>> =
        dao.observeBudgets().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeTransactions(from: LocalDate, to: LocalDate): Flow<List<LedgerTransaction>> =
        dao.observeTransactions(from.toString(), to.toString())
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeAllTransactions(): Flow<List<LedgerTransaction>> =
        dao.observeAllTransactions().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun accounts(): List<Account> = dao.accounts().map { it.toDomain() }
    override suspend fun categories(): List<Category> = dao.categories().map { it.toDomain() }
    override suspend fun transaction(id: NodeId): LedgerTransaction? = dao.transaction(id.value)?.toDomain()

    override suspend fun saveTransaction(tx: LedgerTransaction, now: Instant) {
        dao.saveTransaction(
            node = nodeEntity(tx.id, NodeType.TRANSACTION, tx.nodeTitle(), now, archived = false, isSample = tx.isSample),
            tx = tx.toEntity(),
            splits = tx.splitEntities(),
        )
    }

    override suspend fun deleteTransaction(id: NodeId) {
        // Kaskade: ledger_transaction → transaction_split, link
        db.nodeDao().delete(id.value)
    }

    override suspend fun saveAccount(account: Account, now: Instant) {
        dao.saveAccount(nodeEntity(account.id, NodeType.ACCOUNT, account.name, now, account.archived), account.toEntity())
    }

    override suspend fun saveCategory(category: Category, now: Instant) {
        dao.saveCategory(nodeEntity(category.id, NodeType.CATEGORY, category.name, now, category.archived), category.toEntity())
    }

    override suspend fun saveBudget(budget: Budget) = dao.saveBudget(budget.toEntity())

    override suspend fun deleteBudget(id: NodeId) {
        dao.deleteBudget(id.value)
    }

    override fun observeTotalBudgets(): Flow<List<TotalBudget>> =
        dao.observeTotalBudgets().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun saveTotalBudget(budget: TotalBudget) = dao.saveTotalBudget(budget.toEntity())

    override suspend fun deleteTotalBudget(id: NodeId) {
        dao.deleteTotalBudget(id.value)
    }

    override fun observeRecurring(): Flow<List<RecurringPayment>> =
        dao.observeRecurring().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeRecurringBookings(): Flow<List<RecurringBooking>> =
        dao.observeRecurringBookings().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun recurring(id: NodeId): RecurringPayment? = dao.recurring(id.value)?.toDomain()

    override suspend fun saveRecurring(payment: RecurringPayment, now: Instant) {
        dao.saveRecurring(
            nodeEntity(payment.id, NodeType.RECURRING, payment.name, now, archived = !payment.active),
            payment.toEntity(),
        )
    }

    override suspend fun deleteRecurring(id: NodeId) {
        db.nodeDao().delete(id.value) // Kaskade: recurring_payment → recurring_booking; Buchungen bleiben
    }

    override suspend fun bookRecurring(tx: LedgerTransaction, recurringId: NodeId, dueDate: LocalDate, now: Instant) {
        dao.bookRecurring(
            node = nodeEntity(tx.id, NodeType.TRANSACTION, tx.nodeTitle(), now, archived = false, isSample = tx.isSample),
            tx = tx.toEntity(),
            splits = tx.splitEntities(),
            booking = RecurringBooking(recurringId, dueDate, RecurringStatus.BOOKED, tx.id).toEntity(),
        )
    }

    override suspend fun skipRecurring(recurringId: NodeId, dueDate: LocalDate) {
        dao.insertRecurringBooking(RecurringBooking(recurringId, dueDate, RecurringStatus.SKIPPED, null).toEntity())
    }

    override suspend fun clearRecurringBooking(recurringId: NodeId, dueDate: LocalDate) {
        dao.deleteRecurringBooking(recurringId.value, dueDate.toString())
    }

    override suspend fun recurringBookingOf(transactionId: NodeId): RecurringBooking? =
        dao.recurringBookingOf(transactionId.value)?.toDomain()

    override suspend fun restoreRecurringBooking(booking: RecurringBooking) {
        dao.insertRecurringBooking(booking.toEntity())
    }
}

class RoomLinkRepository(private val db: LifeOsDatabase) : LinkRepository {
    override suspend fun link(link: NodeLink, now: Instant) {
        db.linkDao().insert(
            LinkEntity(link.id.value, link.from.value, link.to.value, link.relation.name, now.toEpochMilli()),
        )
    }

    override suspend fun unlink(id: NodeId) {
        db.linkDao().delete(id.value)
    }

    override fun observeLinksOf(nodeId: NodeId): Flow<List<NodeLink>> =
        db.linkDao().observeLinksOf(nodeId.value).map { rows -> rows.map { it.toDomain() } }

    override suspend fun node(id: NodeId): NodeSummary? = db.nodeDao().get(id.value)?.toSummary()

    override suspend fun linkExists(from: NodeId, to: NodeId, relation: LinkRelation): Boolean =
        db.linkDao().exists(from.value, to.value, relation.name)

    override suspend fun linksOf(nodeId: NodeId): List<NodeLink> = db.linkDao().linksOf(nodeId.value).map { it.toDomain() }

    override fun observeNeighbors(nodeId: NodeId): Flow<List<LinkedNode>> =
        db.linkDao().observeNeighbors(nodeId.value).map { rows ->
            rows.map { r ->
                LinkedNode(
                    link = NodeLink(NodeId(r.linkId), NodeId(r.from), NodeId(r.to), LinkRelation.valueOf(r.relation)),
                    node = r.node.toSummary(),
                    outgoing = r.from == nodeId.value,
                )
            }
        }.distinctUntilChanged()

    override suspend fun search(query: String, types: Set<NodeType>, limit: Int): List<NodeSummary> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return db.linkDao().nodesOfTypes(types.isEmpty(), types.map { it.name })
            .asSequence()
            .filter { it.title.lowercase().contains(needle) }
            .take(limit)
            .map { it.toSummary() }
            .toList()
    }

    override fun observeAllLinks(): Flow<List<NodeLink>> =
        db.linkDao().observeAll().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeNodes(types: Set<NodeType>): Flow<List<NodeSummary>> =
        db.linkDao().observeNodesOfTypes(types.isEmpty(), types.map { it.name })
            .map { rows -> rows.map { it.toSummary() } }
            .distinctUntilChanged()
}

class RoomSettingsRepository(private val db: LifeOsDatabase) : SettingsRepository {
    override fun observe(key: String): Flow<String?> = db.settingsDao().observe(key).distinctUntilChanged()
    override suspend fun get(key: String): String? = db.settingsDao().get(key)
    override suspend fun put(key: String, value: String) = db.settingsDao().put(SettingEntity(key, value))
}

class RoomIntegrityChecker(private val db: LifeOsDatabase) : IntegrityChecker {
    override suspend fun run(): IntegrityReport {
        val dao = db.integrityDao()
        val violations = buildList {
            dao.splitViolations().forEach {
                add(
                    IntegrityViolation(
                        rule = "SPLIT_SUM",
                        nodeId = it.nodeId,
                        detail = "${it.kind}: Betrag ${it.amountMinor}, Splits ${it.splitSum} (${it.splitCount})",
                    ),
                )
            }
            dao.orphanNodes().forEach {
                add(IntegrityViolation("ORPHAN_NODE", it.nodeId, "Node vom Typ ${it.type} ohne Fachdatensatz"))
            }
            dao.foreignKeyCheck(SimpleSQLiteQuery("PRAGMA foreign_key_check")).forEach {
                add(IntegrityViolation("FOREIGN_KEY", "${it.table}#${it.rowId}", "verweist ins Leere: ${it.parent}"))
            }
        }
        return IntegrityReport(dao.transactionCount(), violations)
    }
}
