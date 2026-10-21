package app.lifeos.core.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.lifeos.core.designsystem.R

/**
 * Eingebettete Schriften (SIL Open Font License, siehe `core/designsystem/licenses/`).
 * Alle drei decken Umlaute, ß, €, typografische Anführungszeichen und Gedankenstriche ab
 * (geprüft mit fontTools, siehe CHANGELOG 0.2.0).
 */
object LifeFonts {
    val Grotesk = FontFamily(
        Font(R.font.space_grotesk_light, FontWeight.Light),
        Font(R.font.space_grotesk_regular, FontWeight.Normal),
        Font(R.font.space_grotesk_medium, FontWeight.Medium),
    )

    val Inter = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

    val Mono = FontFamily(
        Font(R.font.jetbrains_mono_light, FontWeight.Light),
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    )
}
