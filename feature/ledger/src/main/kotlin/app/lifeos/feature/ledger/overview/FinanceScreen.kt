package app.lifeos.feature.ledger.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.core.designsystem.component.ChartLegend
import app.lifeos.core.designsystem.component.ChartSeries
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeBarChart
import app.lifeos.core.designsystem.component.LifeDivider
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeFlowChart
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeLink
import app.lifeos.core.designsystem.component.LifeMoneyMetric
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeSpectrum
import app.lifeos.core.designsystem.component.LifeTag
import app.lifeos.core.designsystem.component.LifeToastMessage
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.MetricSize
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpectrumSegment
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.domain.ledger.CategoryLine
import app.lifeos.core.domain.ledger.LedgerMath
import app.lifeos.core.domain.ledger.LedgerMonthSnapshot
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.feature.ledger.ui.PendingLine
import app.lifeos.feature.ledger.ui.SummaryRow
import app.lifeos.feature.ledger.ui.TransactionLine
import java.time.YearMonth

data class FinanceNavigation(
    val openTransactions: (YearMonth) -> Unit,
    val openTransaction: (NodeId) -> Unit,
    val newTransaction: () -> Unit,
    val openBudgets: () -> Unit,
    val openRecurring: () -> Unit,
    val openAccounts: () -> Unit,
)

@Composable
fun FinanceRoute(viewModel: FinanceViewModel, nav: FinanceNavigation) {
    val snap by viewModel.snapshot.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            if (e.success) signal.confirm(e.text) else signal.toast(LifeToastMessage(e.text, tone = NodeTone.ALERT))
        }
    }
    val s = snap ?: return
    FinanceScreen(s, viewModel, nav)
}

@Composable
private fun FinanceScreen(s: LedgerMonthSnapshot, vm: FinanceViewModel, nav: FinanceNavigation) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("lifeos-screen"),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item(key = "top") {
            LifeTopBar(
                overline = "Finanzen",
                title = LifeFormat.monthTitle(s.month),
                subtitle = if (s.isCurrentMonth) "Aktueller Monat" else null,
                actions = {
                    if (!s.isCurrentMonth) LifeIconButton(Glyph.CALENDAR, "Zum aktuellen Monat", vm::currentMonth)
                    LifeIconButton(Glyph.CHEVRON_LEFT, "Vorheriger Monat", vm::previousMonth)
                    LifeIconButton(Glyph.CHEVRON_RIGHT, "Nächster Monat", vm::nextMonth)
                    LifeIconButton(Glyph.SEARCH, "Buchungen durchsuchen", { nav.openTransactions(s.month) })
                },
            )
        }
        if (s.containsSampleData) {
            item(key = "sample") { Row(Modifier.padding(start = ContinuumSpacing.spineRail)) { LifeTag("Enthält Beispieldaten", NodeTone.WARN) } }
        }
        item(key = "now") { NowSection(s, nav) }
        item(key = "month") { MonthSection(s) }
        item(key = "history") { HistorySection(s, vm) }
        if (s.budgetPace != null && s.cumulativeExpenses.isNotEmpty()) {
            item(key = "pace") { PaceSection(s) }
        }
        item(key = "categories") { CategoriesSection(s.expenseLines, s, nav) }
        item(key = "pending") { PendingSection(s, vm, nav) }
        item(key = "accounts") { AccountsSection(s, nav) }
        item(key = "recent") { RecentSection(s, nav) }
    }
}

@Composable
private fun NowSection(s: LedgerMonthSnapshot, nav: FinanceNavigation) {
    SpineSection(title = "Jetzt", index = 0, shape = NodeShape.RING, tone = NodeTone.ACTIVE, onOpen = nav.openAccounts, openLabel = "Konten öffnen") {
        LifeMoneyMetric(
            label = "Kontostand",
            minor = s.totalBalance.minor,
            currency = s.currency,
            size = MetricSize.HUGE,
            caption = "Gebucht · ${s.balances.count { !it.account.archived }} Konten",
            tone = if (s.totalBalance.isNegative) NodeTone.ALERT else NodeTone.NEUTRAL,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.l)) {
            val tb = s.totalBudget
            if (tb != null) {
                LifeMoneyMetric(
                    label = "Budget übrig",
                    minor = tb.remainingMinor,
                    currency = s.currency,
                    tone = if (tb.isExceeded) NodeTone.ALERT else NodeTone.ACTIVE,
                    progress = tb.usedPermille / 1000f,
                    caption = if (tb.isExceeded) "Überschritten · ${LifeFormat.percent(tb.usedPermille)}" else "${LifeFormat.percent(tb.usedPermille)} von ${MoneyText.format(tb.limitMinor, s.currency)}",
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("BUDGET", style = Continuum.type.label)
                    Text("Kein Monatsbudget", style = Continuum.type.bodyMuted)
                    LifeLink("Festlegen", nav.openBudgets)
                }
            }
            val f = s.forecast
            if (f != null) {
                val open = s.pending.count { !it.dueDate.isAfter(f.until) }
                LifeMoneyMetric(
                    label = "Voraussichtlich ${LifeFormat.numericDate(f.until).take(6)}",
                    minor = f.expectedAtMonthEnd.minor,
                    currency = s.currency,
                    tone = if (f.expectedAtMonthEnd.isNegative) NodeTone.ALERT else NodeTone.NEUTRAL,
                    caption = if (open == 0) "Keine offenen Zahlungen" else "inkl. $open offener Zahlungen",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MonthSection(s: LedgerMonthSnapshot) {
    val m = s.summary
    SpineSection(title = "Monat", index = 1, shape = NodeShape.DIAMOND) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.l)) {
            LifeMoneyMetric("Einnahmen", m.incomeMinor, Modifier.weight(1f), s.currency, tone = NodeTone.INFLOW)
            LifeMoneyMetric(
                "Ausgaben", m.expenseMinor, Modifier.weight(1f), s.currency, tone = NodeTone.OUTFLOW,
                caption = if (m.refundMinor > 0) "nach ${MoneyText.format(m.refundMinor, s.currency)} Erstattung" else null,
            )
        }
        SummaryRow(
            if (m.netMinor >= 0) "Überschuss" else "Defizit",
            MoneyText.format(m.netMinor, s.currency, showPlus = true),
            color = if (m.netMinor < 0) ContinuumColors.Alert else ContinuumColors.Mint,
        )
        if (m.expenseMinor > 0) {
            LifeSpectrum(
                segments = listOf(
                    SpectrumSegment(m.fixedExpenseMinor, ContinuumColors.Violet),
                    SpectrumSegment(m.variableExpenseMinor, ContinuumColors.Cyan),
                ),
                description = "Fixkosten ${MoneyText.format(m.fixedExpenseMinor)}, variable Kosten ${MoneyText.format(m.variableExpenseMinor)}",
            )
            Row(Modifier.fillMaxWidth()) {
                LegendDot(ContinuumColors.Violet, "Fix ${MoneyText.format(m.fixedExpenseMinor, s.currency)}", Modifier.weight(1f))
                LegendDot(ContinuumColors.Cyan, "Variabel ${MoneyText.format(m.variableExpenseMinor, s.currency)}", Modifier.weight(1f))
            }
            Text(
                "Fixkostenquote ${LifeFormat.percent(LedgerMath.permille(m.fixedExpenseMinor, m.expenseMinor))} der Ausgaben" +
                    if (s.fixedMonthlyPlanMinor > 0) " · geplant ${MoneyText.format(s.fixedMonthlyPlanMinor, s.currency)}/Monat" else "",
                style = Continuum.type.small,
            )
        }
    }
}

@Composable
fun LegendDot(color: androidx.compose.ui.graphics.Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(text, style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HistorySection(s: LedgerMonthSnapshot, vm: FinanceViewModel) {
    val labels = s.history.map { LifeFormat.monthAxis(it.month) }
    val series = listOf(
        ChartSeries("Einnahmen", s.history.map { it.incomeMinor }, ContinuumColors.Mint),
        ChartSeries("Ausgaben", s.history.map { it.expenseMinor.coerceAtLeast(0) }, ContinuumColors.Violet),
    )
    SpineSection(title = "Verlauf · 6 Monate", index = 2, shape = NodeShape.LINKED) {
        if (s.history.all { it.incomeMinor == 0L && it.expenseMinor == 0L }) {
            LifeEmpty("Noch keine Buchungen in den letzten sechs Monaten.", glyph = Glyph.CHART)
        } else {
            LifeBarChart(
                labels = labels,
                series = series,
                selectedIndex = s.history.indexOfFirst { it.month == s.month },
                onSelect = { i -> vm.showMonth(s.history[i].month) },
                description = s.history.joinToString("; ") {
                    "${LifeFormat.monthTitle(it.month)}: Einnahmen ${MoneyText.format(it.incomeMinor)}, Ausgaben ${MoneyText.format(it.expenseMinor)}"
                },
            )
            ChartLegend(series)
        }
    }
}

@Composable
private fun PaceSection(s: LedgerMonthSnapshot) {
    val pace = s.budgetPace ?: return
    val days = s.month.lengthOfMonth()
    val actual = s.cumulativeExpenses.map { it.valueMinor } + List(days - s.cumulativeExpenses.size) { s.cumulativeExpenses.last().valueMinor }
    val shownActual = if (s.isCurrentMonth) s.cumulativeExpenses.map { it.valueMinor } else actual
    val labels = (1..days).map { d -> if (d == 1 || d % 7 == 1 || d == days) d.toString() else "" }
    val series = listOf(
        ChartSeries("Ausgaben kumuliert", shownActual, ContinuumColors.Cyan, area = true),
        ChartSeries("Budget-Soll", pace.map { it.valueMinor }, ContinuumColors.Muted, dashed = true),
    )
    SpineSection(title = "Budgetentwicklung", index = 3, shape = NodeShape.DOT, tone = if (s.totalBudget?.isExceeded == true) NodeTone.ALERT else NodeTone.NEUTRAL) {
        LifeFlowChart(
            labels = labels,
            series = series,
            height = 140.dp,
            description = "Kumulierte Ausgaben ${MoneyText.format(shownActual.lastOrNull() ?: 0)} gegenüber Budget-Soll",
        )
        ChartLegend(series)
    }
}

@Composable
private fun CategoriesSection(lines: List<CategoryLine>, s: LedgerMonthSnapshot, nav: FinanceNavigation) {
    SpineSection(
        title = "Ausgaben nach Kategorie", index = 4, shape = NodeShape.DIAMOND, tone = NodeTone.OUTFLOW,
        onOpen = nav.openBudgets, openLabel = "Budgets öffnen",
        trailing = { LifeLink("Budgets", nav.openBudgets) },
    ) {
        if (lines.isEmpty()) {
            LifeEmpty("Noch keine Ausgaben in diesem Monat.", glyph = Glyph.WALLET)
            return@SpineSection
        }
        val colors = ContinuumColors.Series
        LifeSpectrum(
            segments = lines.mapIndexed { i, l -> SpectrumSegment(l.totalMinor, colors[i % colors.size]) },
            description = lines.joinToString { "${it.category.name} ${MoneyText.format(it.totalMinor)}" },
        )
        lines.forEachIndexed { i, line ->
            val budget = line.budget
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeNode(shape = NodeShape.DOT, size = 10.dp, tone = NodeTone.NEUTRAL)
                    androidx.compose.foundation.Canvas(Modifier.size(6.dp)) { drawCircle(colors[i % colors.size]) }
                    Text(line.category.name, style = Continuum.type.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(LifeFormat.percent(line.sharePermille), style = Continuum.type.figuresSmall, color = ContinuumColors.Faint)
                    Text(MoneyText.format(line.totalMinor, s.currency), style = Continuum.type.figures)
                }
                if (budget != null) {
                    LifeLine(
                        progress = budget.usedPermille / 1000f,
                        meaning = LineMeaning.PROGRESS,
                        progressDescription = "${LifeFormat.percent(budget.usedPermille)} des Budgets",
                    )
                    Text(
                        if (budget.isExceeded) {
                            "Budget ${MoneyText.format(budget.limitMinor)} · überschritten um ${MoneyText.format(-budget.remainingMinor)}"
                        } else {
                            "Budget ${MoneyText.format(budget.limitMinor)} · frei ${MoneyText.format(budget.remainingMinor)}"
                        },
                        style = Continuum.type.small,
                        color = if (budget.isExceeded) ContinuumColors.Alert else ContinuumColors.Faint,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingSection(s: LedgerMonthSnapshot, vm: FinanceViewModel, nav: FinanceNavigation) {
    val overdue = s.pending.any { it.dueDate.isBefore(s.today) }
    SpineSection(
        title = "Fällige Zahlungen", index = 5, shape = NodeShape.SQUARE,
        tone = if (overdue) NodeTone.ALERT else if (s.pending.isNotEmpty()) NodeTone.WARN else NodeTone.NEUTRAL,
        onOpen = nav.openRecurring, openLabel = "Wiederkehrende Zahlungen öffnen",
        trailing = { LifeLink("Alle", nav.openRecurring) },
        fullBleed = true,
    ) {
        val inset = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)
        when {
            s.recurring.isEmpty() -> LifeEmpty(
                modifier = inset,
                text = 
                    "Noch keine wiederkehrenden Zahlungen. Miete, Versicherungen oder Abos einmal anlegen – " +
                    "LifeOS erinnert an jede Fälligkeit, bucht aber nie ungefragt.",
                actionLabel = "Einrichten", onAction = nav.openRecurring, glyph = Glyph.REPEAT,
            )
            s.pending.isEmpty() -> LifeEmpty("In den nächsten 30 Tagen ist nichts offen.", modifier = inset, glyph = Glyph.CHECK)
            else -> {
                s.pending.take(5).forEach { occ ->
                    PendingLine(occ, s.today, onBook = { vm.book(occ) }, onSkip = { vm.skip(occ) })
                }
                if (s.pending.size > 5) Text("+ ${s.pending.size - 5} weitere", style = Continuum.type.small, modifier = inset)
            }
        }
    }
}

@Composable
private fun AccountsSection(s: LedgerMonthSnapshot, nav: FinanceNavigation) {
    SpineSection(
        title = "Konten", index = 6, shape = NodeShape.RING, onOpen = nav.openAccounts, openLabel = "Konten verwalten",
        trailing = { LifeLink("Verwalten", nav.openAccounts) },
    ) {
        s.balances.filter { !it.account.archived || !it.balance.isZero }.forEach { ab ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ab.account.name + if (ab.account.archived) " (archiviert)" else "", style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(LifeFormat.accountKindLabel(ab.account.kind), style = Continuum.type.small)
                }
                Text(MoneyText.format(ab.balance), style = Continuum.type.figures, color = if (ab.balance.isNegative) ContinuumColors.Alert else ContinuumColors.Frost)
            }
            LifeDivider()
        }
    }
}

@Composable
private fun RecentSection(s: LedgerMonthSnapshot, nav: FinanceNavigation) {
    val entries = s.days.flatMap { it.entries }.take(6)
    SpineSection(
        title = "Letzte Buchungen", index = 7, shape = NodeShape.DIAMOND,
        onOpen = { nav.openTransactions(s.month) }, openLabel = "Alle Buchungen öffnen",
        trailing = { LifeLink("Alle", { nav.openTransactions(s.month) }) },
        fullBleed = true,
    ) {
        if (entries.isEmpty()) {
            LifeEmpty(
                "Noch keine Buchungen im ${LifeFormat.monthName(s.month)}.",
                modifier = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter),
                actionLabel = "Buchung erfassen", onAction = nav.newTransaction, glyph = Glyph.PLUS,
            )
        } else {
            entries.forEach { e -> TransactionLine(e, onClick = { nav.openTransaction(e.transaction.id) }) }
        }
    }
}
