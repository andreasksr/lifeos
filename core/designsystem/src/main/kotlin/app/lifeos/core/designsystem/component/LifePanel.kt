package app.lifeos.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing

enum class PanelEmphasis {
    /** Kaum sichtbare Fläche – Gruppierung ohne „Karte". */
    QUIET,

    /** Leicht erhobene Fläche mit Lichtkante – für das Wichtigste auf einer Ansicht. */
    RAISED,
}

/**
 * LifePanel – Informationsfläche aus Licht statt Box: Verlauf von Deep nach transparent, eine
 * Lichtkante oben, eine abgeschrägte Ecke oben rechts. Keine Schatten, keine großen Rundungen.
 */
@Composable
fun LifePanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    emphasis: PanelEmphasis = PanelEmphasis.QUIET,
    contentPadding: Dp = ContinuumSpacing.l,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val click = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Column(
        modifier
            .fillMaxWidth()
            .then(click)
            .drawBehind {
                val chamfer = ContinuumSpacing.chamfer.toPx()
                val outline = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width - chamfer, 0f)
                    lineTo(size.width, chamfer)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                val top = if (emphasis == PanelEmphasis.RAISED) ContinuumColors.Elevated else ContinuumColors.Deep
                drawPath(
                    outline,
                    Brush.verticalGradient(
                        listOf(top.copy(alpha = if (emphasis == PanelEmphasis.RAISED) 0.95f else 0.6f), top.copy(alpha = 0.15f)),
                    ),
                )
                if (emphasis == PanelEmphasis.RAISED) {
                    drawPath(outline, ContinuumColors.Hairline, style = Stroke(1.dp.toPx()))
                }
                // Lichtkante oben – nimmt die LifeLine auf
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(ContinuumColors.Cyan.copy(alpha = 0.9f), ContinuumColors.Violet.copy(alpha = 0.35f), Color.Transparent),
                        endX = size.width * 0.7f,
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width - chamfer, 0f),
                    strokeWidth = 1.dp.toPx(),
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

/** Dünne Trennlinie (Haarlinie) für Listen. */
@Composable
fun LifeDivider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    androidx.compose.foundation.Canvas(
        modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(1.dp),
    ) {
        drawLine(ContinuumColors.Hairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
    }
}
