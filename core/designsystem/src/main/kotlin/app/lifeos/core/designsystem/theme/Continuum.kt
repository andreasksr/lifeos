package app.lifeos.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CONTINUUM – Design-Tokens. Einzige Quelle für Farben, Typografie, Abstände und Bewegung.
 * Komponenten verwenden ausschließlich diese Tokens (keine Hex-Werte in Feature-Code).
 */
@Immutable
object ContinuumColors {
    /** Tiefes Obsidian – Hintergrund. */
    val Obsidian = Color(0xFF05060A)
    val ObsidianRaised = Color(0xFF0A0C12)
    val ObsidianHigh = Color(0xFF11141C)

    /** Haarlinien – Konturen statt Flächen. */
    val Hairline = Color(0x24DCE8FF)
    val HairlineStrong = Color(0x45DCE8FF)

    /** Eisweiß – Typografie. Kontrast auf Obsidian: Ice ≈ 18:1, IceMuted ≈ 8:1, IceFaint ≈ 4.6:1. */
    val Ice = Color(0xFFEAF2FF)
    val IceMuted = Color(0xFFA3AFC2)
    val IceFaint = Color(0xFF6F7A8E)

    /** Lichtsignaturen. Cyan = Zufluss/aktiv, Violett = Abfluss/Struktur. */
    val Cyan = Color(0xFF52E5F2)
    val Violet = Color(0xFF9D7CFF)

    /** Nur für Grenzüberschreitungen (Budget überzogen) – immer zusätzlich mit Text/Symbol. */
    val Flare = Color(0xFFFF7AB6)

    val Signature: Brush get() = Brush.horizontalGradient(listOf(Cyan, Violet))
    val SignatureVertical: Brush get() = Brush.verticalGradient(listOf(Cyan, Violet))
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
    val gutter: Dp = 20.dp

    /** Breite der linken Zeitleisten-Spalte, in der die LifeLine verläuft. */
    val timelineRail: Dp = 28.dp

    /** Mindestgröße für Touch-Ziele (Barrierefreiheit). */
    val touchTarget: Dp = 48.dp

    val hairline: Dp = 1.dp
    val line: Dp = 1.5.dp
    val chamfer: Dp = 10.dp
}

/**
 * Typografie. Ziffern immer in [figures] (Monospace + tabellarische Ziffern), damit Beträge
 * untereinander exakt fluchten. Eigene Schriften (z. B. Space Grotesk / JetBrains Mono, OFL) werden
 * in Phase 2 eingebunden – bis dahin Systemschriften.
 */
@Immutable
data class ContinuumTypography(
    val display: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp, color = ContinuumColors.Ice,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp, color = ContinuumColors.Ice,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp, color = ContinuumColors.Ice,
    ),
    val bodyMuted: TextStyle = body.copy(color = ContinuumColors.IceMuted),
    /** Versal-Label mit Laufweite – Abschnitte, Metrik-Beschriftungen. */
    val label: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.6.sp, color = ContinuumColors.IceMuted,
    ),
    val figures: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum", color = ContinuumColors.Ice,
    ),
    val figuresLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum",
        color = ContinuumColors.Ice,
    ),
)

/**
 * Bewegung. Wenn [enabled] false ist (Nutzereinstellung ODER System „Animationen entfernen"),
 * springen alle Komponenten direkt in den Endzustand und Endlos-Animationen laufen nicht.
 */
@Immutable
data class ContinuumMotion(val enabled: Boolean = true) {
    val quickMillis: Int get() = if (enabled) 160 else 0
    val standardMillis: Int get() = if (enabled) 320 else 0
    val emphasisMillis: Int get() = if (enabled) 560 else 0

    /** Periode des wandernden Lichtpulses auf der LifeLine. */
    val pulseMillis: Int = 4200
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
