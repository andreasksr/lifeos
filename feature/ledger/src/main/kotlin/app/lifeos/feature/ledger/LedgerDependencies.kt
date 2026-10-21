package app.lifeos.feature.ledger

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.ledger.BookOccurrence
import app.lifeos.core.domain.ledger.CreateAccount
import app.lifeos.core.domain.ledger.CreateCategory
import app.lifeos.core.domain.ledger.DeleteRecurring
import app.lifeos.core.domain.ledger.DeleteTransaction
import app.lifeos.core.domain.ledger.ObserveLedgerMonth
import app.lifeos.core.domain.ledger.ReconcileBalance
import app.lifeos.core.domain.ledger.RecordTransaction
import app.lifeos.core.domain.ledger.RemoveCategoryBudget
import app.lifeos.core.domain.ledger.RemoveTotalBudget
import app.lifeos.core.domain.ledger.RestoreTransaction
import app.lifeos.core.domain.ledger.SaveRecurring
import app.lifeos.core.domain.ledger.SetBudget
import app.lifeos.core.domain.ledger.SetTotalBudget
import app.lifeos.core.domain.ledger.SkipOccurrence
import app.lifeos.core.domain.ledger.UnskipOccurrence
import app.lifeos.core.domain.ledger.UpdateAccount
import app.lifeos.core.domain.ledger.UpdateCategory
import app.lifeos.core.domain.ports.LedgerRepository
import app.lifeos.core.domain.ports.LinkRepository

/**
 * Alles, was das Modul Finanzen von außen braucht. Wird im `AppContainer` erzeugt.
 * Das Feature kennt nur Domänen-Schnittstellen – nie Room.
 */
class LedgerDependencies(
    val repository: LedgerRepository,
    val links: LinkRepository,
    val clock: LifeClock,
    ids: IdGenerator,
    /** Überlebt einzelne Bildschirme – z. B. für „Rückgängig" nach dem Schließen eines Formulars. */
    val background: kotlinx.coroutines.CoroutineScope,
) {
    val observeMonth = ObserveLedgerMonth(repository, clock)
    val record = RecordTransaction(repository, ids, clock)
    val delete = DeleteTransaction(repository, links)
    val restore = RestoreTransaction(repository, links, clock)
    val setBudget = SetBudget(repository, ids)
    val removeBudget = RemoveCategoryBudget(repository)
    val setTotalBudget = SetTotalBudget(repository, ids)
    val removeTotalBudget = RemoveTotalBudget(repository)
    val createAccount = CreateAccount(repository, ids, clock)
    val updateAccount = UpdateAccount(repository, clock)
    val createCategory = CreateCategory(repository, ids, clock)
    val updateCategory = UpdateCategory(repository, clock)
    val reconcile = ReconcileBalance(repository, ids, clock)
    val saveRecurring = SaveRecurring(repository, ids, clock)
    val deleteRecurring = DeleteRecurring(repository)
    val bookOccurrence = BookOccurrence(repository, ids, clock)
    val skipOccurrence = SkipOccurrence(repository)
    val unskipOccurrence = UnskipOccurrence(repository)
    val linkNodes = app.lifeos.core.domain.links.LinkNodes(links, ids, clock)
    val unlinkNodes = app.lifeos.core.domain.projects.UnlinkNodes(links)
}
