package app.lifeos.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing

/**
 * LifeOverlay – Detailfenster, das von unten aus der LifeLine aufsteigt (einhändig erreichbar).
 * Schließt per Zurück-Geste, Tippen auf den Hintergrund oder [onDismiss]. Die Ein-/Ausblendung
 * respektiert die Bewegungseinstellung.
 */
@Composable
fun LifeOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    overline: String? = null,
    fullHeight: Boolean = false,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState) return
    val motion = Continuum.motion

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visibleState = state,
                enter = fadeIn(tween(motion.standardMillis)),
                exit = fadeOut(tween(motion.quickMillis)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(ContinuumColors.Scrim)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                )
            }
            AnimatedVisibility(
                visibleState = state,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(motion.standardMillis)) { it / 3 } + fadeIn(tween(motion.standardMillis)),
                exit = slideOutVertically(tween(motion.quickMillis)) { it / 3 } + fadeOut(tween(motion.quickMillis)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (fullHeight) Modifier.fillMaxHeight(0.94f) else Modifier)
                        .statusBarsPadding()
                        .semantics { if (title != null) paneTitle = title }
                        .drawBehind {
                            val cut = ContinuumSpacing.chamfer.toPx()
                            val p = Path().apply {
                                moveTo(cut, 0f); lineTo(size.width - cut, 0f); lineTo(size.width, cut)
                                lineTo(size.width, size.height); lineTo(0f, size.height); lineTo(0f, cut); close()
                            }
                            drawPath(p, ContinuumColors.Deep)
                            drawLine(
                                Brush.horizontalGradient(listOf(Color.Transparent, ContinuumColors.Cyan, ContinuumColors.Violet, Color.Transparent)),
                                Offset(cut, 0f), Offset(size.width - cut, 0f), strokeWidth = 1.5.dp.toPx(),
                            )
                        }
                        // Klicks innerhalb des Panels nicht an den Hintergrund durchreichen
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = ContinuumSpacing.gutter)
                        .padding(top = ContinuumSpacing.l, bottom = ContinuumSpacing.m),
                    verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.m),
                ) {
                    if (title != null || overline != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if (overline != null) Text(overline.uppercase(), style = Continuum.type.label)
                                if (title != null) Text(title, style = Continuum.type.headline, modifier = Modifier.semantics { heading() })
                            }
                            actions()
                            LifeIconButton(Glyph.CLOSE, "Schließen", onDismiss)
                        }
                    }
                    content()
                }
            }
        }
    }
}
