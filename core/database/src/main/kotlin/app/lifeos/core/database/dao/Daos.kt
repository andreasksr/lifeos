package app.lifeos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import app.lifeos.core.database.LifeOsDatabase
import app.lifeos.core.database.entity.AccountEntity
import app.lifeos.core.database.entity.AccountRow
import app.lifeos.core.database.entity.BudgetEntity
import app.lifeos.core.database.entity.CategoryEntity
import app.lifeos.core.database.entity.CategoryRow
import app.lifeos.core.database.entity.LinkEntity
import app.lifeos.core.database.entity.NodeEntity
import app.lifeos.core.database.entity.OrphanNodeRow
import app.lifeos.core.database.entity.SettingEntity
import app.lifeos.core.database.entity.SplitEntity
import app.lifeos.core.database.entity.SplitViolationRow
import app.lifeos.core.database.entity.TransactionEntity
import app.lifeos.core.database.entity.TransactionWithSplits
import app.lifeos.core.database.schema.SchemaSql
import kotlinx.coroutines.flow.Flow

/**
 * WICHTIG: Nodes werden NIE mit `REPLACE` geschrieben. `INSERT OR REPLACE` löscht die alte Zeile und
 * würde per Kaskade Buchungen, Splits und Links vernichten (siehe tools/schema_test.py).
 * Deshalb: explizites Insert (ABORT) oder Update.
 */
@Dao
abstract class NodeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(node: NodeEntity)

    @Query("UPDATE node SET title = :title, updated_at = :updatedAt, archived_at = :archivedAt WHERE id = :id")
    abstract suspend fun update(id: String, title: String, updatedAt: Long, archivedAt: Long?): Int

    @Query("SELECT * FROM node WHERE id = :id")
    abstract suspend fun get(id: String): NodeEntity?

    @Query("DELETE FROM node WHERE id = :id")
    abstract suspend fun delete(id: String): Int

    /** Legt an oder aktualisiert Titel/Archivstatus – Typ und Erstellzeit bleiben unverändert. */
    @Transaction
    open suspend fun save(node: NodeEntity) {
        val existing = get(node.id)
        if (existing == null) {
            insert(node)
        } else {
            check(existing.type == node.type) { "Node-Typ ist unveränderlich: ${existing.type} → ${node.type}" }
            update(node.id, node.title, node.updatedAt, node.archivedAt)
        }
    }
}

@Dao
abstract class LedgerDao(private val db: LifeOsDatabase) {

    private val nodes: NodeDao get() = db.nodeDao()


    // ───── Lesen ─────
    @Query(
        """SELECT a.*, n.title AS title, n.archived_at AS archived_at
           FROM account a JOIN node n ON n.id = a.node_id
           ORDER BY a.sort_order, n.title""",
    )
    abstract fun observeAccounts(): Flow<List<AccountRow>>

    @Query(
        """SELECT a.*, n.title AS title, n.archived_at AS archived_at
           FROM account a JOIN node n ON n.id = a.node_id
           ORDER BY a.sort_order, n.title""",
    )
    abstract suspend fun accounts(): List<AccountRow>

    @Query(
        """SELECT c.*, n.title AS title, n.archived_at AS archived_at
           FROM category c JOIN node n ON n.id = c.node_id
           ORDER BY c.sort_order, n.title""",
    )
    abstract fun observeCategories(): Flow<List<CategoryRow>>

    @Query(
        """SELECT c.*, n.title AS title, n.archived_at AS archived_at
           FROM category c JOIN node n ON n.id = c.node_id
           ORDER BY c.sort_order, n.title""",
    )
    abstract suspend fun categories(): List<CategoryRow>

    @Transaction
    @Query(
        """SELECT t.*, n.is_sample AS is_sample
           FROM ledger_transaction t JOIN node n ON n.id = t.node_id
           ORDER BY t.booking_date DESC, n.created_at DESC""",
    )
    abstract fun observeAllTransactions(): Flow<List<TransactionWithSplits>>

    @Transaction
    @Query(
        """SELECT t.*, n.is_sample AS is_sample
           FROM ledger_transaction t JOIN node n ON n.id = t.node_id
           WHERE t.booking_date BETWEEN :from AND :to
           ORDER BY t.booking_date DESC, n.created_at DESC""",
    )
    abstract fun observeTransactions(from: String, to: String): Flow<List<TransactionWithSplits>>

    @Transaction
    @Query(
        """SELECT t.*, n.is_sample AS is_sample
           FROM ledger_transaction t JOIN node n ON n.id = t.node_id
           WHERE t.node_id = :id""",
    )
    abstract suspend fun transaction(id: String): TransactionWithSplits?

    @Query("SELECT * FROM budget ORDER BY valid_from, category_id")
    abstract fun observeBudgets(): Flow<List<BudgetEntity>>

    // ───── Schreiben (Bausteine – nur über die atomaren save*-Methoden verwenden) ─────
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAccount(account: AccountEntity)

    @Update
    abstract suspend fun updateAccount(account: AccountEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertCategory(category: CategoryEntity)

    @Update
    abstract suspend fun updateCategory(category: CategoryEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertTransaction(tx: TransactionEntity)

    @Update
    abstract suspend fun updateTransaction(tx: TransactionEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSplits(splits: List<SplitEntity>)

    @Query("DELETE FROM transaction_split WHERE transaction_id = :transactionId")
    abstract suspend fun deleteSplitsOf(transactionId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertBudget(budget: BudgetEntity)

    @Update
    abstract suspend fun updateBudget(budget: BudgetEntity): Int

    @Query("DELETE FROM budget WHERE id = :id")
    abstract suspend fun deleteBudget(id: String): Int

    // ───── Schreiben (atomar) ─────
    @Transaction
    open suspend fun saveAccount(node: NodeEntity, account: AccountEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertAccount(account) else updateAccount(account)
    }

    @Transaction
    open suspend fun saveCategory(node: NodeEntity, category: CategoryEntity) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) insertCategory(category) else updateCategory(category)
    }

    /**
     * Legt eine Buchung an oder ersetzt sie vollständig. Reihenfolge beim Ändern:
     * Splits löschen → Buchung aktualisieren → Splits einfügen (sonst greifen die Trigger
     * `LIFEOS_TX_KIND_LOCKED` bzw. `LIFEOS_SPLIT_DIRECTION` zu Recht).
     */
    @Transaction
    open suspend fun saveTransaction(node: NodeEntity, tx: TransactionEntity, splits: List<SplitEntity>) {
        val isNew = nodes.get(node.id) == null
        nodes.save(node)
        if (isNew) {
            insertTransaction(tx)
        } else {
            deleteSplitsOf(tx.nodeId)
            updateTransaction(tx)
        }
        if (splits.isNotEmpty()) insertSplits(splits)
    }

    @Transaction
    open suspend fun saveBudget(budget: BudgetEntity) {
        if (updateBudget(budget) == 0) insertBudget(budget)
    }
}

@Dao
abstract class LinkDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(link: LinkEntity)

    @Query("DELETE FROM link WHERE id = :id")
    abstract suspend fun delete(id: String): Int

    @Query("SELECT * FROM link WHERE from_node_id = :nodeId OR to_node_id = :nodeId ORDER BY created_at")
    abstract fun observeLinksOf(nodeId: String): Flow<List<LinkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM link WHERE from_node_id = :from AND to_node_id = :to AND relation = :relation)")
    abstract suspend fun exists(from: String, to: String, relation: String): Boolean
}

@Dao
abstract class SettingsDao {
    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    abstract fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    abstract suspend fun get(key: String): String?

    /** REPLACE ist hier unbedenklich: `app_setting` hat keine abhängigen Tabellen. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(setting: SettingEntity)
}

data class ForeignKeyViolationRow(
    @androidx.room.ColumnInfo(name = "table") val table: String,
    @androidx.room.ColumnInfo(name = "rowid") val rowId: Long?,
    @androidx.room.ColumnInfo(name = "parent") val parent: String,
    @androidx.room.ColumnInfo(name = "fkid") val fkId: Int,
)

@Dao
abstract class IntegrityDao {
    @Query(SchemaSql.QUERY_SPLIT_VIOLATIONS)
    abstract suspend fun splitViolations(): List<SplitViolationRow>

    @Query(SchemaSql.QUERY_ORPHAN_NODES)
    abstract suspend fun orphanNodes(): List<OrphanNodeRow>

    @Query(SchemaSql.QUERY_TRANSACTION_COUNT)
    abstract suspend fun transactionCount(): Int

    /** Aufruf mit `SimpleSQLiteQuery("PRAGMA foreign_key_check")`. */
    @RawQuery
    abstract suspend fun foreignKeyCheck(query: SupportSQLiteQuery): List<ForeignKeyViolationRow>
}
