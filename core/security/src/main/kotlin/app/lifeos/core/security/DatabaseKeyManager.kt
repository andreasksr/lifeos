package app.lifeos.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Verwaltet die Passphrase der SQLCipher-Datenbank (ADR-004, ARCHITECTURE.md §3).
 *
 * Ablauf:
 *  1. Ein AES-256-GCM-Schlüssel ("Key Encryption Key") wird im Android Keystore erzeugt.
 *     Er ist nicht exportierbar und verlässt nie die sichere Hardware (sofern vorhanden).
 *  2. Die eigentliche DB-Passphrase sind 32 Zufallsbytes. Sie wird mit dem KEK verschlüsselt und als
 *     `[version | iv(12) | ciphertext+tag]` in `noBackupFilesDir` abgelegt.
 *  3. Beim Start wird sie entschlüsselt und an SQLCipher übergeben.
 *
 * Bewusst KEIN `setUserAuthenticationRequired`: sonst würde der Schlüssel bei jeder Änderung der
 * Biometrie ungültig und alle Daten wären verloren (Risiko R-1). Zugriffsschutz per App-Sperre folgt
 * als eigene Funktion (Roadmap Phase 3).
 *
 * Achtung: Geht der Keystore-Eintrag verloren (Werksreset, Neuinstallation), ist die Datenbank
 * unwiderruflich unlesbar. Deshalb gibt es das passphrase-basierte, gerätunabhängige Backup.
 */
class DatabaseKeyManager(
    private val context: Context,
    private val alias: String = DEFAULT_ALIAS,
) {

    sealed interface KeyState {
        /** Passphrase verfügbar. */
        data class Ready(val passphrase: ByteArray) : KeyState

        /** Es existiert eine DB-Datei, aber der Schlüssel fehlt oder ist unbrauchbar. */
        data class Unrecoverable(val reason: String) : KeyState
    }

    private val keyFile: File get() = File(context.noBackupFilesDir, KEY_FILE_NAME)

    /**
     * Liefert die DB-Passphrase. Legt sie beim allerersten Start an.
     * Wirft keine Exception für den erwartbaren Fall „Schlüssel verloren", sondern meldet
     * [KeyState.Unrecoverable], damit die App einen verständlichen Wiederherstellungsdialog zeigt.
     */
    @Synchronized
    fun obtainPassphrase(databaseExists: Boolean): KeyState {
        val file = keyFile
        if (!file.exists()) {
            if (databaseExists) {
                return KeyState.Unrecoverable("Schlüsseldatei fehlt, Datenbank existiert bereits.")
            }
            val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
            writeAtomically(file, encrypt(passphrase))
            return KeyState.Ready(passphrase)
        }
        return try {
            KeyState.Ready(decrypt(file.readBytes()))
        } catch (e: Exception) {
            KeyState.Unrecoverable("Schlüssel nicht entschlüsselbar: ${e.javaClass.simpleName}")
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unerwartete IV-Länge ${iv.size}" }
        val sealed = cipher.doFinal(plain)
        return byteArrayOf(FORMAT_VERSION) + iv + sealed
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > 1 + IV_BYTES && blob[0] == FORMAT_VERSION) { "Unbekanntes Schlüsselformat" }
        val iv = blob.copyOfRange(1, 1 + IV_BYTES)
        val sealed = blob.copyOfRange(1 + IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(sealed)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        check(tmp.renameTo(target)) { "Schlüsseldatei konnte nicht geschrieben werden" }
    }

    companion object {
        const val DEFAULT_ALIAS = "lifeos.db.kek.v1"
        private const val KEY_FILE_NAME = "lifeos.db.key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PASSPHRASE_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val FORMAT_VERSION: Byte = 1
    }
}
