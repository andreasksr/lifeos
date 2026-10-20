package app.lifeos.core.domain.ports

import app.lifeos.core.model.Account
import app.lifeos.core.model.Budget
import app.lifeos.core.model.Category
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeLink
import app.lifeos.core.model.NodeSummary
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Port der Haushaltskasse. Implementiert in `:core:database`.
 * Alle Schreiboperationen sind atomar (eine DB-Transaktion je Aufruf).
 */
interface LedgerRepository {
    fun observeAccounts(): Flow<List<Account>>
    fun observeCategories(): Flow<List<Category>>
    fun observeBudgets(): Flow<List<Budget>>

    /** Buchungen mit `from ≤ bookingDate ≤ to`, neueste zuerst. */
    fun observeTransactions(from: LocalDate, to: LocalDate): Flow<List<LedgerTransaction>>

    /** Alle Buchungen – Grundlage der Saldenberechnung. */
    fun observeAllTransactions(): Flow<List<LedgerTransaction>>

    suspend fun accounts(): List<Account>
    suspend fun categories(): List<Category>
    suspend fun transaction(id: NodeId): LedgerTransaction?

    /** Legt an oder ersetzt vollständig (inkl. Splits). Erwartet eine validierte Buchung. */
    suspend fun saveTransaction(tx: LedgerTransaction, now: Instant)
    suspend fun deleteTransaction(id: NodeId)

    suspend fun saveAccount(account: Account, now: Instant)
    suspend fun saveCategory(category: Category, now: Instant)
    suspend fun saveBudget(budget: Budget)
    suspend fun deleteBudget(id: NodeId)
}

interface LinkRepository {
    suspend fun link(link: NodeLink, now: Instant)
    suspend fun unlink(id: NodeId)
    fun observeLinksOf(nodeId: NodeId): Flow<List<NodeLink>>
    suspend fun node(id: NodeId): NodeSummary?
    suspend fun linkExists(from: NodeId, to: NodeId, relation: LinkRelation): Boolean
}

interface SettingsRepository {
    fun observe(key: String): Flow<String?>
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
}

/** Prüft Regeln, die SQLite-Trigger nicht zeilenweise erzwingen können. */
interface IntegrityChecker {
    suspend fun run(): IntegrityReport
}

data class IntegrityReport(
    val checkedTransactions: Int,
    val violations: List<IntegrityViolation>,
) {
    val isHealthy: Boolean get() = violations.isEmpty()
}

data class IntegrityViolation(val rule: String, val nodeId: String, val detail: String)
