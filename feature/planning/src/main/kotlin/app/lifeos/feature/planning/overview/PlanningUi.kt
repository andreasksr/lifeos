package app.lifeos.feature.planning.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeGlyph
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeTag
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineRow
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.domain.planning.AgendaItem
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.Priority
import app.lifeos.core.model.Task
import app.lifeos.core.model.TaskStatus
import app.lifeos.feature.planning.presentation.PlanningText
import java.time.LocalDate

/**
 * Aufgabe an der LifeLine. Der Knoten auf der Linie ist der Erledigt-Schalter (48-dp-Fläche, haptisch),
 * die Zeile öffnet die Aufgabe. So bleibt beides einhändig erreichbar.
 */
@Composable
fun TaskLine(
    task: Task,
    today: LocalDate,
    projectName: String?,
    hasReminder: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    showDue: Boolean = true,
) {
    val done = task.status == TaskStatus.DONE
    val overdue = !done && task.dueDate?.isBefore(today) == true
    SpineRow(
        modifier = Modifier.heightIn(min = 56.dp),
        node = {
            LifeNode(
                shape = NodeShape.RING,
                tone = when {
                    done -> NodeTone.DONE
                    overdue -> NodeTone.ALERT
                    task.priority == Priority.HIGH -> NodeTone.WARN
                    else -> NodeTone.NEUTRAL
                },
                size = 16.dp,
                onClick = onToggle,
                onClickLabel = if (done) "„${task.title}“ wieder öffnen" else "„${task.title}“ erledigen",
            )
        },
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClickLabel = "Aufgabe öffnen", onClick = onOpen)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = Continuum.type.body.copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                    color = if (done) ContinuumColors.Faint else ContinuumColors.Frost,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val parts = buildList {
                    if (showDue || overdue) add(if (overdue) LifeFormat.relativeDue(task.dueDate!!, today) else PlanningText.due(task.dueDate, task.dueTime, today))
                    projectName?.let { add(it) }
                    task.repeat?.let { add(PlanningText.repeat(it)) }
                }
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString(" · "),
                        style = Continuum.type.small,
                        color = if (overdue) ContinuumColors.Alert else ContinuumColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (task.priority == Priority.HIGH && !done) LifeTag("Hoch", NodeTone.WARN)
            if (hasReminder && !done) LifeGlyph(Glyph.BELL, tint = ContinuumColors.Muted, size = 16.dp, contentDescription = "mit Erinnerung")
        }
    }
}

/** Termin an der LifeLine: Uhrzeit links in Ziffernschrift, Knoten = Zeitpunkt. */
@Composable
fun EventLine(item: AgendaItem.EventItem, today: LocalDate, hasReminder: Boolean, onOpen: () -> Unit) {
    val e = item.event
    SpineRow(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Termin öffnen", onClick = onOpen),
        node = { LifeNode(shape = NodeShape.DOT, tone = NodeTone.ACTIVE, active = true, size = 14.dp) },
    ) {
        Row(Modifier.weight(1f).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (e.allDay) "ganzt." else LifeFormat.time(item.occurrenceStart.toLocalTime()),
                style = Continuum.type.figuresSmall,
                color = ContinuumColors.Cyan,
                modifier = Modifier.width(44.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(e.title, style = Continuum.type.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    PlanningText.eventSpan(item.occurrenceStart, item.occurrenceEnd, e.allDay, today),
                    e.location,
                    e.repeat?.let { PlanningText.repeat(it) },
                ).joinToString(" · ")
                Text(sub, style = Continuum.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hasReminder) LifeGlyph(Glyph.BELL, tint = ContinuumColors.Muted, size = 16.dp, contentDescription = "mit Erinnerung")
        }
    }
}
