package app.lifeos.core.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Status: geschrieben, NICHT ausgeführt (benötigt Gerät/Emulator mit Android Keystore). */
@RunWith(AndroidJUnit4::class)
class DatabaseKeyManagerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun passphraseIsStableAcrossInstances() {
        File(context.noBackupFilesDir, "lifeos.db.key").delete()
        val alias = "test.kek.${System.nanoTime()}"
        val first = DatabaseKeyManager(context, alias).obtainPassphrase(databaseExists = false)
        val second = DatabaseKeyManager(context, alias).obtainPassphrase(databaseExists = true)
        assertTrue(first is DatabaseKeyManager.KeyState.Ready)
        assertEquals(32, (first as DatabaseKeyManager.KeyState.Ready).passphrase.size)
        assertArrayEquals(first.passphrase, (second as DatabaseKeyManager.KeyState.Ready).passphrase)
    }

    @Test
    fun missingKeyWithExistingDatabaseIsReportedNotSilentlyReplaced() {
        File(context.noBackupFilesDir, "lifeos.db.key").delete()
        val state = DatabaseKeyManager(context, "test.kek.missing").obtainPassphrase(databaseExists = true)
        assertTrue(state is DatabaseKeyManager.KeyState.Unrecoverable)
    }
}
