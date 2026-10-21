package app.lifeos.core.security.vault

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passwortgeschützte, geräteunabhängige Sicherungsdatei (reines JVM, ohne Android).
 *
 * Aufbau:
 * ```
 * Kopf:     "LIFEOSBK" | version(1) | iterationen(int) | salt(16) | nonce-präfix(8)
 * Segmente: länge(int) | AES-256-GCM(klartext ≤ 1 MiB) ...
 * ```
 * - Schlüssel: PBKDF2-HMAC-SHA256 aus dem Passwort (Iterationen im Kopf, nach oben begrenzt).
 * - Nonce je Segment = Präfix(8) ‖ Segmentnummer(4) – nie doppelt verwendet.
 * - AAD je Segment = Kopf ‖ Segmentnummer ‖ „letztes Segment"-Flag. Vertauschte, fehlende oder
 *   abgeschnittene Segmente werden daher erkannt (eine Datei ohne Schluss-Segment ist ungültig).
 * - Ein falsches Passwort scheitert am ersten Segment → [WrongPassphrase].
 */
object BackupCipher {
    private val MAGIC = "LIFEOSBK".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    const val DEFAULT_ITERATIONS = 210_000
    private const val MAX_ITERATIONS = 5_000_000
    private const val SALT_BYTES = 16
    private const val PREFIX_BYTES = 8
    const val SEGMENT_BYTES = 1 shl 20
    private const val TAG_BYTES = 16
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    class WrongPassphrase : IOException("Falsches Passwort oder beschädigte Sicherung")
    class Corrupt(message: String) : IOException(message)

    /** Mindestanforderung an das Sicherungspasswort. */
    fun isAcceptablePassphrase(p: CharArray): Boolean = p.size >= 8

    fun encrypt(out: OutputStream, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): OutputStream {
        require(iterations in 1..MAX_ITERATIONS)
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val prefix = ByteArray(PREFIX_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(MAGIC.size + 1 + 4 + SALT_BYTES + PREFIX_BYTES)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(prefix).array()
        out.write(header)
        return SegmentOutput(out, deriveKey(passphrase, salt, iterations), header, prefix)
    }

    fun decrypt(input: InputStream, passphrase: CharArray): InputStream {
        val data = DataInputStream(input)
        val header = ByteArray(MAGIC.size + 1 + 4 + SALT_BYTES + PREFIX_BYTES)
        try {
            data.readFully(header)
        } catch (e: EOFException) {
            throw Corrupt("Keine LifeOS-Sicherung")
        }
        val buf = ByteBuffer.wrap(header)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) throw Corrupt("Keine LifeOS-Sicherung")
        val version = buf.get()
        if (version != VERSION) throw Corrupt("Sicherungsformat $version wird nicht unterstützt")
        val iterations = buf.int
        if (iterations !in 1..MAX_ITERATIONS) throw Corrupt("Ungültiger Sicherungskopf")
        val salt = ByteArray(SALT_BYTES).also { buf.get(it) }
        val prefix = ByteArray(PREFIX_BYTES).also { buf.get(it) }
        return SegmentInput(data, deriveKey(passphrase, salt, iterations), header, prefix)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun nonce(prefix: ByteArray, index: Int) = ByteBuffer.allocate(12).put(prefix).putInt(index).array()
    private fun aad(header: ByteArray, index: Int, last: Boolean) =
        ByteBuffer.allocate(header.size + 5).put(header).putInt(index).put(if (last) 1 else 0).array()

    private class SegmentOutput(
        private val target: OutputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val prefix: ByteArray,
    ) : FilterOutputStream(target) {
        private val pending = ByteArrayOutputStream(SEGMENT_BYTES)
        private var index = 0
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "Bereits geschlossen" }
            var o = off
            var remaining = len
            while (remaining > 0) {
                // Ein volles Segment erst schreiben, wenn weitere Daten folgen – das letzte bleibt für close().
                if (pending.size() == SEGMENT_BYTES) flushSegment(last = false)
                val n = minOf(remaining, SEGMENT_BYTES - pending.size())
                pending.write(b, o, n)
                o += n
                remaining -= n
            }
        }

        private fun flushSegment(last: Boolean) {
            check(index != Int.MAX_VALUE) { "Sicherung zu groß" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce(prefix, index)))
            cipher.updateAAD(aad(header, index, last))
            val sealed = cipher.doFinal(pending.toByteArray())
            target.write(ByteBuffer.allocate(4).putInt(sealed.size).array())
            target.write(sealed)
            pending.reset()
            index++
        }

        override fun flush() = target.flush()

        override fun close() {
            if (closed) return
            flushSegment(last = true)
            closed = true
            target.flush()
            target.close()
        }
    }

    private class SegmentInput(
        private val source: DataInputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val prefix: ByteArray,
    ) : InputStream() {
        private var current = ByteArray(0)
        private var pos = 0
        private var index = 0
        private var finished = false

        private fun nextSegment(): Boolean {
            if (finished) return false
            val len = try {
                source.readInt()
            } catch (e: EOFException) {
                throw Corrupt("Sicherung ist unvollständig (abgeschnitten)")
            }
            if (len < TAG_BYTES || len > SEGMENT_BYTES + TAG_BYTES) throw Corrupt("Ungültige Segmentlänge")
            val sealed = ByteArray(len)
            try {
                source.readFully(sealed)
            } catch (e: EOFException) {
                throw Corrupt("Sicherung ist unvollständig (abgeschnitten)")
            }
            // Ob es das letzte Segment ist, verrät erst das Dateiende – beide Varianten prüfen.
            val plain = open(sealed, last = false) ?: open(sealed, last = true)?.also { finished = true }
                ?: if (index == 0) throw WrongPassphrase() else throw Corrupt("Segment $index beschädigt")
            if (finished && source.read() != -1) throw Corrupt("Daten nach dem Ende der Sicherung")
            index++
            current = plain
            pos = 0
            return true
        }

        private fun open(sealed: ByteArray, last: Boolean): ByteArray? = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce(prefix, index)))
            cipher.updateAAD(aad(header, index, last))
            cipher.doFinal(sealed)
        } catch (e: AEADBadTagException) {
            null
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= current.size) {
                if (!nextSegment()) return -1
            }
            val n = minOf(len, current.size - pos)
            System.arraycopy(current, pos, b, off, n)
            pos += n
            return n
        }

        override fun close() = source.close()
    }
}
