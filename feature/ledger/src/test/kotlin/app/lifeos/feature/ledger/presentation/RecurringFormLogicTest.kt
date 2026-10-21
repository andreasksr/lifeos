package app.lifeos.feature.ledger.presentation

import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.RecurringPayment
import app.lifeos.core.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringFormLogicTest {
    private fun id(n: Int) = NodeId("00000000-0000-4000-8000-%012d".format(n))
    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun `leeres formular meldet alle pflichtfelder`() {
        val r = RecurringFormLogic.toDraft(RecurringForm(anchorDate = today)) as RecurringFormResult.Invalid
        assertEquals(setOf(EditorField.PAYEE, EditorField.AMOUNT, EditorField.ACCOUNT, EditorField.CATEGORY), r.errors.keys)
    }

    @Test
    fun `gueltiges formular und rundreise`() {
        val form = RecurringForm(name = "Miete", amountText = "950", accountId = id(1), categoryId = id(2), intervalMonths = 3, anchorDate = today)
        val draft = (RecurringFormLogic.toDraft(form) as RecurringFormResult.Valid).draft
        assertEquals(95_000L, draft.amountMinor)
        assertEquals(3, draft.intervalMonths)
        val p = RecurringPayment(id(9), "Miete", TransactionKind.EXPENSE, id(1), id(2), Money.eur(95_000), 3, today)
        assertEquals("950,00", RecurringFormLogic.fromPayment(p).amountText)
    }

    @Test
    fun `enddatum vor beginn`() {
        val form = RecurringForm(name = "X", amountText = "1", accountId = id(1), categoryId = id(2), anchorDate = today, endDate = today.minusDays(1))
        assertTrue((RecurringFormLogic.toDraft(form) as RecurringFormResult.Invalid).errors.containsKey(EditorField.DATE))
    }
}
