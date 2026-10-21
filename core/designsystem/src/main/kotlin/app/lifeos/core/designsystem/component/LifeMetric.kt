package app.lifeos.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.model.CurrencyCode
import app.lifeos.core.model.MoneyText
import kotlin.math.roundToLong

enum class MetricSize { HUGE, LARGE, REGULAR, SMALL }

/**
 * Animierter exakter Betrag: zwischen zwei Werten wird weich interpoliert, im Ruhezustand ist der
 * angezeigte Wert IMMER exakt der Zielwert (keine Rundungsreste).
 */
@Composable
fun animatedMinor(target: Long): Long {
    val motion = Continuum.motion
    var from by remember { mutableLongStateOf(target) }
    var to by remember { mutableLongStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to && progress.value >= 1f) return@LaunchedEffect
        val current = if (progress.value >= 1f) to else from + ((to - from).toDouble() * progress.value).roundToLong()
        from = current
        to = target
        if (!motion.enabled) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(motion.emphasisMillis, easing = FastOutSlowInEasing))
        }
    }
    val p = progress.value
    return if (p >= 1f) to else from + ((to - from).toDouble() * p).roundToLong()
}

/**
 * Typografisch gesetzter Geldbetrag: ganze Einheiten groß, Nachkommastellen und Symbol kleiner und
 * gedämpft – Finanzzahlen sind so auf einen Blick erfassbar.
 */
@Composable
fun LifeAmount(
    minor: Long,
    modifier: Modifier = Modifier,
    currency: CurrencyCode = CurrencyCode.EUR,
    size: MetricSize = MetricSize.REGULAR,
    showPlus: Boolean = false,
    color: Color = ContinuumColors.Frost,
    animate: Boolean = true,
) {
    val shown = if (animate) animatedMinor(minor) else minor
    val type = Continuum.type
    val style: TextStyle = when (size) {
        MetricSize.HUGE -> type.figuresHuge
        MetricSize.LARGE -> type.figuresLarge
        MetricSize.REGULAR -> type.figuresMedium
        MetricSize.SMALL -> type.figures
    }
    Text(
        text = amountText(shown, currency, showPlus, style, color),
        style = style,
        modifier = modifier.clearAndSetSemantics { contentDescription = MoneyText.format(minor, currency, showPlus) },
    )
}

private fun amountText(minor: Long, currency: CurrencyCode, showPlus: Boolean, style: TextStyle, color: Color): AnnotatedString {
    val number = MoneyText.format(minor, currency, showPlus = showPlus, withSymbol = false)
    val comma = number.lastIndexOf(',')
    val whole = if (comma >= 0) number.substring(0, comma) else number
    val fraction = if (comma >= 0) number.substring(comma) else ""
    val small = style.fontSize * 0.62f
    return buildAnnotatedString {
        withStyle(SpanStyle(color = color)) { append(whole) }
        withStyle(SpanStyle(color = color.copy(alpha = 0.72f), fontSize = small)) { append(fraction) }
        withStyle(SpanStyle(color = ContinuumColors.Muted, fontSize = small)) { append(MoneyText.NNBSP).append(MoneyText.symbol(currency)) }
    }
}

/**
 * LifeMetric – eine Kennzahl. Label in Versalien, Wert in Monospace, optional Messlinie und Zusatztext.
 * Zeigt nur übergebene, echte Werte an.
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
    MetricFrame(label, caption, tone, progress, progressMeaning, modifier, listOfNotNull(label, value, caption).joinToString(", ")) {
        val type = Continuum.type
        Text(
            text = value,
            style = when (size) {
                MetricSize.HUGE -> type.figuresHuge
                MetricSize.LARGE -> type.figuresLarge
                MetricSize.REGULAR -> type.figuresMedium
                MetricSize.SMALL -> type.figures
            },
            color = if (tone == NodeTone.ALERT) ContinuumColors.Alert else ContinuumColors.Frost,
        )
    }
}

/** LifeMetric für Geldbeträge – animiert und typografisch gesetzt. */
@Composable
fun LifeMoneyMetric(
    label: String,
    minor: Long,
    modifier: Modifier = Modifier,
    currency: CurrencyCode = CurrencyCode.EUR,
    caption: String? = null,
    tone: NodeTone = NodeTone.NEUTRAL,
    size: MetricSize = MetricSize.REGULAR,
    showPlus: Boolean = false,
    progress: Float? = null,
    progressMeaning: LineMeaning = LineMeaning.PROGRESS,
) {
    val description = listOfNotNull(label, MoneyText.format(minor, currency, showPlus), caption).joinToString(", ")
    MetricFrame(label, caption, tone, progress, progressMeaning, modifier, description) {
        LifeAmount(
            minor = minor,
            currency = currency,
            size = size,
            showPlus = showPlus,
            color = if (tone == NodeTone.ALERT) ContinuumColors.Alert else ContinuumColors.Frost,
        )
    }
}

@Composable
private fun MetricFrame(
    label: String,
    caption: String?,
    tone: NodeTone,
    progress: Float?,
    progressMeaning: LineMeaning,
    modifier: Modifier,
    description: String,
    value: @Composable () -> Unit,
) {
    Column(
        modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tone != NodeTone.NEUTRAL) {
                LifeNode(shape = NodeShape.DOT, tone = tone, size = 8.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(label.uppercase(), style = Continuum.type.label)
        }
        value()
        if (progress != null) LifeLine(progress = progress, meaning = progressMeaning)
        if (caption != null) Text(caption, style = Continuum.type.small)
    }
}
