package app.lifeos.feature.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeTag
import app.lifeos.core.designsystem.component.LifeTimelineRow
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.domain.ledger.FlowDirection
import app.lifeos.core.domain.ledger.Occurrence
import app.lifeos.core.domain.ledger.OccurrenceState
import app.lifeos.core.domain.ledger.TimelineEntry
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.TransactionKind
import java.time.LocalDate

data class AmountStyle(val text: String, val color: Color, val tone: NodeTone, val shape: NodeShape)

/** Einheitliche Darstellung eines Betrags je Buchungsrichtung – Vorzeichen + Form, nicht nur Farbe. */
fun amountStyle(entry: TimelineEntry): AmountStyle {
    val tx = entry.transaction
    return when (entry.direction) {
        FlowDirection.IN -> AmountStyle(MoneyText.format(tx.amount, showPlus = true), ContinuumColors.Mint, NodeTone.INFLOW, NodeShape.DIAMOND)
        FlowDirection.OUT -> AmountStyle(MoneyText.format(-tx.amount), ContinuumColors.Frost, NodeTone.OUTFLOW, NodeShape.DIAMOND)
        FlowDirection.MOVE -> AmountStyle("↔ " + MoneyText.format(tx.amount), ContinuumColors.Muted, NodeTone.NEUTRAL, NodeShape.LINKED)
        FlowDirection.CORRECTION -> AmountStyle(
            MoneyText.format(if (tx.kind.isInflow) tx.amount else -tx.amount, showPlus = true),
            ContinuumColors.Muted, NodeTone.NEUTRAL, NodeShape.SQUARE,
        )
    }
}

/** Eine Buchung auf der Zeitachse. Antippen öffnet die Buchung. */
@Composable
fun TransactionLine(entry: TimelineEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = amountStyle(entry)
    val tx = entry.transaction
    val subtitle = when (entry.direction) {
        FlowDirection.MOVE -> "${entry.accountName} → ${entry.counterAccountName}"
        else -> (entry.categoryNames + entry.accountName).joinToString(" · ")
    }
    LifeTimelineRow(
        modifier = modifier,
        nodeShape = style.shape,
        nodeTone = style.tone,
        onClick = onClick,
        onClickLabel = "Buchung öffnen",
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (tx.kind == TransactionKind.REFUND) LifeTag("Erstattung", NodeTone.INFLOW)
                if (entry.recurringName != null) LifeTag("Fix", NodeTone.ACTIVE)
                if (tx.isSample) LifeTag("Beispiel", NodeTone.WARN)
            }
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(style.text, style = Continuum.type.figures, color = style.color)
    }
}

/** Fällige wiederkehrende Zahlung mit „Buchen" / „Überspringen". */
@Composable
fun PendingLine(
    occurrence: Occurrence,
    today: LocalDate,
    onBook: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = occurrence.payment
    val overdue = occurrence.state == OccurrenceState.OVERDUE
    val soon = !overdue && !occurrence.dueDate.isAfter(today.plusDays(3))
    LifeTimelineRow(
        modifier = modifier,
        nodeShape = NodeShape.SQUARE,
        nodeTone = when {
            overdue -> NodeTone.ALERT
            soon -> NodeTone.WARN
            else -> NodeTone.NEUTRAL
        },
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = Continuum.type.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    MoneyText.format(if (p.kind == TransactionKind.INCOME) p.amount else -p.amount, showPlus = p.kind == TransactionKind.INCOME),
                    style = Continuum.type.figures,
                    color = if (p.kind == TransactionKind.INCOME) ContinuumColors.Mint else ContinuumColors.Frost,
                )
            }
            Text(
                "${LifeFormat.relativeDue(occurrence.dueDate, today)} · ${LifeFormat.numericDate(occurrence.dueDate)}",
                style = Continuum.type.small,
                color = when {
                    overdue -> ContinuumColors.Alert
                    soon -> ContinuumColors.Amber
                    else -> ContinuumColors.Muted
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeButton("Buchen", onBook, style = LifeButtonStyle.SECONDARY, glyph = Glyph.CHECK)
                LifeButton("Überspringen", onSkip, style = LifeButtonStyle.GHOST)
            }
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String, modifier: Modifier = Modifier, color: Color = ContinuumColors.Frost) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Continuum.type.bodyMuted, modifier = Modifier.weight(1f))
        Text(value, style = Continuum.type.figures, color = color)
    }
}
