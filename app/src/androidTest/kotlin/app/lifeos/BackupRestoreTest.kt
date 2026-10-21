package app.lifeos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lifeos.backup.BackupManager
import app.lifeos.core.database.LifeOsDatabase
import app.lifeos.core.security.vault.BackupCipher
import app.lifeos.core.security.vault.FileVault
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Auftrag Teil 15: „Eine Datensicherung lässt sich tatsächlich wiederherstellen."
 * Gerät A (Schlüssel A) erstellt eine Sicherung; „Gerät B" mit völlig anderem Datenbank- und
 * Dokumentschlüssel spielt sie ein und liest Buchung, Fahrzeug, Verknüpfung und Dokument wieder.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun id(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

    @Test
    fun sicherungAufAnderemGeraetWiederherstellen() {
        val keyA = ByteArray(32) { (it + 1).toByte() }
        val keyB = ByteArray(32) { (100 - it).toByte() }
        val srcName = "backup-src-test.db"
        val dstName = "backup-dst-test.db"
        ctx.deleteDatabase(srcName)
        ctx.deleteDatabase(dstName)
        val work = File(ctx.cacheDir, "backup-test").apply { deleteRecursively(); mkdirs() }
        val vaultA = FileVault(File(work, "vaultA"), FileVault.deriveKey(keyA))
        val vaultBDir = File(work, "vaultB")

        // ── Gerät A: Daten anlegen ──
        val receipt = "Rechnung Bremsscheiben 89,90 EUR".toByteArray()
        val sha = vaultA.store(ByteArrayInputStream(receipt)).sha256
        val src = LifeOsDatabase.open(ctx, keyA, srcName)
        val w = src.openHelper.writableDatabase
        w.execSQL("INSERT INTO node VALUES ('${id(1)}', 'ACCOUNT', 'Haushaltskasse', 0, 0, NULL, 0)")
        w.execSQL("INSERT INTO account VALUES ('${id(1)}', 'CHECKING', 'EUR', 1000, 0)")
        w.execSQL("INSERT INTO node VALUES ('${id(2)}', 'CATEGORY', 'Fahrzeug', 0, 0, NULL, 0)")
        w.execSQL("INSERT INTO category VALUES ('${id(2)}', NULL, 'EXPENSE', 0)")
        w.execSQL("INSERT INTO node VALUES ('${id(3)}', 'TRANSACTION', 'Bremsscheiben', 0, 0, NULL, 0)")
        w.execSQL("INSERT INTO ledger_transaction VALUES ('${id(3)}', 'EXPENSE', '${id(1)}', NULL, 8990, 'EUR', '2026-10-18', 'Teilehandel', NULL)")
        w.execSQL("INSERT INTO transaction_split VALUES ('${id(4)}', '${id(3)}', '${id(2)}', 8990, NULL, 0)")
        w.execSQL("INSERT INTO node VALUES ('${id(5)}', 'VEHICLE', 'Suzuki Samurai', 0, 0, NULL, 0)")
        w.execSQL("INSERT INTO vehicle VALUES ('${id(5)}', 'Suzuki', 'Samurai', 1993, NULL, NULL, 150000, NULL, NULL, NULL)")
        w.execSQL("INSERT INTO link VALUES ('${id(6)}', '${id(3)}', '${id(5)}', 'CONCERNS', 0)")
        w.execSQL("INSERT INTO node VALUES ('${id(7)}', 'DOCUMENT', 'Rechnung', 0, 0, NULL, 0)")
        w.execSQL("INSERT INTO document VALUES ('${id(7)}', 'rechnung.pdf', 'application/pdf', ${receipt.size}, '$sha', 0, NULL, NULL, NULL)")

        val manager = BackupManager(File(work, "work"))
        val sealed = ByteArrayOutputStream()
        val summary = manager.export(w, LifeOsDatabase.VERSION, vaultA, sealed, "korrektes-pferd".toCharArray(), "test")
        src.close()
        assertEquals(1, summary.documents)
        val bytes = sealed.toByteArray()
        // Die Sicherung enthält keinen Klartext
        assertTrue(!String(bytes, Charsets.ISO_8859_1).contains("Teilehandel"))
        assertTrue(!String(bytes, Charsets.ISO_8859_1).contains("Bremsscheiben"))

        // ── Falsches Passwort: Abbruch ohne Änderung ──
        try {
            manager.stage(ByteArrayInputStream(bytes), "falsches-pferd".toCharArray(), LifeOsDatabase.VERSION)
            fail("Falsches Passwort wurde akzeptiert")
        } catch (e: BackupCipher.WrongPassphrase) {
        }
        assertTrue(!ctx.getDatabasePath(dstName).exists())

        // ── Gerät B: anderer Schlüssel, Wiederherstellung ──
        val staged = manager.stage(ByteArrayInputStream(bytes), "korrektes-pferd".toCharArray(), LifeOsDatabase.VERSION)
        val dstFile = ctx.getDatabasePath(dstName).also { it.parentFile?.mkdirs() }
        manager.install(staged, dstFile, keyB, vaultBDir, FileVault.deriveKey(keyB))

        val dst = LifeOsDatabase.open(ctx, keyB, dstName)
        val r = dst.openHelper.readableDatabase
        r.query("SELECT amount_minor, payee FROM ledger_transaction").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(8990L, c.getLong(0))
            assertEquals("Teilehandel", c.getString(1))
        }
        r.query("SELECT n.title FROM link l JOIN node n ON n.id = l.to_node_id WHERE l.from_node_id = '${id(3)}'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Suzuki Samurai", c.getString(0))
        }
        r.query("SELECT sha256 FROM document").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(sha, c.getString(0))
        }
        dst.close()
        // Dokument mit dem Schlüssel von Gerät B lesbar – mit Schlüssel A nicht
        assertArrayEquals(receipt, FileVault(vaultBDir, FileVault.deriveKey(keyB)).read(sha))
        try {
            FileVault(vaultBDir, FileVault.deriveKey(keyA)).read(sha)
            fail("Dokument mit fremdem Schlüssel lesbar")
        } catch (e: FileVault.Corrupt) {
        }
        ctx.deleteDatabase(srcName)
        ctx.deleteDatabase(dstName)
        work.deleteRecursively()
    }
}
