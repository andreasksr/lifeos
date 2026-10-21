package app.lifeos.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.ContinuumColors

/**
 * Eigene Linien-Icons im Stil der LifeLine: 24er-Raster, 1.6-dp-Strich, runde Enden.
 * Keine Icon-Bibliothek – dadurch einheitlich und klein.
 */
enum class Glyph {
    PLUS, CLOSE, BACK, CHEVRON_LEFT, CHEVRON_RIGHT, CHEVRON_DOWN, SEARCH, FILTER, CHECK, TRASH, EDIT,
    CALENDAR, CLOCK, REPEAT, SLIDERS, HOME, WALLET, TASKS, PROJECT, LIFE, CAR, BOX, DOCUMENT, BOOK,
    BELL, LOCK, LINK, UNDO, MORE, ARROW_IN, ARROW_OUT, TRANSFER, BACKSPACE, MAP, PLAY, STOP, CHART, SHIELD,
}

@Composable
fun LifeGlyph(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    tint: Color = ContinuumColors.Frost,
    size: Dp = 22.dp,
    contentDescription: String? = null,
) {
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier.size(size).then(semanticsModifier)) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) {
            drawGlyph(glyph, tint)
        }
    }
}

private fun DrawScope.drawGlyph(g: Glyph, c: Color) {
    val s = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.6f, cap = StrokeCap.Round)
    fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), c, style = s)
    fun circle(x: Float, y: Float, r: Float, fill: Boolean = false) =
        if (fill) drawCircle(c, r, Offset(x, y)) else drawCircle(c, r, Offset(x, y), style = s)
    fun rect(x: Float, y: Float, w: Float, h: Float) =
        drawRoundRect(c, Offset(x, y), Size(w, h), androidx.compose.ui.geometry.CornerRadius(2f, 2f), style = s)

    when (g) {
        Glyph.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        Glyph.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Glyph.BACK -> { line(5f, 12f, 19f, 12f); path { moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) } }
        Glyph.CHEVRON_LEFT -> path { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
        Glyph.CHEVRON_RIGHT -> path { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
        Glyph.CHEVRON_DOWN -> path { moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f) }
        Glyph.SEARCH -> { circle(10.5f, 10.5f, 6f); line(15f, 15f, 20f, 20f) }
        Glyph.FILTER -> { line(4f, 7f, 20f, 7f); line(7f, 12f, 17f, 12f); line(10f, 17f, 14f, 17f) }
        Glyph.CHECK -> path { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
        Glyph.TRASH -> {
            line(4f, 7f, 20f, 7f); path { moveTo(9f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(15f, 7f) }
            path { moveTo(6f, 7f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 7f) }; line(10f, 11f, 10f, 16f); line(14f, 11f, 14f, 16f)
        }
        Glyph.EDIT -> { path { moveTo(4f, 20f); lineTo(5f, 15.5f); lineTo(15.5f, 5f); lineTo(19f, 8.5f); lineTo(8.5f, 19f); close() }; line(13f, 7.5f, 16.5f, 11f) }
        Glyph.CALENDAR -> { rect(4f, 6f, 16f, 14f); line(4f, 10f, 20f, 10f); line(8f, 4f, 8f, 7f); line(16f, 4f, 16f, 7f); circle(12f, 15f, 1.2f, fill = true) }
        Glyph.CLOCK -> { circle(12f, 12f, 8f); path { moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15f, 14f) } }
        Glyph.REPEAT -> {
            path { moveTo(5f, 11f); lineTo(5f, 9f); quadraticTo(5f, 7f, 7f, 7f); lineTo(18f, 7f) }; path { moveTo(15f, 4f); lineTo(18f, 7f); lineTo(15f, 10f) }
            path { moveTo(19f, 13f); lineTo(19f, 15f); quadraticTo(19f, 17f, 17f, 17f); lineTo(6f, 17f) }; path { moveTo(9f, 20f); lineTo(6f, 17f); lineTo(9f, 14f) }
        }
        Glyph.SLIDERS -> { line(4f, 8f, 20f, 8f); line(4f, 16f, 20f, 16f); circle(9f, 8f, 2.2f, fill = true); circle(15f, 16f, 2.2f, fill = true) }
        Glyph.HOME -> { circle(12f, 12f, 7.5f); circle(12f, 12f, 2.2f, fill = true) }
        Glyph.WALLET -> path { moveTo(12f, 3.5f); lineTo(20.5f, 12f); lineTo(12f, 20.5f); lineTo(3.5f, 12f); close() }
        Glyph.TASKS -> { rect(4.5f, 4.5f, 15f, 15f); path { moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16.5f, 9f) } }
        Glyph.PROJECT -> { circle(6f, 17f, 2.2f); circle(18f, 7f, 2.2f); circle(17f, 18f, 1.6f, fill = true); line(7.8f, 15.6f, 16.2f, 8.4f); line(8f, 17.3f, 15.4f, 17.9f) }
        Glyph.LIFE -> path { moveTo(12f, 3.5f); lineTo(19.5f, 7.75f); lineTo(19.5f, 16.25f); lineTo(12f, 20.5f); lineTo(4.5f, 16.25f); lineTo(4.5f, 7.75f); close() }
        Glyph.CAR -> {
            path { moveTo(3.5f, 15f); lineTo(3.5f, 12f); lineTo(6f, 7.5f); lineTo(18f, 7.5f); lineTo(20.5f, 12f); lineTo(20.5f, 15f); close() }
            circle(7.5f, 16.5f, 1.8f); circle(16.5f, 16.5f, 1.8f); line(4f, 12f, 20f, 12f)
        }
        Glyph.BOX -> { path { moveTo(4f, 8f); lineTo(12f, 4f); lineTo(20f, 8f); lineTo(20f, 16f); lineTo(12f, 20f); lineTo(4f, 16f); close() }; path { moveTo(4f, 8f); lineTo(12f, 12f); lineTo(20f, 8f) }; line(12f, 12f, 12f, 20f) }
        Glyph.DOCUMENT -> { path { moveTo(6f, 3.5f); lineTo(14f, 3.5f); lineTo(18.5f, 8f); lineTo(18.5f, 20.5f); lineTo(6f, 20.5f); close() }; path { moveTo(14f, 3.5f); lineTo(14f, 8f); lineTo(18.5f, 8f) }; line(9f, 13f, 15.5f, 13f); line(9f, 16.5f, 14f, 16.5f) }
        Glyph.BOOK -> { path { moveTo(12f, 6.5f); quadraticTo(8f, 4f, 4f, 5f); lineTo(4f, 18.5f); quadraticTo(8f, 17.5f, 12f, 20f); quadraticTo(16f, 17.5f, 20f, 18.5f); lineTo(20f, 5f); quadraticTo(16f, 4f, 12f, 6.5f); close() }; line(12f, 6.5f, 12f, 20f) }
        Glyph.BELL -> { path { moveTo(6f, 16.5f); lineTo(6f, 11f); quadraticTo(6f, 5f, 12f, 5f); quadraticTo(18f, 5f, 18f, 11f); lineTo(18f, 16.5f); lineTo(19.5f, 18f); lineTo(4.5f, 18f); close() }; line(10.5f, 20.5f, 13.5f, 20.5f) }
        Glyph.LOCK -> { rect(5f, 10.5f, 14f, 10f); path { moveTo(8f, 10.5f); lineTo(8f, 8f); quadraticTo(8f, 4f, 12f, 4f); quadraticTo(16f, 4f, 16f, 8f); lineTo(16f, 10.5f) }; circle(12f, 15.5f, 1.3f, fill = true) }
        Glyph.LINK -> { circle(8f, 12f, 3.5f); circle(16f, 12f, 3.5f); line(10.5f, 12f, 13.5f, 12f) }
        Glyph.UNDO -> { path { moveTo(8f, 5f); lineTo(4f, 9f); lineTo(8f, 13f) }; path { moveTo(4f, 9f); lineTo(14f, 9f); quadraticTo(20f, 9f, 20f, 14.5f); quadraticTo(20f, 20f, 14f, 20f); lineTo(9f, 20f) } }
        Glyph.MORE -> { circle(6f, 12f, 1.4f, fill = true); circle(12f, 12f, 1.4f, fill = true); circle(18f, 12f, 1.4f, fill = true) }
        Glyph.ARROW_IN -> { line(12f, 4f, 12f, 18f); path { moveTo(6f, 12f); lineTo(12f, 18f); lineTo(18f, 12f) }; line(5f, 21f, 19f, 21f) }
        Glyph.ARROW_OUT -> { line(12f, 20f, 12f, 6f); path { moveTo(6f, 12f); lineTo(12f, 6f); lineTo(18f, 12f) }; line(5f, 3f, 19f, 3f) }
        Glyph.TRANSFER -> { line(4f, 8f, 19f, 8f); path { moveTo(15f, 4f); lineTo(19f, 8f); lineTo(15f, 12f) }; line(20f, 16f, 5f, 16f); path { moveTo(9f, 12f); lineTo(5f, 16f); lineTo(9f, 20f) } }
        Glyph.BACKSPACE -> { path { moveTo(9f, 5.5f); lineTo(20f, 5.5f); lineTo(20f, 18.5f); lineTo(9f, 18.5f); lineTo(3.5f, 12f); close() }; line(12f, 9.5f, 17f, 14.5f); line(17f, 9.5f, 12f, 14.5f) }
        Glyph.MAP -> { circle(12f, 12f, 2.4f, fill = true); circle(5f, 6f, 1.8f); circle(19f, 7f, 1.8f); circle(17f, 19f, 1.8f); circle(5.5f, 17.5f, 1.8f); line(10.3f, 10.6f, 6.4f, 7.2f); line(13.9f, 10.9f, 17.4f, 8f); line(13.4f, 13.8f, 16f, 17.6f); line(10.2f, 13.5f, 6.8f, 16.4f) }
        Glyph.PLAY -> path { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() }
        Glyph.STOP -> rect(6f, 6f, 12f, 12f)
        Glyph.CHART -> { path { moveTo(4f, 17f); lineTo(9f, 11f); lineTo(13f, 14f); lineTo(20f, 6f) }; line(4f, 20.5f, 20f, 20.5f) }
        Glyph.SHIELD -> { path { moveTo(12f, 3.5f); lineTo(19f, 6.5f); lineTo(19f, 12f); quadraticTo(19f, 18f, 12f, 20.5f); quadraticTo(5f, 18f, 5f, 12f); lineTo(5f, 6.5f); close() }; path { moveTo(9f, 12f); lineTo(11.3f, 14.3f); lineTo(15.5f, 9.8f) } }
    }
}
