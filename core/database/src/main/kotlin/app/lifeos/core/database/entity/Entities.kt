package app.lifeos.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// Muss exakt SchemaSql.TABLE_* / INDEX_* entsprechen (geprüft durch SchemaReferenceTest).
// Enums werden als TEXT gespeichert (ADR-005); das Mapping erfolgt in Mappers.kt.

@Entity(tableName = "node", indices = [Index("type")])
data class NodeEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "is_sample", defaultValue = "0") val isSample: Boolean,
)

@Entity(
    tableName = "link",
    foreignKeys = [
        ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["from_node_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["to_node_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["from_node_id", "to_node_id", "relation"], unique = true),
        Index("to_node_id"),
    ],
)
data class LinkEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "from_node_id") val fromNodeId: String,
    @ColumnInfo(name = "to_node_id") val toNodeId: String,
    @ColumnInfo(name = "relation") val relation: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "account",
    foreignKeys = [
        ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["node_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class AccountEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "opening_balance_minor") val openingBalanceMinor: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "category",
    foreignKeys = [
        ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["node_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["node_id"], childColumns = ["parent_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("parent_id")],
)
data class CategoryEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "ledger_transaction",
    foreignKeys = [
        ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["node_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["node_id"], childColumns = ["account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["node_id"], childColumns = ["counter_account_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("booking_date"), Index("account_id"), Index("counter_account_id")],
)
data class TransactionEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "counter_account_id") val counterAccountId: String?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency") val currency: String,
    /** ISO `yyyy-MM-dd` (ADR-006). */
    @ColumnInfo(name = "booking_date") val bookingDate: String,
    @ColumnInfo(name = "payee") val payee: String?,
    @ColumnInfo(name = "note") val note: String?,
)

@Entity(
    tableName = "transaction_split",
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["node_id"], childColumns = ["transaction_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["node_id"], childColumns = ["category_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("transaction_id"), Index("category_id")],
)
data class SplitEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "memo") val memo: String?,
    @ColumnInfo(name = "position") val position: Int,
)

@Entity(
    tableName = "budget",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["node_id"], childColumns = ["category_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["category_id", "valid_from"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    /** `yyyy-MM`. */
    @ColumnInfo(name = "valid_from") val validFrom: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency") val currency: String,
)

@Entity(tableName = "app_setting")
data class SettingEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
)

// ───────────── Abfrageergebnisse ─────────────

data class AccountRow(
    @Embedded val account: AccountEntity,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
)

data class CategoryRow(
    @Embedded val category: CategoryEntity,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
)

data class TransactionWithSplits(
    @Embedded val transaction: TransactionEntity,
    @ColumnInfo(name = "is_sample") val isSample: Boolean,
    @Relation(parentColumn = "node_id", entityColumn = "transaction_id")
    val splits: List<SplitEntity>,
)

data class SplitViolationRow(
    @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "split_sum") val splitSum: Long,
    @ColumnInfo(name = "split_count") val splitCount: Int,
)

data class OrphanNodeRow(
    @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "type") val type: String,
)
