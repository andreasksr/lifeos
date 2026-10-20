package app.lifeos.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing

/**
 * LifePanel – Container aus Kontur statt Fläche. Haarlinienrahmen mit abgeschrägter Ecke oben rechts
 * und einem kurzen Lichtsegment oben links, das die LifeLine aufnimmt. Keine bunten Karten.
 */
@Composable
fun LifePanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: Dp = ContinuumSpacing.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val chamfer = ContinuumSpacing.chamfer.toPx()
                val stroke = 1.dp.toPx()
                val half = stroke / 2
                val outline = Path().apply {
                    moveTo(half, half)
                    lineTo(size.width - chamfer, half)
                    lineTo(size.width - half, chamfer)
                    lineTo(size.width - half, size.height - half)
                    lineTo(half, size.height - half)
                    close()
                }
                drawPath(outline, ContinuumColors.ObsidianRaised.copy(alpha = 0.7f))
                drawPath(outline, ContinuumColors.Hairline, style = Stroke(stroke))
                // Lichtsegment – Anschluss an die LifeLine
                drawLine(
                    brush = ContinuumColors.Signature,
                    start = Offset(half, half),
                    end = Offset(minOf(size.width * 0.22f, 72.dp.toPx()), half),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.m),
    ) {
        if (title != null || trailing != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Text(
                        text = title.uppercase(),
                        style = Continuum.type.label,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                }
                trailing?.invoke()
            }
        }
        content()
    }
}
