package app.lifeos.core.designsystem.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Material3 wird nur als technisches Fundament genutzt (DatePicker, Textauswahl, Ripple).
 * Dieses Mapping sorgt dafür, dass auch diese Bausteine CONTINUUM-Farben tragen.
 */
@Composable
internal fun MaterialBridge(content: @Composable () -> Unit) {
    val c = ContinuumColors
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = c.Cyan,
            onPrimary = c.Obsidian,
            primaryContainer = c.Elevated,
            onPrimaryContainer = c.Frost,
            secondary = c.Violet,
            onSecondary = c.Obsidian,
            tertiary = c.Mint,
            background = c.Obsidian,
            onBackground = c.Frost,
            surface = c.Deep,
            onSurface = c.Frost,
            surfaceVariant = c.Elevated,
            onSurfaceVariant = c.Muted,
            surfaceContainerLowest = c.Obsidian,
            surfaceContainerLow = c.Deep,
            surfaceContainer = c.Deep,
            surfaceContainerHigh = c.Elevated,
            surfaceContainerHighest = c.Elevated,
            outline = c.HairlineStrong,
            outlineVariant = c.Hairline,
            error = c.Alert,
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
