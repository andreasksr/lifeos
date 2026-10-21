package app.lifeos.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.core.designsystem.component.ChartSeries
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeBarChart
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeLink
import app.lifeos.core.designsystem.component.LifeMoneyMetric
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeSegmented
import app.lifeos.core.designsystem.component.LifeTimelineRow
import app.lifeos.core.designsystem.component.LifeToggle
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.MetricSize
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.domain.home.AttentionSeverity
import app.lifeos.core.domain.home.AttentionTarget
import app.lifeos.core.domain.home.HomeDensity
import app.lifeos.core.domain.home.HomeSectionId
import app.lifeos.core.domain.ledger.LedgerMonthSnapshot
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.TransactionKind

/** Navigation und Zusatzinhalte, die die App einspeist (andere Module). */
data class HomeActions(
    val openFinance: () -> Unit,
    val openBudgets: () -> Unit,
    val openRecurring: () -> Unit,
    val openTarget: (AttentionTarget) -> Unit,
    val captureExpense: () -> Unit,
    val captureIncome: () -> Unit,
    /** Weitere Schnellerfassungen anderer Module (Aufgabe, Termin …): Label, Glyph, Aktion. */
    val extraCaptures: List<Triple<String, Glyph, () -> Unit>> = emptyList(),
    val openSearch: (() -> Unit)? = null,
)

typealias HomeSectionContent = @Composable (index: Int, density: HomeDensity) -> Unit

@Composable
fun HomeRoute(
    vm: HomeViewModel,
    actions: HomeActions,
    /** Inhalte zusätzlicher Module, z. B. PLANNING → Agenda. */
    sectionContent: Map<HomeSectionId, HomeSectionContent> = emptyMap(),
    /** Ergänzung des Abschnitts „Heute" (z. B. Termine und Aufgaben von heute). */
    todayContent: (@Composable (HomeDensity) -> Unit)? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    var customize by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) {
        item(key = "top") {
            LifeTopBar(
                overline = LifeFormat.greeting(s.now.hour),
                title = LifeFormat.weekdayLong(s.today.dayOfWeek),
                subtitle = LifeFormat.longDate(s.today),
                large = true,
                actions = {
                    actions.openSearch?.let { LifeIconButton(Glyph.SEARCH, "Suche", it) }
                    LifeIconButton(Glyph.SLIDERS, "Startseite anpassen", { customize = true })
                },
            )
        }
        s.sections.forEachIndexed { index, id ->
            item(key = id.name) {
                when (id) {
                    HomeSectionId.TODAY -> TodaySection(s, index, todayContent)
                    HomeSectionId.ATTENTION -> AttentionSection(s, index, actions)
                    HomeSectionId.FINANCE -> s.finance?.let { FinanceSection(it, s.density, index, actions) }
                    HomeSectionId.CAPTURE -> CaptureSection(index, actions)
                    else -> sectionContent[id]?.invoke(index, s.density)
                }
            }
        }
    }

    CustomizeOverlay(customize, s, vm, onDismiss = { customize = false }, sectionNames = sectionNames)
}

private val sectionNames = mapOf(
    HomeSectionId.TODAY to "Heute",
    HomeSectionId.ATTENTION to "Aufmerksamkeit",
    HomeSectionId.FINANCE to "Finanzen",
    HomeSectionId.PLANNING to "Planung",
    HomeSectionId.PROJECTS to "Projekte",
    HomeSectionId.LEARNING to "Weiterbildung",
    HomeSectionId.CAPTURE to "Schnellerfassung",
)

@Composable
private fun TodaySection(s: HomeState, index: Int, extra: (@Composable (HomeDensity) -> Unit)?) {
    val todaySpent = s.finance?.days?.firstOrNull { it.date == s.today }?.entries
        ?.filter { it.transaction.kind == TransactionKind.EXPENSE }?.sumOf { it.transaction.amount.minor } ?: 0L
    SpineSection("Heute", index = index, shape = NodeShape.RING, tone = NodeTone.ACTIVE) {
        if (extra != null) {
            extra(s.density)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeNode(shape = NodeShape.DIAMOND, tone = if (todaySpent > 0) NodeTone.OUTFLOW else NodeTone.NEUTRAL, size = 10.dp)
            Text(
                if (todaySpent > 0) "Heute ausgegeben: ${MoneyText.format(todaySpent)}" else "Heute noch keine Ausgaben erfasst.",
                style = Continuum.type.bodyMuted,
            )
        }
    }
}

@Composable
private fun AttentionSection(s: HomeState, index: Int, actions: HomeActions) {
    val items = when (s.density) {
        HomeDensity.MINIMAL -> s.attention.take(3)
        HomeDensity.STANDARD -> s.attention.take(6)
        HomeDensity.ADVANCED -> s.attention
    }
    SpineSection(
        "Aufmerksamkeit", index = index, shape = NodeShape.SQUARE,
        tone = when {
            s.attention.any { it.severity == AttentionSeverity.ALERT } -> NodeTone.ALERT
            s.attention.isNotEmpty() -> NodeTone.WARN
            else -> NodeTone.NEUTRAL
        },
        fullBleed = true,
    ) {
        if (items.isEmpty()) {
            LifeEmpty(
                "Nichts Dringendes. Überfällige Zahlungen, überschrittene Budgets und fällige Termine erscheinen hier.",
                modifier = Modifier.padding48(),
                glyph = Glyph.CHECK,
            )
        }
        items.forEach { item ->
            LifeTimelineRow(
                nodeShape = NodeShape.SQUARE,
                nodeTone = when (item.severity) {
                    AttentionSeverity.ALERT -> NodeTone.ALERT
                    AttentionSeverity.WARN -> NodeTone.WARN
                    AttentionSeverity.INFO -> NodeTone.ACTIVE
                },
                onClick = { actions.openTarget(item.target) },
                onClickLabel = "Öffnen",
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.detail, style = Continuum.type.small,
                        color = if (item.severity == AttentionSeverity.ALERT) ContinuumColors.Alert else ContinuumColors.Muted,
                        maxLines = 2,
                    )
                }
                app.lifeos.core.designsystem.component.LifeGlyph(Glyph.CHEVRON_RIGHT, tint = ContinuumColors.Faint, size = 16.dp)
            }
        }
        if (s.attention.size > items.size) {
            Text("+ ${s.attention.size - items.size} weitere", style = Continuum.type.small, modifier = Modifier.padding48())
        }
    }
}

private fun Modifier.padding48(): Modifier = this.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)

@Composable
private fun FinanceSection(f: LedgerMonthSnapshot, density: HomeDensity, index: Int, actions: HomeActions) {
    SpineSection(
        "Finanzen · ${LifeFormat.monthName(f.month)}", index = index, shape = NodeShape.DIAMOND, tone = NodeTone.ACTIVE,
        onOpen = actions.openFinance, openLabel = "Finanzen öffnen",
        trailing = { LifeLink("Öffnen", actions.openFinance) },
    ) {
        val tb = f.totalBudget
        if (tb != null) {
            LifeMoneyMetric(
                label = if (tb.isExceeded) "Budget überschritten" else "Budget übrig",
                minor = if (tb.isExceeded) -tb.remainingMinor else tb.remainingMinor,
                size = MetricSize.LARGE,
                tone = if (tb.isExceeded) NodeTone.ALERT else NodeTone.ACTIVE,
                progress = tb.usedPermille / 1000f,
                caption = "${LifeFormat.percent(tb.usedPermille)} von ${MoneyText.format(tb.limitMinor)} verbraucht",
            )
        } else {
            LifeMoneyMetric("Ausgaben im Monat", f.summary.expenseMinor, size = MetricSize.LARGE, tone = NodeTone.OUTFLOW)
            if (density != HomeDensity.MINIMAL) LifeLink("Monatsbudget festlegen", actions.openBudgets)
        }
        if (density != HomeDensity.MINIMAL) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LifeMoneyMetric("Einnahmen", f.summary.incomeMinor, Modifier.weight(1f), tone = NodeTone.INFLOW, size = MetricSize.SMALL)
                if (tb != null) LifeMoneyMetric("Ausgaben", f.summary.expenseMinor, Modifier.weight(1f), tone = NodeTone.OUTFLOW, size = MetricSize.SMALL)
                LifeMoneyMetric("Kontostand", f.totalBalance.minor, Modifier.weight(1f), size = MetricSize.SMALL)
            }
            val upcoming = f.pending.filter { it.payment.kind == TransactionKind.EXPENSE }.take(3)
            if (upcoming.isNotEmpty()) {
                Text("BALD FÄLLIG", style = Continuum.type.label)
                upcoming.forEach { o ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LifeNode(shape = NodeShape.SQUARE, size = 10.dp, tone = if (o.dueDate.isBefore(f.today)) NodeTone.ALERT else NodeTone.WARN)
                        Text(o.payment.name, style = Continuum.type.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(LifeFormat.relativeDue(o.dueDate, f.today), style = Continuum.type.small)
                        Text(MoneyText.format(o.payment.amount), style = Continuum.type.figuresSmall)
                    }
                }
                LifeLink("Fixkosten", actions.openRecurring)
            }
        }
        if (density == HomeDensity.ADVANCED && f.history.any { it.incomeMinor != 0L || it.expenseMinor != 0L }) {
            LifeBarChart(
                labels = f.history.map { LifeFormat.monthAxis(it.month) },
                series = listOf(
                    ChartSeries("Einnahmen", f.history.map { it.incomeMinor }, ContinuumColors.Mint),
                    ChartSeries("Ausgaben", f.history.map { it.expenseMinor.coerceAtLeast(0) }, ContinuumColors.Violet),
                ),
                height = 100.dp,
                selectedIndex = f.history.lastIndex,
                description = "Einnahmen und Ausgaben der letzten sechs Monate",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureSection(index: Int, actions: HomeActions) {
    SpineSection("Schnellerfassung", index = index, shape = NodeShape.DIAMOND) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeButton("Ausgabe", actions.captureExpense, style = LifeButtonStyle.SECONDARY, glyph = Glyph.ARROW_OUT)
            LifeButton("Einnahme", actions.captureIncome, style = LifeButtonStyle.SECONDARY, glyph = Glyph.ARROW_IN)
            actions.extraCaptures.forEach { (label, glyph, action) ->
                LifeButton(label, action, style = LifeButtonStyle.SECONDARY, glyph = glyph)
            }
        }
    }
}

@Composable
private fun CustomizeOverlay(
    visible: Boolean,
    s: HomeState,
    vm: HomeViewModel,
    onDismiss: () -> Unit,
    sectionNames: Map<HomeSectionId, String>,
) {
    LifeOverlay(visible = visible, onDismiss = onDismiss, title = "Startseite anpassen", overline = "Personalisierung") {
        Text("INFORMATIONSDICHTE", style = Continuum.type.label)
        LifeSegmented(
            listOf(HomeDensity.MINIMAL to "Minimal", HomeDensity.STANDARD to "Standard", HomeDensity.ADVANCED to "Advanced"),
            s.layout.density,
            { d -> vm.updateLayout { it.copy(density = d) } },
        )
        Text("BEREICHE", style = Continuum.type.label)
        s.layout.order.filter { it in s.available }.forEach { id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LifeToggle(
                    checked = id !in s.layout.hidden,
                    onCheckedChange = { vm.updateLayout { it.toggle(id) } },
                    label = sectionNames[id] ?: id.name,
                    modifier = Modifier.weight(1f),
                )
                LifeIconButton(Glyph.ARROW_OUT, "${sectionNames[id]} nach oben", { vm.updateLayout { it.moveUp(id) } }, tint = ContinuumColors.Faint)
                LifeIconButton(Glyph.ARROW_IN, "${sectionNames[id]} nach unten", { vm.updateLayout { it.moveDown(id) } }, tint = ContinuumColors.Faint)
            }
        }
        Spacer(Modifier.height(8.dp))
        LifeButton("Fertig", onDismiss, fill = true, glyph = Glyph.CHECK)
    }
}
