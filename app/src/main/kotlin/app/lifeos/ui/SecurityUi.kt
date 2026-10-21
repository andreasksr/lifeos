package app.lifeos.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.AppContainer
import app.lifeos.LifeOsApplication
import app.lifeos.backup.BackupManager
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeGlyph
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeToastMessage
import app.lifeos.core.designsystem.component.LifeToggle
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.security.vault.BackupCipher
import app.lifeos.security.AppLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.exitProcess

fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Startet den App-Prozess neu (nach einer Wiederherstellung zwingend, damit nur noch der neue Stand offen ist). */
fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(Intent.makeRestartActivityTask(launch.component))
    exitProcess(0)
}

private enum class BackupMode { EXPORT, RESTORE }

/** Datensicherung: Export (Passwort → Datei wählen) und Wiederherstellung (Datei → Passwort → Bestätigung). */
@Composable
fun BackupSection(container: AppContainer, index: Int) {
    val context = LocalContext.current
    val app = context.applicationContext as LifeOsApplication
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<BackupMode?>(null) }
    var pass1 by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<String?>(null) }

    fun reset() {
        mode = null; pass1 = ""; pass2 = ""; error = null; restoreUri = null; confirmRestore = false
    }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pass1.toCharArray()
        reset()
        if (uri == null) {
            password.fill(' ')
            return@rememberLauncherForActivityResult
        }
        busy = "Sicherung wird erstellt …"
        scope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Datei nicht beschreibbar" }.use { out -> container.exportBackup(out, password) }
                }
                haptics.confirm()
                lastResult = "Letzte Sicherung: ${LifeFormat.numericDate(LocalDate.now())} · ${summary.documents} Dokumente"
                signal.confirm("Sicherung erstellt – Passwort gut aufbewahren")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                haptics.reject()
                runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
                signal.toast(LifeToastMessage("Sicherung fehlgeschlagen. Es wurde keine gültige Datei geschrieben.", tone = NodeTone.ALERT))
            } finally {
                password.fill(' ')
                busy = null
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreUri = uri
            mode = BackupMode.RESTORE
        }
    }

    SpineSection("Datensicherung", index = index, shape = NodeShape.SQUARE, tone = NodeTone.ACTIVE) {
        Text(
            "Eine Sicherung enthält alle Daten und Dokumente, verschlüsselt mit einem eigenen Passwort (AES-256, PBKDF2). " +
                "Sie lässt sich auf jedem Gerät mit LifeOS wiederherstellen. Ohne das Passwort ist sie nicht lesbar – auch nicht für den Entwickler.",
            style = Continuum.type.small,
        )
        lastResult?.let { Text(it, style = Continuum.type.small, color = ContinuumColors.Mint) }
        busy?.let {
            Text(it, style = Continuum.type.body)
            LifeLine(meaning = LineMeaning.TIME, pulse = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeButton("Sicherung erstellen", { mode = BackupMode.EXPORT }, style = LifeButtonStyle.PRIMARY, glyph = Glyph.ARROW_OUT, enabled = busy == null)
            LifeButton("Wiederherstellen", { openFile.launch(arrayOf("*/*")) }, style = LifeButtonStyle.SECONDARY, glyph = Glyph.ARROW_IN, enabled = busy == null)
        }
    }

    LifeOverlay(visible = mode == BackupMode.EXPORT, onDismiss = ::reset, title = "Sicherung erstellen", overline = "Passwort festlegen") {
        Text("Mindestens 8 Zeichen. Das Passwort wird nirgends gespeichert und kann nicht zurückgesetzt werden.", style = Continuum.type.small)
        LifeTextField(pass1, { pass1 = it.take(128); error = null }, "Passwort", visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        LifeTextField(pass2, { pass2 = it.take(128); error = null }, "Passwort wiederholen", visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), error = error)
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            LifeButton("Speicherort wählen", {
                when {
                    !BackupCipher.isAcceptablePassphrase(pass1.toCharArray()) -> error = "Mindestens 8 Zeichen."
                    pass1 != pass2 -> error = "Die Passwörter stimmen nicht überein."
                    else -> createFile.launch("LifeOS-Sicherung-${LocalDate.now(ZoneId.systemDefault())}.${BackupManager.FILE_EXTENSION}")
                }
            }, glyph = Glyph.CHECK)
        }
    }

    LifeOverlay(visible = mode == BackupMode.RESTORE, onDismiss = ::reset, title = "Wiederherstellen", overline = "Sicherung öffnen") {
        if (!confirmRestore) {
            LifeTextField(pass1, { pass1 = it.take(128); error = null }, "Passwort der Sicherung", visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), error = error)
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                LifeButton("Weiter", { if (pass1.isEmpty()) error = "Bitte Passwort eingeben." else confirmRestore = true }, glyph = Glyph.CHECK)
            }
        } else {
            Text(
                "Alle aktuellen Daten auf diesem Gerät werden durch den Stand der Sicherung ersetzt. Die Sicherung wird vorher vollständig geprüft – bei falschem Passwort oder beschädigter Datei bleibt alles unverändert. Danach startet LifeOS neu.",
                style = Continuum.type.body,
            )
            error?.let { Text(it, style = Continuum.type.body, color = ContinuumColors.Alert) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LifeButton("Abbrechen", ::reset, style = LifeButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                LifeButton("Ersetzen & neu starten", {
                    val uri = restoreUri ?: return@LifeButton
                    val password = pass1.toCharArray()
                    busy = "Sicherung wird geprüft und eingespielt …"
                    scope.launch {
                        try {
                            app.restoreBackup({ requireNotNull(context.contentResolver.openInputStream(uri)) { "Datei nicht lesbar" } }, password)
                            restartApp(context)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: BackupCipher.WrongPassphrase) {
                            error = "Falsches Passwort – oder die Datei ist beschädigt. Es wurde nichts verändert."
                            confirmRestore = false
                        } catch (e: java.io.IOException) {
                            error = (e.message ?: "Datei nicht lesbar") + " Es wurde nichts verändert."
                        } catch (e: Exception) {
                            error = "Keine gültige LifeOS-Sicherung. Es wurde nichts verändert."
                        } finally {
                            password.fill(' ')
                            busy = null
                        }
                    }
                }, style = LifeButtonStyle.DANGER, glyph = Glyph.ARROW_IN, enabled = busy == null)
            }
            busy?.let { LifeLine(meaning = LineMeaning.TIME, pulse = true) }
        }
    }
}

/** App-Sperre ein-/ausschalten – Einschalten erfordert eine erfolgreiche Bestätigung. */
@Composable
fun AppLockSection(container: AppContainer, index: Int) {
    val context = LocalContext.current
    val enabled by container.settings.appLockEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val signal = LocalLifeSignal.current
    val reason = remember { AppLock.unavailableReason(context) }
    SpineSection("App-Sperre", index = index, shape = NodeShape.HEX, tone = if (enabled) NodeTone.ACTIVE else NodeTone.NEUTRAL) {
        LifeToggle(
            checked = enabled,
            onCheckedChange = { want ->
                val activity = context.findFragmentActivity() ?: return@LifeToggle
                AppLock.authenticate(
                    activity, if (want) "App-Sperre einschalten" else "App-Sperre ausschalten",
                    onSuccess = { container.appLock.unlock(); scope.launch { container.settings.setAppLockEnabled(want); signal.confirm(if (want) "App-Sperre aktiv" else "App-Sperre aus") } },
                    onFailure = { msg -> msg?.let { signal.toast(LifeToastMessage(it, tone = NodeTone.ALERT)) } },
                )
            },
            label = "Beim Öffnen entsperren",
            description = "Fingerabdruck, Gesicht oder Geräte-PIN. Nach 30 Sekunden im Hintergrund wird erneut gefragt.",
        )
        if (reason != null && !enabled) Text(reason, style = Continuum.type.small, color = ContinuumColors.Amber)
        Text("Die Sperre schützt die Oberfläche. Die Daten selbst sind unabhängig davon verschlüsselt.", style = Continuum.type.small)
    }
}

/** Sperrbildschirm – nur die LifeLine und ein Entsperren-Knopf, keine Inhalte. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    fun prompt() {
        val activity = context.findFragmentActivity() ?: return
        AppLock.authenticate(activity, "LifeOS entsperren", onSuccess = onUnlocked, onFailure = { message = it })
    }
    LaunchedEffect(Unit) { prompt() }
    Box(Modifier.fillMaxSize().background(ContinuumColors.Obsidian), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            LifeGlyph(Glyph.LOCK, tint = ContinuumColors.Cyan, size = 40.dp)
            Text("LifeOS ist gesperrt", style = Continuum.type.headline)
            LifeLine(meaning = LineMeaning.TIME, pulse = true)
            message?.let { Text(it, style = Continuum.type.small, color = ContinuumColors.Amber) }
            LifeButton("Entsperren", ::prompt, glyph = Glyph.LOCK)
        }
    }
}
