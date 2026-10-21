package app.lifeos.documents

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.lifeos.core.domain.life.DocumentStore
import app.lifeos.core.domain.life.DocumentTooLargeException
import app.lifeos.core.model.Document
import app.lifeos.core.security.vault.FileVault
import app.lifeos.feature.life.DocumentAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Verzeichnis des verschlüsselten Dokumenttresors (App-intern, nicht im Android-Backup). */
fun vaultDir(context: Context): File = File(context.noBackupFilesDir, "vault")

/** Brücke Domäne ↔ Tresor: speichert/liest Dateien verschlüsselt, inhaltsadressiert. */
class VaultDocumentStore(val vault: FileVault) : DocumentStore {
    override suspend fun store(input: InputStream): DocumentStore.Stored = withContext(Dispatchers.IO) {
        try {
            val s = vault.store(input)
            DocumentStore.Stored(s.sha256, s.sizeBytes, s.alreadyPresent)
        } catch (e: FileVault.TooLarge) {
            throw DocumentTooLargeException(e.limitBytes)
        }
    }

    override suspend fun delete(sha256: String) = withContext(Dispatchers.IO) { vault.delete(sha256) }
}

/**
 * Lesen und „In anderer App öffnen". Für das Öffnen wird eine entschlüsselte Kopie im privaten Cache
 * (`cache/share/`) abgelegt und nur per FileProvider mit Lese-Erlaubnis freigegeben; der Ordner wird bei
 * jedem App-Start und vor jeder neuen Freigabe geleert.
 */
class AndroidDocumentAccess(private val context: Context, private val vault: FileVault) : DocumentAccess {
    override val maxBytes: Long get() = vault.maxBytes

    override suspend fun read(document: Document): ByteArray = withContext(Dispatchers.IO) { vault.read(document.sha256) }

    override suspend fun openExternally(document: Document): Boolean {
        val uri = withContext(Dispatchers.IO) {
            val dir = shareDir(context)
            clearShared(context)
            dir.mkdirs()
            val safeName = document.fileName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80).ifBlank { "dokument" }
            val file = File(dir, safeName)
            file.writeBytes(vault.read(document.sha256))
            FileProvider.getUriForFile(context, context.packageName + ".documents", file)
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, document.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(Intent.createChooser(intent, document.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    companion object {
        fun shareDir(context: Context) = File(context.cacheDir, "share")

        /** Entfernt freigegebene Klartext-Kopien (App-Start, vor neuer Freigabe). */
        fun clearShared(context: Context) {
            shareDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}
