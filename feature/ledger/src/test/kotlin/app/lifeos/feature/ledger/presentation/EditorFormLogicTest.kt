package app.lifeos.feature.ledger.presentation

import app.lifeos.core.domain.ledger.LedgerError
import app.lifeos.core.domain.ledger.SplitDraft
import app.lifeos.core.model.Account
import app.lifeos.core.model.AccountKind
import app.lifeos.core.model.Category
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.Split
import app.lifeos.core.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EditorFormLogicTest {

    private fun id(n: Int) = NodeId("00000000-0000-4000-8000-%012d".format(n))
    private val today = LocalDate.of(2026, 10, 20)
    private val giro = Account(id(1), "Giro", AccountKind.CHECKING, CurrencyCode.EUR, 0, 0)
    private val spar = Account(id(2), "Spar", AccountKind.SAVINGS, CurrencyCode.EUR, 0, 1)
    private val food = Category(id(10), "Lebensmittel", CategoryDirection.EXPENSE, sortOrder = 1)
    private val salary = Category(id(11), "Gehalt", CategoryDirection.INCOME, sortOrder = 2)
    private val categories = listOf(food, salary)

    @Test
    fun `startformular waehlt erstes aktives konto und heute`() {
        val form = EditorFormLogic.initial(listOf(giro.copy(archived = true), spar), today)
        assertEquals(spar.id, form.accountId)
        assertEquals(today, form.date)
        assertEquals(TransactionKind.EXPENSE, form.kind)
    }

    @Test
    fun `gueltige ausgabe wird zu genau einem split`() {
        val form = EditorFormLogic.initial(listOf(giro), today).copy(amountText = "89,90", categoryId = food.id, payee = "ATU")
        val draft = (EditorFormLogic.toDraft(form, editId = null) as FormResult.Valid).draft
        assertEquals(8_990L, draft.amountMinor)
        assertEquals(listOf(SplitDraft(food.id, 8_990)), draft.splits)
        assertNull(draft.counterAccountId)
    }

    @Test
    fun `fehlende pflichtfelder werden alle gleichzeitig gemeldet`() {
        val result = EditorFormLogic.toDraft(EditorForm(date = today), editId = null) as FormResult.Invalid
        assertEquals(setOf(EditorField.AMOUNT, EditorField.ACCOUNT, EditorField.CATEGORY), result.errors.keys)
    }

    @Test
    fun `betrag null oder unklar wird abgelehnt`() {
        val base = EditorFormLogic.initial(listOf(giro), today).copy(categoryId = food.id)
        assertTrue(EditorFormLogic.toDraft(base.copy(amountText = "0"), null) is FormResult.Invalid)
        assertTrue(EditorFormLogic.toDraft(base.copy(amountText = "-5"), null) is FormResult.Invalid)
        val r = EditorFormLogic.toDraft(base.copy(amountText = "1,234"), null) as FormResult.Invalid
        assertEquals("Höchstens zwei Nachkommastellen.", r.errors[EditorField.AMOUNT])
    }

    @Test
    fun `umbuchung braucht verschiedenes zielkonto und keine kategorie`() {
        val form = EditorFormLogic.initial(listOf(giro, spar), today).copy(kind = TransactionKind.TRANSFER, amountText = "500")
        assertEquals(setOf(EditorField.COUNTER_ACCOUNT), (EditorFormLogic.toDraft(form, null) as FormResult.Invalid).errors.keys)
        assertTrue(EditorFormLogic.toDraft(form.copy(counterAccountId = giro.id), null) is FormResult.Invalid)
        val draft = (EditorFormLogic.toDraft(form.copy(counterAccountId = spar.id), null) as FormResult.Valid).draft
        assertTrue(draft.splits.isEmpty())
        assertEquals(spar.id, draft.counterAccountId)
    }

    @Test
    fun `artwechsel setzt unpassende kategorie und zielkonto zurueck`() {
        val form = EditorForm(date = today, categoryId = food.id, counterAccountId = spar.id, kind = TransactionKind.TRANSFER)
        val expense = EditorFormLogic.changeKind(form, TransactionKind.EXPENSE, categories)
        assertEquals(food.id, expense.categoryId)
        assertNull(expense.counterAccountId)
        val income = EditorFormLogic.changeKind(expense, TransactionKind.INCOME, categories)
        assertNull(income.categoryId)
        assertEquals(listOf(salary), EditorFormLogic.categoriesFor(TransactionKind.INCOME, categories))
        assertTrue(EditorFormLogic.categoriesFor(TransactionKind.TRANSFER, categories).isEmpty())
    }

    @Test
    fun `aufgeteilte buchung behaelt ihre aufteilung beim bearbeiten`() {
        val tx = LedgerTransaction(
            id(100), TransactionKind.EXPENSE, giro.id, null, Money.eur(10_000), today, "Baumarkt", null,
            listOf(Split(id(200), food.id, 3_000), Split(id(201), id(12), 7_000)),
        )
        val form = EditorFormLogic.fromTransaction(tx)
        assertEquals("100,00", form.amountText)
        assertNull(form.categoryId)
        val draft = (EditorFormLogic.toDraft(form.copy(payee = "Bauhaus"), tx.id) as FormResult.Valid).draft
        assertEquals(listOf(SplitDraft(food.id, 3_000), SplitDraft(id(12), 7_000)), draft.splits)
        assertEquals(tx.id, draft.id)
        // Art bleibt gesperrt
        assertEquals(TransactionKind.EXPENSE, EditorFormLogic.changeKind(form, TransactionKind.INCOME, categories).kind)
    }

    @Test
    fun `jeder domaenenfehler hat eine deutsche meldung`() {
        val all = listOf(
            LedgerError.AmountNotPositive, LedgerError.MissingSplits, LedgerError.SplitsOnTransfer,
            LedgerError.SplitAmountNotPositive, LedgerError.SplitSumMismatch(1, 2),
            LedgerError.TransferNeedsCounterAccount, LedgerError.TransferSameAccount,
            LedgerError.CounterAccountOnNonTransfer, LedgerError.UnknownAccount(id(1)),
            LedgerError.ArchivedAccount(id(1)), LedgerError.UnknownCategory(id(1)),
            LedgerError.CategoryDirectionMismatch(id(1)), LedgerError.CurrencyMismatch,
            LedgerError.AmountOutOfRange, LedgerError.TextTooLong, LedgerError.NameRequired,
            LedgerError.TransactionNotFound,
        )
        all.forEach { assertTrue(EditorFormLogic.domainMessage(it).second.isNotBlank()) }
    }
}
