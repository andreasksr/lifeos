package app.lifeos.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors

enum class MetricSize { LARGE, REGULAR }

/**
 * LifeMetric – eine Kennzahl. Label in Versalien, Wert in tabellarischen Ziffern, optional eine
 * LifeLine als Auslastungs-/Fortschrittsanzeige. Zeigt nur übergebene, echte Werte an.
 */
@Composable
fun LifeMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    tone: NodeTone = NodeTone.NEUTRAL,
    size: MetricSize = MetricSize.REGULAR,
    progress: Float? = null,
    progressMeaning: LineMeaning = LineMeaning.PROGRESS,
) {
    val type = Continuum.type
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = listOfNotNull(label, value, caption).joinToString(", ")
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tone != NodeTone.NEUTRAL) {
                LifeNode(shape = NodeShape.DOT, tone = tone, size = 8.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(label.uppercase(), style = type.label)
        }
        Text(
            text = value,
            style = if (size == MetricSize.LARGE) type.figuresLarge else type.figures.copy(fontSize = type.figures.fontSize * 1.15f),
            color = when (tone) {
                NodeTone.ALERT -> ContinuumColors.Flare
                else -> ContinuumColors.Ice
            },
        )
        if (progress != null) {
            LifeLine(progress = progress, meaning = progressMeaning)
        }
        if (caption != null) {
            Text(caption, style = type.label.copy(letterSpacing = type.label.letterSpacing * 0.3f), color = ContinuumColors.IceFaint)
        }
    }
}
