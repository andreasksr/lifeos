package app.lifeos.feature.ledger

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.ledger.CreateAccount
import app.lifeos.core.domain.ledger.CreateCategory
import app.lifeos.core.domain.ledger.DeleteTransaction
import app.lifeos.core.domain.ledger.ObserveLedgerMonth
import app.lifeos.core.domain.ledger.RecordTransaction
import app.lifeos.core.domain.ledger.SetBudget
import app.lifeos.core.domain.ports.LedgerRepository

/**
 * Alles, was das Modul Haushaltskasse von außen braucht. Wird im `AppContainer` erzeugt.
 * Das Feature kennt nur Domänen-Schnittstellen – nie Room.
 */
class LedgerDependencies(
    val repository: LedgerRepository,
    val clock: LifeClock,
    ids: IdGenerator,
) {
    val observeMonth = ObserveLedgerMonth(repository)
    val record = RecordTransaction(repository, ids, clock)
    val delete = DeleteTransaction(repository)
    val setBudget = SetBudget(repository, ids)
    val createAccount = CreateAccount(repository, ids, clock)
    val createCategory = CreateCategory(repository, ids, clock)
}
