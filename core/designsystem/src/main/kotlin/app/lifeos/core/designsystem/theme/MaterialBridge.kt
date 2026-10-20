package app.lifeos.core.designsystem.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Material3 wird nur als technisches Fundament genutzt (Dialoge, Datumsauswahl, Textauswahl).
 * Dieses Mapping sorgt dafür, dass auch diese Bausteine CONTINUUM-Farben tragen.
 */
@Composable
internal fun MaterialBridge(content: @Composable () -> Unit) {
    val c = ContinuumColors
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = c.Cyan,
            onPrimary = c.Obsidian,
            secondary = c.Violet,
            onSecondary = c.Obsidian,
            background = c.Obsidian,
            onBackground = c.Ice,
            surface = c.ObsidianRaised,
            onSurface = c.Ice,
            surfaceVariant = c.ObsidianHigh,
            onSurfaceVariant = c.IceMuted,
            surfaceContainerHigh = c.ObsidianHigh,
            surfaceContainerHighest = c.ObsidianHigh,
            surfaceContainer = c.ObsidianRaised,
            outline = c.HairlineStrong,
            outlineVariant = c.Hairline,
            error = c.Flare,
            onError = c.Obsidian,
        ),
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = c.Cyan,
                backgroundColor = c.Cyan.copy(alpha = 0.3f),
            ),
            content = content,
        )
    }
}
