package app.lifeos.core.security.vault

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Verschlüsselter, inhaltsadressierter Dateispeicher für Dokumente (reines JVM, ohne Android).
 *
 * - Jede Datei liegt als `<sha256>.lv` in [dir]; dieselbe Datei existiert daher nie doppelt.
 * - Format: `[version(1) | iv(12) | AES-256-GCM(ciphertext+tag)]`, AAD = SHA-256 als Text – eine
 *   umbenannte/vertauschte Datei fällt beim Entschlüsseln auf.
 * - Schreiben erfolgt atomar (temporäre Datei + Umbenennen).
 *
 * Größengrenze [maxBytes]: Dateien werden komplett im Speicher verschlüsselt; die Grenze hält das
 * auch auf schwächeren Geräten sicher.
 */
class FileVault(private val dir: File, key: ByteArray, val maxBytes: Long = DEFAULT_MAX_BYTES) {
    private val key = SecretKeySpec(key.copyOf(), "AES")
    private val random = SecureRandom()

    init {
        require(key.size == 32) { "Schlüssel muss 32 Bytes haben" }
    }

    data class Stored(val sha256: String, val sizeBytes: Long, val alreadyPresent: Boolean)

    class TooLarge(val limitBytes: Long) : Exception("Datei größer als $limitBytes Bytes")
    class Corrupt(message: String) : Exception(message)

    private fun file(sha: String): File {
        require(sha.length == 64 && sha.all { it in '0'..'9' || it in 'a'..'f' }) { "Ungültiger Hash" }
        return File(dir, "$sha.lv")
    }

    /** Liest den Strom vollständig, prüft die Größe und speichert verschlüsselt (falls noch nicht vorhanden). */
    @Synchronized
    fun store(input: InputStream): Stored {
        val plain = readLimited(input)
        val sha = sha256Hex(plain)
        val target = file(sha)
        if (target.exists()) return Stored(sha, plain.size.toLong(), alreadyPresent = true)
        dir.mkdirs()
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(sha.toByteArray(Charsets.US_ASCII))
        val sealed = cipher.doFinal(plain)
        plain.fill(0)
        val tmp = File(dir, "$sha.tmp")
        tmp.outputStream().use { out ->
            out.write(byteArrayOf(FORMAT_VERSION))
            out.write(iv)
            out.write(sealed)
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IllegalStateException("Dokument konnte nicht gespeichert werden")
        }
        return Stored(sha, sealed.size.toLong() - TAG_BITS / 8, alreadyPresent = false)
    }

    /** Entschlüsselt eine Datei; prüft Integrität (GCM-Tag) und Hash. */
    fun read(sha: String): ByteArray {
        val blob = file(sha).readBytes()
        if (blob.size < 1 + IV_BYTES + TAG_BITS / 8 || blob[0] != FORMAT_VERSION) throw Corrupt("Unbekanntes Format")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 1, IV_BYTES))
        cipher.updateAAD(sha.toByteArray(Charsets.US_ASCII))
        val plain = try {
            cipher.doFinal(blob, 1 + IV_BYTES, blob.size - 1 - IV_BYTES)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw Corrupt("Datei beschädigt oder falscher Schlüssel")
        }
        if (sha256Hex(plain) != sha) throw Corrupt("Prüfsumme stimmt nicht")
        return plain
    }

    fun exists(sha: String): Boolean = file(sha).exists()

    @Synchronized
    fun delete(sha: String) {
        file(sha).delete()
    }

    /** Alle gespeicherten Hashes (für Aufräumen und Sicherung). */
    fun hashes(): Set<String> =
        dir.listFiles()?.mapNotNull { f -> f.name.takeIf { it.endsWith(".lv") }?.removeSuffix(".lv") }?.toSet().orEmpty()

    /** Entfernt Dateien, auf die kein Datensatz mehr verweist (z. B. nach abgebrochenem Import). */
    @Synchronized
    fun retainOnly(referenced: Set<String>): Int {
        var removed = 0
        hashes().filter { it !in referenced }.forEach { if (file(it).delete()) removed++ }
        dir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
        return removed
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw TooLarge(maxBytes)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 25L * 1024 * 1024
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val FORMAT_VERSION: Byte = 1

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Leitet den Dokumentschlüssel aus dem Datenbankgeheimnis ab (domänengetrennt, 32 Bytes). */
        fun deriveKey(secret: ByteArray, purpose: String = "lifeos.documents.v1"): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(purpose.toByteArray(Charsets.UTF_8))
            md.update(0)
            md.update(secret)
            return md.digest()
        }
    }
}
