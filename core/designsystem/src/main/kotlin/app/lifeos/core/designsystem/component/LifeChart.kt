package app.lifeos.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import kotlin.math.max

/** Eine Datenreihe in Minor Units (oder einer anderen ganzzahligen Einheit). */
data class ChartSeries(
    val name: String,
    val values: List<Long>,
    val color: Color,
    /** Gestrichelt = Plan/Soll (z. B. Budget-Soll-Linie). */
    val dashed: Boolean = false,
    /** Fläche unter der Linie leicht ausleuchten. */
    val area: Boolean = false,
)

/**
 * Interpoliert Datenreihen weich zwischen zwei Zuständen (Monatswechsel). Im Ruhezustand werden
 * exakt die übergebenen Werte gezeichnet.
 */
@Composable
private fun rememberMorph(target: List<List<Long>>): () -> List<List<Float>> {
    val motion = Continuum.motion
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val t = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = to
        to = target
        if (motion.enabled) {
            t.snapTo(0f)
            t.animateTo(1f, tween(motion.emphasisMillis, easing = FastOutSlowInEasing))
        } else {
            t.snapTo(1f)
        }
    }
    return {
        val p = t.value
        to.mapIndexed { s, series ->
            series.mapIndexed { i, v ->
                val old = from.getOrNull(s)?.getOrNull(i) ?: 0L
                if (p >= 1f) v.toFloat() else old + (v - old) * p
            }
        }
    }
}

/**
 * LifeFlowChart – Verlaufsdiagramm im CONTINUUM-Stil: Lichtlinien statt Standarddiagramm, Knoten an den
 * Messpunkten, Antippen wählt einen Punkt (Detail via [onSelect]). Datenwechsel werden weich überblendet.
 */
@Composable
fun LifeFlowChart(
    labels: List<String>,
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    selectedIndex: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    description: String,
) {
    val count = labels.size
    val values = rememberMorph(series.map { it.values })
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(count, onSelect) {
                    if (onSelect != null && count > 0) {
                        detectTapGestures { pos ->
                            val slot = size.width / count.toFloat()
                            onSelect((pos.x / slot).toInt().coerceIn(0, count - 1))
                        }
                    }
                },
        ) {
            if (count == 0) return@Canvas
            val data = values()
            val maxValue = max(1f, data.flatten().maxOrNull() ?: 1f)
            val top = 10.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val slot = size.width / count
            fun x(i: Int) = slot * (i + 0.5f)
            fun y(v: Float) = bottom - (v.coerceAtLeast(0f) / maxValue) * (bottom - top)

            // Grundlinie + feine Hilfslinien
            drawLine(ContinuumColors.Hairline, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
            for (f in listOf(0.5f, 1f)) {
                val gy = bottom - f * (bottom - top)
                drawLine(ContinuumColors.Hairline.copy(alpha = 0.05f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            }
            if (selectedIndex != null && selectedIndex in 0 until count) {
                drawLine(ContinuumColors.HairlineStrong, Offset(x(selectedIndex), top), Offset(x(selectedIndex), bottom), strokeWidth = 1.dp.toPx())
            }
            series.forEachIndexed { s, def ->
                val pts = data[s].mapIndexed { i, v -> Offset(x(i), y(v)) }
                if (pts.isEmpty()) return@forEachIndexed
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val p0 = pts[i - 1]
                        val p1 = pts[i]
                        val mid = (p0.x + p1.x) / 2f
                        cubicTo(mid, p0.y, mid, p1.y, p1.x, p1.y)
                    }
                }
                if (def.area && pts.size > 1) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(pts.last().x, bottom)
                        lineTo(pts.first().x, bottom)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(def.color.copy(alpha = 0.22f), Color.Transparent), startY = top, endY = bottom))
                }
                val effect = if (def.dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null
                if (!def.dashed) drawPath(path, def.color, alpha = 0.18f, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, def.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = effect))
                if (!def.dashed && pts.size <= 14) {
                    pts.forEachIndexed { i, p ->
                        val selected = i == selectedIndex
                        drawCircle(ContinuumColors.Obsidian, radius = if (selected) 5.dp.toPx() else 3.5.dp.toPx(), center = p)
                        drawCircle(def.color, radius = if (selected) 5.dp.toPx() else 3.5.dp.toPx(), center = p, style = Stroke(1.5.dp.toPx()))
                        if (selected) drawCircle(def.color, radius = 2.dp.toPx(), center = p)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    style = Continuum.type.label,
                    color = if (i == selectedIndex) ContinuumColors.Frost else ContinuumColors.Faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Legende: Knoten + Name je Reihe. */
@Composable
fun ChartLegend(series: List<ChartSeries>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(14.dp).height(8.dp)) {
                    val effect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null
                    drawLine(s.color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), pathEffect = effect)
                }
                Spacer(Modifier.width(6.dp))
                Text(s.name, style = Continuum.type.small)
            }
        }
    }
}

/**
 * LifeBarChart – „Lichtsäulen": je Zeitraum schmale Säulen nebeneinander (z. B. Einnahmen/Ausgaben).
 * Gut für Monatsvergleiche.
 */
@Composable
fun LifeBarChart(
    labels: List<String>,
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
    selectedIndex: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    description: String,
) {
    val count = labels.size
    val values = rememberMorph(series.map { it.values })
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(count, onSelect) {
                    if (onSelect != null && count > 0) {
                        detectTapGestures { pos -> onSelect((pos.x / (size.width / count.toFloat())).toInt().coerceIn(0, count - 1)) }
                    }
                },
        ) {
            if (count == 0 || series.isEmpty()) return@Canvas
            val data = values()
            val maxValue = max(1f, data.flatten().maxOrNull() ?: 1f)
            val bottom = size.height - 2.dp.toPx()
            val slot = size.width / count
            val barW = 5.dp.toPx()
            val gap = 4.dp.toPx()
            val groupW = series.size * barW + (series.size - 1) * gap
            drawLine(ContinuumColors.Hairline, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
            for (i in 0 until count) {
                val startX = slot * (i + 0.5f) - groupW / 2f
                if (i == selectedIndex) {
                    drawRoundRect(
                        ContinuumColors.Elevated.copy(alpha = 0.6f),
                        topLeft = Offset(slot * i + 4.dp.toPx(), 0f),
                        size = Size(slot - 8.dp.toPx(), bottom),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
                series.forEachIndexed { s, def ->
                    val v = data[s].getOrElse(i) { 0f }.coerceAtLeast(0f)
                    val h = (v / maxValue) * (bottom - 8.dp.toPx())
                    val x = startX + s * (barW + gap)
                    if (h > 0f) {
                        drawRoundRect(
                            Brush.verticalGradient(listOf(def.color, def.color.copy(alpha = 0.35f)), startY = bottom - h, endY = bottom),
                            topLeft = Offset(x, bottom - h),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(barW / 2f),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Text(
                    label, style = Continuum.type.label,
                    color = if (i == selectedIndex) ContinuumColors.Frost else ContinuumColors.Faint,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1,
                )
            }
        }
    }
}

data class SpectrumSegment(val value: Long, val color: Color)

/**
 * LifeSpectrum – Verteilung als eine einzige, segmentierte Lichtlinie (z. B. Ausgaben nach Kategorie).
 * Negative Werte (z. B. Erstattungsüberhang) werden nicht dargestellt.
 */
@Composable
fun LifeSpectrum(segments: List<SpectrumSegment>, modifier: Modifier = Modifier, height: Dp = 10.dp, description: String) {
    val values = rememberMorph(listOf(segments.map { it.value.coerceAtLeast(0) }))
    Box(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val data = values().firstOrNull().orEmpty()
            val total = data.sum()
            val y = size.height / 2f
            drawLine(ContinuumColors.Hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            if (total <= 0f) return@Canvas
            val gap = 3.dp.toPx()
            var x = 0f
            data.forEachIndexed { i, v ->
                if (v <= 0f) return@forEachIndexed
                val w = (v / total) * size.width
                val end = (x + w - gap).coerceAtLeast(x + 1f)
                val c = segments.getOrNull(i)?.color ?: ContinuumColors.Muted
                drawLine(c.copy(alpha = 0.2f), Offset(x, y), Offset(end, y), strokeWidth = size.height, cap = StrokeCap.Butt)
                drawLine(c, Offset(x, y), Offset(end, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                x += w
            }
        }
    }
}
