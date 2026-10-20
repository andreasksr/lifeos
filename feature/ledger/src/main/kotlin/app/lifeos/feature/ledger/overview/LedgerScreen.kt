package app.lifeos.feature.ledger.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeMetric
import app.lifeos.core.designsystem.component.LifePanel
import app.lifeos.core.designsystem.component.LifeTimelineHeader
import app.lifeos.core.designsystem.component.LifeTimelineRow
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.component.MetricSize
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineSegment
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.domain.ledger.CategoryLine
import app.lifeos.core.domain.ledger.FlowDirection
import app.lifeos.core.domain.ledger.LedgerMath
import app.lifeos.core.domain.ledger.LedgerMonthSnapshot
import app.lifeos.core.domain.ledger.TimelineEntry
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.feature.ledger.presentation.LedgerFormat

@Composable
fun LedgerRoute(
    viewModel: LedgerViewModel,
    onAddTransaction: () -> Unit,
    onEditTransaction: (NodeId) -> Unit,
    onOpenStructure: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LedgerScreen(
        state = state,
        onPrevious = viewModel::previousMonth,
        onNext = viewModel::nextMonth,
        onToday = viewModel::currentMonth,
        onAddTransaction = onAddTransaction,
        onEditTransaction = onEditTransaction,
        onOpenStructure = onOpenStructure,
    )
}

@Composable
fun LedgerScreen(
    state: LedgerUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onAddTransaction: () -> Unit,
    onEditTransaction: (NodeId) -> Unit,
    onOpenStructure: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ContinuumColors.Obsidian),
    ) {
        when (state) {
            LedgerUiState.Loading -> Unit // Daten kommen lokal in Millisekunden – kein Spinner-Flackern
            is LedgerUiState.Ready -> LedgerContent(state, onPrevious, onNext, onToday, onEditTransaction, onOpenStructure)
        }
        LifeButton(
            text = "+ Buchung",
            onClick = onAddTransaction,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(ContinuumSpacing.gutter)
                .background(ContinuumColors.Obsidian),
        )
    }
}

@Composable
private fun LedgerContent(
    state: LedgerUiState.Ready,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onEditTransaction: (NodeId) -> Unit,
    onOpenStructure: () -> Unit,
) {
    val snap = state.snapshot
    val gutter = ContinuumSpacing.gutter
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        item(key = "top") {
            LifeTopBar(
                overline = "Haushaltskasse",
                title = LedgerFormat.monthTitle(snap.month),
                actions = {
                    MonthStepper(onPrevious, onNext, onToday, showToday = !state.isCurrentMonth)
                },
            )
        }
        if (snap.containsSampleData) {
            item(key = "sample") {
                Text(
                    "Enthält gekennzeichnete Beispieldaten.",
                    style = Continuum.type.label,
                    color = ContinuumColors.Flare,
                    modifier = Modifier.padding(horizontal = gutter, vertical = 4.dp),
                )
            }
        }
        item(key = "metrics") {
            MetricsPanel(snap, Modifier.padding(horizontal = gutter, vertical = ContinuumSpacing.s))
        }
        item(key = "accounts") {
            AccountsPanel(snap, onOpenStructure, Modifier.padding(horizontal = gutter, vertical = ContinuumSpacing.s))
        }
        if (snap.expenseLines.isNotEmpty()) {
            item(key = "categories") {
                CategoriesPanel(snap.expenseLines, onOpenStructure, Modifier.padding(horizontal = gutter, vertical = ContinuumSpacing.s))
            }
        }
        item(key = "timelineLabel") {
            Text(
                "VERLAUF",
                style = Continuum.type.label,
                modifier = Modifier.padding(start = gutter, end = gutter, top = ContinuumSpacing.xl, bottom = ContinuumSpacing.s),
            )
        }
        if (snap.days.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Noch keine Buchungen im ${LedgerFormat.monthName(snap.month)}. " +
                        "Über „+ Buchung“ erfasst du die erste.",
                    style = Continuum.type.bodyMuted,
                    modifier = Modifier.padding(horizontal = gutter),
                )
            }
        }
        val lastDay = snap.days.lastIndex
        snap.days.forEachIndexed { dayIndex, day ->
            item(key = "day-${day.date}") {
                LifeTimelineHeader(
                    title = LedgerFormat.dayTitle(day.date, state.today),
                    trailing = MoneyText.format(day.netMinor, snap.currency, showPlus = true),
                    continuesAbove = dayIndex > 0,
                    continuesBelow = true,
                    highlighted = day.date == state.today,
                    modifier = Modifier.padding(horizontal = gutter - 4.dp),
                )
            }
            val lastEntry = day.entries.lastIndex
            itemsIndexed(day.entries, key = { _, e -> e.transaction.id.value }) { index, entry ->
                TimelineEntryRow(
                    entry = entry,
                    segment = if (dayIndex == lastDay && index == lastEntry) SpineSegment.LAST else SpineSegment.MIDDLE,
                    onClick = { onEditTransaction(entry.transaction.id) },
                    modifier = Modifier.padding(horizontal = gutter - 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthStepper(onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, showToday: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showToday) {
            StepperButton("Heute", "Zum aktuellen Monat", onToday)
        }
        StepperButton("‹", "Vorheriger Monat", onPrevious)
        StepperButton("›", "Nächster Monat", onNext)
    }
}

@Composable
private fun StepperButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = ContinuumSpacing.touchTarget)
            .widthIn(min = ContinuumSpacing.touchTarget)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = if (text.length == 1) Continuum.type.title else Continuum.type.label,
            color = ContinuumColors.IceMuted,
        )
    }
}

@Composable
private fun MetricsPanel(snap: LedgerMonthSnapshot, modifier: Modifier) {
    val s = snap.summary
    LifePanel(modifier = modifier) {
        LifeMetric(
            label = "Gesamtsaldo",
            value = MoneyText.format(snap.totalBalance),
            size = MetricSize.LARGE,
            caption = "Alle Konten · ${snap.balances.size}",
        )
        LifeLine(pulse = true, meaning = LineMeaning.TIME)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.l)) {
            LifeMetric(
                label = "Einnahmen",
                value = MoneyText.format(s.incomeMinor, s.currency),
                tone = NodeTone.INFLOW,
                modifier = Modifier.weight(1f),
            )
            LifeMetric(
                label = "Ausgaben",
                value = MoneyText.format(s.expenseMinor, s.currency),
                tone = NodeTone.OUTFLOW,
                modifier = Modifier.weight(1f),
            )
        }
        val spentShare = if (s.incomeMinor > 0) LedgerMath.permille(s.expenseMinor, s.incomeMinor) else null
        LifeMetric(
            label = if (s.netMinor >= 0) "Überschuss" else "Defizit",
            value = MoneyText.format(s.netMinor, s.currency, showPlus = true),
            tone = if (s.netMinor < 0) NodeTone.ALERT else NodeTone.NEUTRAL,
            progress = spentShare?.let { it / 1000f },
            progressMeaning = LineMeaning.OUTFLOW,
            caption = spentShare?.let { "${LedgerFormat.percent(it)} der Einnahmen ausgegeben" }
                ?: "${s.transactionCount} Buchungen in diesem Monat",
        )
    }
}

@Composable
private fun AccountsPanel(snap: LedgerMonthSnapshot, onOpenStructure: () -> Unit, modifier: Modifier) {
    LifePanel(
        modifier = modifier,
        title = "Konten",
        trailing = { PanelLink("Verwalten", onOpenStructure) },
    ) {
        snap.balances.filter { !it.account.archived || !it.balance.isZero }.forEach { ab ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ab.account.name + if (ab.account.archived) " (archiviert)" else "",
                    style = Continuum.type.body,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    MoneyText.format(ab.balance),
                    style = Continuum.type.figures,
                    color = if (ab.balance.isNegative) ContinuumColors.Flare else ContinuumColors.Ice,
                )
            }
        }
    }
}

@Composable
private fun CategoriesPanel(lines: List<CategoryLine>, onOpenStructure: () -> Unit, modifier: Modifier) {
    LifePanel(
        modifier = modifier,
        title = "Ausgaben nach Kategorie",
        trailing = { PanelLink("Budgets", onOpenStructure) },
    ) {
        lines.forEach { line ->
            val budget = line.budget
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(line.category.name, style = Continuum.type.body, modifier = Modifier.weight(1f))
                    Text(MoneyText.format(line.totalMinor), style = Continuum.type.figures)
                }
                if (budget != null) {
                    LifeLine(
                        progress = budget.usedPermille / 1000f,
                        meaning = LineMeaning.PROGRESS,
                        progressDescription = "${LedgerFormat.percent(budget.usedPermille)} des Budgets",
                    )
                    Text(
                        if (budget.isExceeded) {
                            "Budget ${MoneyText.format(budget.limitMinor)} · überschritten um ${MoneyText.format(-budget.remainingMinor)}"
                        } else {
                            "Budget ${MoneyText.format(budget.limitMinor)} · frei ${MoneyText.format(budget.remainingMinor)}"
                        },
                        style = Continuum.type.label.copy(letterSpacing = Continuum.type.label.letterSpacing * 0.3f),
                        color = if (budget.isExceeded) ContinuumColors.Flare else ContinuumColors.IceFaint,
                    )
                } else {
                    LifeLine(
                        progress = line.sharePermille / 1000f,
                        meaning = LineMeaning.OUTFLOW,
                        progressDescription = "${LedgerFormat.percent(line.sharePermille)} der Ausgaben",
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelLink(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$text →", style = Continuum.type.label, color = ContinuumColors.Cyan)
    }
}

@Composable
private fun TimelineEntryRow(entry: TimelineEntry, segment: SpineSegment, onClick: () -> Unit, modifier: Modifier) {
    val tx = entry.transaction
    val (tone, amountText, color) = when (entry.direction) {
        FlowDirection.IN -> Triple(NodeTone.INFLOW, MoneyText.format(tx.amount, showPlus = true), ContinuumColors.Cyan)
        FlowDirection.OUT -> Triple(NodeTone.OUTFLOW, MoneyText.format(-tx.amount), ContinuumColors.Ice)
        FlowDirection.MOVE -> Triple(NodeTone.NEUTRAL, "⇄ " + MoneyText.format(tx.amount), ContinuumColors.IceMuted)
    }
    val subtitle = when (entry.direction) {
        FlowDirection.MOVE -> "${entry.accountName} → ${entry.counterAccountName}"
        else -> (entry.categoryNames + entry.accountName).joinToString(" · ")
    }
    LifeTimelineRow(
        segment = segment,
        nodeShape = if (entry.direction == FlowDirection.MOVE) NodeShape.LINKED else NodeShape.DIAMOND,
        nodeTone = tone,
        modifier = modifier
            .clickable(role = Role.Button, onClickLabel = "Buchung bearbeiten", onClick = onClick),
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = Continuum.type.bodyMuted.copy(fontSize = Continuum.type.label.fontSize * 1.15f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(amountText, style = Continuum.type.figures, color = color)
    }
}
