package app.lifeos.core.database.repository

import app.lifeos.core.database.entity.AccountEntity
import app.lifeos.core.database.entity.AccountRow
import app.lifeos.core.database.entity.BudgetEntity
import app.lifeos.core.database.entity.CategoryEntity
import app.lifeos.core.database.entity.CategoryRow
import app.lifeos.core.database.entity.LinkEntity
import app.lifeos.core.database.entity.NodeEntity
import app.lifeos.core.database.entity.RecurringBookingEntity
import app.lifeos.core.database.entity.RecurringRow
import app.lifeos.core.database.entity.TotalBudgetEntity
import app.lifeos.core.database.entity.SplitEntity
import app.lifeos.core.database.entity.TransactionEntity
import app.lifeos.core.database.entity.TransactionWithSplits
import app.lifeos.core.model.Account
import app.lifeos.core.model.AccountKind
import app.lifeos.core.model.Budget
import app.lifeos.core.model.Category
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeLink
import app.lifeos.core.model.NodeSummary
import app.lifeos.core.model.NodeType
import app.lifeos.core.model.RecurringBooking
import app.lifeos.core.model.RecurringPayment
import app.lifeos.core.model.RecurringStatus
import app.lifeos.core.model.TotalBudget
import app.lifeos.core.model.Split
import app.lifeos.core.model.TransactionKind
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// Unbekannte Enum-Werte führen bewusst zu einer Exception: Die Trigger lassen nur gültige Werte zu,
// ein Fehler hier wäre ein Programmierfehler und darf nicht still umgedeutet werden.

internal fun AccountRow.toDomain() = Account(
    id = NodeId(account.nodeId),
    name = title,
    kind = AccountKind.valueOf(account.kind),
    currency = CurrencyCode(account.currency),
    openingBalanceMinor = account.openingBalanceMinor,
    sortOrder = account.sortOrder,
    archived = archivedAt != null,
)

internal fun CategoryRow.toDomain() = Category(
    id = NodeId(category.nodeId),
    name = title,
    direction = CategoryDirection.valueOf(category.direction),
    parentId = category.parentId?.let(::NodeId),
    sortOrder = category.sortOrder,
    archived = archivedAt != null,
)

internal fun TransactionWithSplits.toDomain() = LedgerTransaction(
    id = NodeId(transaction.nodeId),
    kind = TransactionKind.valueOf(transaction.kind),
    accountId = NodeId(transaction.accountId),
    counterAccountId = transaction.counterAccountId?.let(::NodeId),
    amount = Money(transaction.amountMinor, CurrencyCode(transaction.currency)),
    bookingDate = LocalDate.parse(transaction.bookingDate),
    payee = transaction.payee,
    note = transaction.note,
    splits = splits.sortedBy { it.position }.map {
        Split(NodeId(it.id), NodeId(it.categoryId), it.amountMinor, it.memo)
    },
    isSample = isSample,
    recurringId = recurringId?.let(::NodeId),
)

internal fun BudgetEntity.toDomain() = Budget(
    id = NodeId(id),
    categoryId = NodeId(categoryId),
    validFrom = YearMonth.parse(validFrom),
    amountMinor = amountMinor,
    currency = CurrencyCode(currency),
)

internal fun LinkEntity.toDomain() = NodeLink(
    id = NodeId(id),
    from = NodeId(fromNodeId),
    to = NodeId(toNodeId),
    relation = LinkRelation.valueOf(relation),
)

internal fun NodeEntity.toSummary() = NodeSummary(
    id = NodeId(id),
    type = NodeType.valueOf(type),
    title = title,
    isSample = isSample,
    archived = archivedAt != null,
)

internal fun nodeEntity(id: NodeId, type: NodeType, title: String, now: Instant, archived: Boolean, isSample: Boolean = false) =
    NodeEntity(
        id = id.value,
        type = type.name,
        title = title,
        createdAt = now.toEpochMilli(),
        updatedAt = now.toEpochMilli(),
        archivedAt = if (archived) now.toEpochMilli() else null,
        isSample = isSample,
    )

internal fun Account.toEntity() = AccountEntity(
    nodeId = id.value,
    kind = kind.name,
    currency = currency.value,
    openingBalanceMinor = openingBalanceMinor,
    sortOrder = sortOrder,
)

internal fun Category.toEntity() = CategoryEntity(
    nodeId = id.value,
    parentId = parentId?.value,
    direction = direction.name,
    sortOrder = sortOrder,
)

internal fun LedgerTransaction.toEntity() = TransactionEntity(
    nodeId = id.value,
    kind = kind.name,
    accountId = accountId.value,
    counterAccountId = counterAccountId?.value,
    amountMinor = amount.minor,
    currency = amount.currency.value,
    bookingDate = bookingDate.toString(), // ISO yyyy-MM-dd
    payee = payee,
    note = note,
)

internal fun LedgerTransaction.splitEntities() = splits.mapIndexed { index, s ->
    SplitEntity(
        id = s.id.value,
        transactionId = id.value,
        categoryId = s.categoryId.value,
        amountMinor = s.amountMinor,
        memo = s.memo,
        position = index,
    )
}

internal fun Budget.toEntity() = BudgetEntity(
    id = id.value,
    categoryId = categoryId.value,
    validFrom = validFrom.toString(), // yyyy-MM
    amountMinor = amountMinor,
    currency = currency.value,
)

internal fun LedgerTransaction.nodeTitle(): String =
    payee ?: when (kind) {
        TransactionKind.EXPENSE -> "Ausgabe"
        TransactionKind.INCOME -> "Einnahme"
        TransactionKind.TRANSFER -> "Umbuchung"
        TransactionKind.REFUND -> "Erstattung"
        TransactionKind.CORRECTION_IN, TransactionKind.CORRECTION_OUT -> "Kontostand-Korrektur"
    }

// ───── v2 ─────

internal fun TotalBudgetEntity.toDomain() = TotalBudget(NodeId(id), YearMonth.parse(validFrom), amountMinor, CurrencyCode(currency))

internal fun TotalBudget.toEntity() = TotalBudgetEntity(id.value, validFrom.toString(), amountMinor, currency.value)

internal fun RecurringRow.toDomain() = RecurringPayment(
    id = NodeId(payment.nodeId),
    name = title,
    kind = TransactionKind.valueOf(payment.kind),
    accountId = NodeId(payment.accountId),
    categoryId = NodeId(payment.categoryId),
    amount = Money(payment.amountMinor, CurrencyCode(payment.currency)),
    intervalMonths = payment.intervalMonths,
    anchorDate = LocalDate.parse(payment.anchorDate),
    endDate = payment.endDate?.let(LocalDate::parse),
    payee = payment.payee,
    note = payment.note,
    active = payment.active,
    isFixedCost = payment.isFixedCost,
)

internal fun RecurringPayment.toEntity() = app.lifeos.core.database.entity.RecurringPaymentEntity(
    nodeId = id.value,
    kind = kind.name,
    accountId = accountId.value,
    categoryId = categoryId.value,
    amountMinor = amount.minor,
    currency = amount.currency.value,
    intervalMonths = intervalMonths,
    anchorDate = anchorDate.toString(),
    endDate = endDate?.toString(),
    payee = payee,
    note = note,
    active = active,
    isFixedCost = isFixedCost,
)

internal fun RecurringBookingEntity.toDomain() = RecurringBooking(
    recurringId = NodeId(recurringId),
    dueDate = LocalDate.parse(dueDate),
    status = RecurringStatus.valueOf(status),
    transactionId = transactionId?.let(::NodeId),
)

internal fun RecurringBooking.toEntity() = RecurringBookingEntity(
    recurringId = recurringId.value,
    dueDate = dueDate.toString(),
    status = status.name,
    transactionId = transactionId?.value,
)
