package app.lifeos.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing

/** Fortschritt der Einstiegsanimation (0 = nichts sichtbar, 1 = alles da). Als Lambda: kein Recomposition-Sturm. */
val LocalSpineReveal = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * LifeFrame – der gemeinsame Rahmen aller Ansichten. Eine einzige LifeLine läuft links vom Kopf
 * bis zur Navigation und biegt dort in die Navigationslinie ein: alle Bereiche hängen sichtbar an
 * derselben Linie.
 *
 * - Beim ersten Öffnen zeichnet sich die Linie von oben nach unten, danach ordnen sich die Abschnitte
 *   gestaffelt entlang der Linie an ([playIntro]).
 * - Bei [LifeSignalState.pulse] läuft ein kurzer Lichtimpuls die Linie entlang (z. B. nach dem Speichern).
 * - Keine Dauer-Animation: die Linie ist im Ruhezustand statisch (Akku, Rendering).
 */
@Composable
fun LifeFrame(
    signal: LifeSignalState,
    modifier: Modifier = Modifier,
    playIntro: Boolean = true,
    navigation: (@Composable (lineStartX: Dp) -> Unit)? = null,
    capture: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val motion = Continuum.motion
    var introDone by rememberSaveable { mutableStateOf(false) }
    val reveal = remember { Animatable(if (introDone || !playIntro || !motion.enabled) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) {
            reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
        introDone = true
    }
    val pulse = remember { Animatable(-1f) }
    LaunchedEffect(signal.pulseCount) {
        if (signal.pulseCount > 0 && motion.enabled) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(900, easing = LinearEasing))
            pulse.snapTo(-1f)
        }
    }
    var navTop by remember { mutableFloatStateOf(Float.NaN) }
    val radius = 18.dp
    val spineX = ContinuumSpacing.spineX

    CompositionLocalProvider(
        LocalSpineReveal provides { reveal.value },
        LocalLifeSignal provides signal,
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(ContinuumColors.Obsidian),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val x = spineX.toPx()
                val r = radius.toPx()
                val bottom = if (navTop.isNaN()) size.height else navTop
                val hasNav = !navTop.isNaN()
                val vertical = (if (hasNav) bottom - r else bottom).coerceAtLeast(0f)
                val arcLen = if (hasNav) (Math.PI * r / 2).toFloat() else 0f
                val horizontal = if (hasNav) (size.width - x - r).coerceAtLeast(0f) else 0f
                val total = vertical + arcLen + horizontal
                val shown = total * reveal.value

                val brush = Brush.verticalGradient(
                    listOf(ContinuumColors.Cyan.copy(alpha = 0.75f), ContinuumColors.Violet.copy(alpha = 0.55f)),
                    startY = 0f, endY = bottom,
                )
                val vEnd = minOf(shown, vertical)
                drawLine(brush, Offset(x, 0f), Offset(x, vEnd), strokeWidth = 5.dp.toPx(), alpha = 0.07f)
                drawLine(brush, Offset(x, 0f), Offset(x, vEnd), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                if (hasNav && shown > vertical) {
                    val sweep = 90f * ((shown - vertical) / arcLen).coerceAtMost(1f)
                    drawArc(
                        ContinuumColors.Violet.copy(alpha = 0.55f), 180f, -sweep, false,
                        topLeft = Offset(x, bottom - 2 * r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                // Lichtimpuls
                val p = pulse.value
                if (p >= 0f) {
                    val d = total * p
                    val pos = when {
                        d <= vertical -> Offset(x, d)
                        d <= vertical + arcLen -> {
                            val a = Math.toRadians(180.0 - 90.0 * ((d - vertical) / arcLen))
                            Offset(x + r + (r * kotlin.math.cos(a)).toFloat(), bottom - r + (r * kotlin.math.sin(a)).toFloat())
                        }
                        else -> Offset(x + r + (d - vertical - arcLen), bottom)
                    }
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), ContinuumColors.Cyan.copy(alpha = 0.35f), Color.Transparent), center = pos, radius = 16.dp.toPx()),
                        radius = 16.dp.toPx(), center = pos,
                    )
                }
            }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { content() }
                if (navigation != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { navTop = it.positionInRoot().y },
                    ) {
                        navigation(spineX + radius)
                    }
                }
            }
            if (navigation == null) {
                LaunchedEffect(Unit) { navTop = Float.NaN }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                LifeToastHost(signal, Modifier.fillMaxWidth())
                Spacer(Modifier.height(if (navigation != null) ContinuumSpacing.navHeight + 56.dp else 24.dp))
            }
            if (capture != null && navigation != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = ContinuumSpacing.l, bottom = ContinuumSpacing.navHeight - 8.dp),
                ) { capture() }
            }
        }
    }
}

/** Blendet ein Element gestaffelt entlang der Linie ein (Einstiegsanimation). */
fun Modifier.spineReveal(index: Int, reveal: () -> Float): Modifier = this.graphicsLayer {
    val r = reveal()
    val local = ((r * 1.5f) - index * 0.09f).coerceIn(0f, 0.5f) / 0.5f
    alpha = local
    translationX = (1f - local) * 24.dp.toPx()
}

/**
 * Eine Zeile an der Linie: links die Rail mit (optionalem) Knoten exakt auf der Spine, rechts der Inhalt.
 */
@Composable
fun SpineRow(
    modifier: Modifier = Modifier,
    node: (@Composable () -> Unit)? = null,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth().padding(end = ContinuumSpacing.gutter), verticalAlignment = verticalAlignment) {
        Box(Modifier.width(ContinuumSpacing.spineRail), contentAlignment = Alignment.Center) {
            node?.invoke()
        }
        content()
    }
}

/**
 * Abschnitt an der LifeLine: Knoten (interaktiv, öffnet Details) + Titel + optionale Aktion,
 * darunter eingerückter Inhalt.
 */
@Composable
fun SpineSection(
    title: String,
    modifier: Modifier = Modifier,
    index: Int = 0,
    shape: NodeShape = NodeShape.RING,
    tone: NodeTone = NodeTone.NEUTRAL,
    onOpen: (() -> Unit)? = null,
    openLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** true: Inhalt beginnt links an der Linie (für Zeitachsen, deren Knoten auf der Linie sitzen). */
    fullBleed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reveal = LocalSpineReveal.current
    Column(modifier.fillMaxWidth().spineReveal(index, reveal)) {
        SpineRow(
            modifier = Modifier.heightIn(min = 48.dp),
            node = {
                LifeNode(
                    shape = shape, tone = tone, size = 14.dp, active = tone != NodeTone.NEUTRAL,
                    onClick = onOpen, onClickLabel = openLabel ?: title,
                )
            },
        ) {
            Text(
                title.uppercase(),
                style = Continuum.type.label,
                color = ContinuumColors.Muted,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            trailing?.invoke()
        }
        Column(
            if (fullBleed) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(if (fullBleed) 0.dp else ContinuumSpacing.m),
        ) { content() }
        Spacer(Modifier.height(ContinuumSpacing.xl))
    }
}

/**
 * Kopfzeile. Der Zurück-Knopf (oder ein Ursprungsknoten) sitzt exakt auf der LifeLine.
 */
@Composable
fun LifeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    large: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = ContinuumSpacing.s, bottom = ContinuumSpacing.m, end = ContinuumSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(ContinuumSpacing.spineRail), contentAlignment = Alignment.Center) {
            if (onBack != null) {
                LifeIconButton(Glyph.BACK, "Zurück", onBack, modifier = Modifier.background(ContinuumColors.Obsidian), tint = ContinuumColors.Frost)
            } else {
                LifeNode(shape = NodeShape.RING, tone = NodeTone.ACTIVE, active = true, size = 14.dp)
            }
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            if (overline != null) Text(overline.uppercase(), style = Continuum.type.label, color = ContinuumColors.Cyan)
            Text(
                title,
                style = if (large) Continuum.type.display else Continuum.type.headline,
                modifier = Modifier.semantics { heading() },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, style = Continuum.type.small)
        }
        actions()
    }
}

/**
 * Leerzustand an der Linie: gestrichelte Fortsetzung, ein klarer Satz, optional eine Aktion.
 * Niemals Platzhalterdaten.
 */
@Composable
fun LifeEmpty(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    glyph: Glyph? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .drawBehind {
                drawLine(
                    ContinuumColors.HairlineStrong,
                    Offset(0f, size.height / 2), Offset(size.width * 0.22f, size.height / 2),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                )
            }
            .padding(start = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(24.dp))
        if (glyph != null) {
            LifeGlyph(glyph, tint = ContinuumColors.Faint, size = 20.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, style = Continuum.type.bodyMuted)
            if (actionLabel != null && onAction != null) LifeLink(actionLabel, onAction)
        }
    }
}

/** Kleine Kennzeichnung (z. B. „PLAN", „BEISPIEL", „ÜBERFÄLLIG"). */
@Composable
fun LifeTag(text: String, tone: NodeTone = NodeTone.NEUTRAL, modifier: Modifier = Modifier) {
    val c = toneColor(tone)
    Text(
        text.uppercase(),
        style = Continuum.type.label.copy(fontSize = Continuum.type.label.fontSize * 0.9f),
        color = c,
        modifier = modifier
            .drawBehind {
                drawRect(c.copy(alpha = 0.10f))
                drawLine(c.copy(alpha = 0.6f), Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx())
            }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
