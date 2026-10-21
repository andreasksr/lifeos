package app.lifeos.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LIFEOS CONTINUUM – Design-Tokens (v2). Einzige Quelle für Farben, Typografie, Abstände und Bewegung.
 * Feature-Code verwendet ausschließlich diese Tokens.
 */
@Immutable
object ContinuumColors {
    // Grundpalette (Auftrag 2.3)
    val Obsidian = Color(0xFF080C14)
    val Deep = Color(0xFF101827)
    val Elevated = Color(0xFF172235)
    val Frost = Color(0xFFF0F5FF)
    val Muted = Color(0xFF91A5BF)
    val Cyan = Color(0xFF8DEAFF)
    val Violet = Color(0xFFB29AFF)
    val Mint = Color(0xFF62E6C7)

    /** Tertiärtext – nur für Nebeninformationen (Kontrast ≈ 4.6:1 auf Obsidian). */
    val Faint = Color(0xFF74859C)

    // Funktionale Zusatzfarben – nur für Zustände, immer zusammen mit Text/Form.
    /** Bald fällig / Achtung. */
    val Amber = Color(0xFFFFC27A)

    /** Überschritten / überfällig / Fehler. */
    val Alert = Color(0xFFFF7D9C)

    val Hairline = Color(0x1AF0F5FF)
    val HairlineStrong = Color(0x33F0F5FF)
    val Scrim = Color(0xCC04060B)

    /** Kategorische Reihe für Verteilungen – aus der Grundpalette abgeleitet, keine Neonfarben. */
    val Series: List<Color> = listOf(
        Cyan, Violet, Mint, Color(0xFF6E9BFF), Color(0xFFD7A6FF), Color(0xFF4FC3D9),
        Color(0xFF9FB8FF), Color(0xFF7FD9B9), Color(0xFFC2B5E8), Muted,
    )

    val Signature: Brush get() = Brush.horizontalGradient(listOf(Cyan, Violet))
    val SignatureVertical: Brush get() = Brush.verticalGradient(listOf(Cyan, Violet))

    // ── Aliasse für Bestandscode aus v0.1 ──
    val ObsidianRaised get() = Deep
    val ObsidianHigh get() = Elevated
    val Ice get() = Frost
    val IceMuted get() = Muted
    val IceFaint get() = Faint
    val Flare get() = Alert
}

@Immutable
object ContinuumSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp
    val gutter: Dp = 20.dp

    /** Breite der Spalte, in der die LifeLine (Spine) vertikal verläuft. */
    val spineRail: Dp = 48.dp

    /** x-Position der Spine-Linie (Mitte der Rail) – Zurück-Knopf und Knoten sitzen genau darauf. */
    val spineX: Dp = 24.dp

    /** Alias für v0.1-Zeitleisten. */
    val timelineRail: Dp = 28.dp

    /** Mindestgröße für Touch-Ziele (Barrierefreiheit). */
    val touchTarget: Dp = 48.dp

    val hairline: Dp = 1.dp
    val line: Dp = 1.5.dp
    val chamfer: Dp = 14.dp

    /** Höhe der LifeNavigation ohne Systemleiste. */
    val navHeight: Dp = 72.dp
}

private val Trimmed = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/**
 * Typografie: Space Grotesk (Überschriften, Labels), Inter (Fließtext), JetBrains Mono (alle Zahlen).
 * Alle Größen in sp – skalieren mit der Systemschriftgröße.
 */
@Immutable
data class ContinuumTypography(
    /** Große Seitentitel / Datum auf Home. */
    val display: TextStyle = TextStyle(
        fontFamily = LifeFonts.Grotesk, fontWeight = FontWeight.Light,
        fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-1).sp, color = ContinuumColors.Frost,
    ),
    val headline: TextStyle = TextStyle(
        fontFamily = LifeFonts.Grotesk, fontWeight = FontWeight.Normal,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp, color = ContinuumColors.Frost,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = LifeFonts.Grotesk, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp, color = ContinuumColors.Frost,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = LifeFonts.Inter, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp, color = ContinuumColors.Frost,
    ),
    val bodyMuted: TextStyle = body.copy(color = ContinuumColors.Muted),
    val small: TextStyle = TextStyle(
        fontFamily = LifeFonts.Inter, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, color = ContinuumColors.Muted,
    ),
    /** Technische Beschriftung: Versalien, kontrollierte Laufweite. */
    val label: TextStyle = TextStyle(
        fontFamily = LifeFonts.Grotesk, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp, color = ContinuumColors.Muted,
        lineHeightStyle = Trimmed,
    ),
    val figures: TextStyle = TextStyle(
        fontFamily = LifeFonts.Mono, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum", color = ContinuumColors.Frost,
    ),
    val figuresSmall: TextStyle = figures.copy(fontSize = 12.sp, lineHeight = 16.sp),
    val figuresMedium: TextStyle = figures.copy(fontSize = 20.sp, lineHeight = 26.sp),
    val figuresLarge: TextStyle = TextStyle(
        fontFamily = LifeFonts.Mono, fontWeight = FontWeight.Light,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum",
        color = ContinuumColors.Frost,
    ),
    val figuresHuge: TextStyle = figuresLarge.copy(fontSize = 52.sp, lineHeight = 58.sp, letterSpacing = (-2).sp),
)

/**
 * Bewegung. Wenn [enabled] false ist (Nutzereinstellung ODER System „Animationen entfernen"),
 * springen alle Komponenten direkt in den Endzustand und Endlos-Animationen laufen nicht.
 */
@Immutable
data class ContinuumMotion(val enabled: Boolean = true) {
    val quickMillis: Int get() = if (enabled) 160 else 0
    val standardMillis: Int get() = if (enabled) 320 else 0
    val emphasisMillis: Int get() = if (enabled) 620 else 0

    /** Verzögerung zwischen gestaffelt erscheinenden Elementen. */
    val staggerMillis: Int get() = if (enabled) 55 else 0

    /** Periode des wandernden Lichtpulses. */
    val pulseMillis: Int = 5200
}

val LocalContinuumTypography = staticCompositionLocalOf { ContinuumTypography() }
val LocalContinuumMotion = staticCompositionLocalOf { ContinuumMotion() }

object Continuum {
    val colors: ContinuumColors get() = ContinuumColors
    val spacing: ContinuumSpacing get() = ContinuumSpacing

    val type: ContinuumTypography
        @Composable @ReadOnlyComposable get() = LocalContinuumTypography.current

    val motion: ContinuumMotion
        @Composable @ReadOnlyComposable get() = LocalContinuumMotion.current
}

@Composable
fun ContinuumTheme(motionEnabled: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContinuumTypography provides ContinuumTypography(),
        LocalContinuumMotion provides ContinuumMotion(motionEnabled),
    ) {
        MaterialBridge(content)
    }
}
