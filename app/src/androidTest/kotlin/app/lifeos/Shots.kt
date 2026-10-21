package app.lifeos

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Speichert echte Bildschirmfotos des Emulators nach `Download/lifeos-shots/`. Die CI holt sie per
 * `adb pull` ab und legt sie im Branch `ci-device` ab. Nur in Tests verwendet.
 */
object Shots {
    fun take(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(350) // Rendering der letzten Frames abschließen
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = instrumentation.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/lifeos-shots")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun text(name: String, content: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.txt")
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/lifeos-shots")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
    }
}
