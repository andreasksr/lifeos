package app.lifeos.core.security.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

class VaultCryptoTest {
    private val pw = "korrekt-pferd-batterie".toCharArray()

    private fun seal(plain: ByteArray, iterations: Int = 1000): ByteArray {
        val bytes = ByteArrayOutputStream()
        BackupCipher.encrypt(bytes, pw, iterations).use { it.write(plain) }
        return bytes.toByteArray()
    }

    private fun open(sealed: ByteArray, password: CharArray = pw): ByteArray =
        BackupCipher.decrypt(ByteArrayInputStream(sealed), password).use { it.readBytes() }

    @Test
    fun `sicherung rundreise fuer leere, kleine und segmentgrenzen-groessen`() {
        for (size in listOf(0, 1, 1000, BackupCipher.SEGMENT_BYTES - 1, BackupCipher.SEGMENT_BYTES, BackupCipher.SEGMENT_BYTES + 1, 3 * BackupCipher.SEGMENT_BYTES + 17)) {
            val plain = Random(size).nextBytes(size)
            assertArrayEquals("Größe $size", plain, open(seal(plain)))
        }
    }

    @Test
    fun `falsches passwort wird erkannt`() {
        val sealed = seal("geheim".toByteArray())
        try {
            open(sealed, "falsch-falsch".toCharArray())
            fail()
        } catch (e: BackupCipher.WrongPassphrase) {
        }
    }

    @Test
    fun `abgeschnittene, veraenderte oder verlaengerte sicherung wird abgewiesen`() {
        val plain = Random(1).nextBytes(2 * BackupCipher.SEGMENT_BYTES + 5)
        val sealed = seal(plain)
        // Abgeschnitten genau hinter dem ersten Segment (Kopf 37 Bytes + Länge 4 + Segment)
        val firstEnd = 37 + 4 + BackupCipher.SEGMENT_BYTES + 16
        for (cut in listOf(firstEnd, sealed.size - 1, 40)) {
            try {
                open(sealed.copyOf(cut))
                fail("cut=$cut")
            } catch (e: BackupCipher.Corrupt) {
            }
        }
        val flipped = sealed.copyOf().also { it[firstEnd + 100] = (it[firstEnd + 100].toInt() xor 1).toByte() }
        try {
            open(flipped)
            fail()
        } catch (e: BackupCipher.Corrupt) {
        }
        try {
            open(sealed + byteArrayOf(0))
            fail()
        } catch (e: BackupCipher.Corrupt) {
        }
        try {
            open("KEINBACKUP-------------------------------------".toByteArray())
            fail()
        } catch (e: BackupCipher.Corrupt) {
        }
    }

    @Test
    fun `tresor speichert inhaltsadressiert, verschluesselt und ohne duplikat`() {
        val dir = Files.createTempDirectory("vault").toFile()
        try {
            val key = FileVault.deriveKey(ByteArray(32) { it.toByte() })
            val vault = FileVault(dir, key, maxBytes = 1000)
            val content = "Rechnung Bremsscheiben 89,90 €".toByteArray()
            val a = vault.store(ByteArrayInputStream(content))
            val b = vault.store(ByteArrayInputStream(content))
            assertFalse(a.alreadyPresent)
            assertTrue(b.alreadyPresent)
            assertEquals(a.sha256, b.sha256)
            assertEquals(content.size.toLong(), a.sizeBytes)
            assertEquals(1, dir.listFiles()!!.size)
            // Datei auf der Platte enthält den Klartext nicht
            val raw = File(dir, a.sha256 + ".lv").readBytes()
            assertFalse(String(raw, Charsets.ISO_8859_1).contains("Bremsscheiben"))
            assertArrayEquals(content, vault.read(a.sha256))
            // Anderer Schlüssel kann nicht lesen
            try {
                FileVault(dir, FileVault.deriveKey(ByteArray(32))).read(a.sha256)
                fail()
            } catch (e: FileVault.Corrupt) {
            }
            // Größengrenze
            try {
                vault.store(ByteArrayInputStream(ByteArray(1001)))
                fail()
            } catch (e: FileVault.TooLarge) {
            }
            assertEquals(0, vault.retainOnly(setOf(a.sha256)))
            assertEquals(1, vault.retainOnly(emptySet()))
            assertTrue(vault.hashes().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
