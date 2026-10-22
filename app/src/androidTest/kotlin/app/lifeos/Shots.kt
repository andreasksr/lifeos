package app.lifeos

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Speichert echte Bildschirmfotos des Emulators nach `Download/lifeos-shots/`. Die CI holt sie per
 * `adb pull` ab und legt sie im Branch `ci-device` ab. Nur in Tests verwendet.
 *
 * Auf dem ATD-Testabbild liefert der System-Screenshot nur Schwarz (kein Hardware-Compositing). Dann
 * werden alle Fenster der App (Activity + Dialoge/Overlays) per Software gezeichnet und übereinandergelegt.
 */
object Shots {
    fun take(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(350) // Rendering der letzten Frames abschließen
        var bitmap: Bitmap? = instrumentation.uiAutomation.takeScreenshot()
        if (bitmap == null || isBlank(bitmap)) bitmap = drawWindows() ?: bitmap
        if (bitmap == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = instrumentation.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/lifeos-shots")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun isBlank(b: Bitmap): Boolean {
        val stepX = (b.width / 24).coerceAtLeast(1)
        val stepY = (b.height / 48).coerceAtLeast(1)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val c = b.getPixel(x, y)
                if (Color.red(c) + Color.green(c) + Color.blue(c) > 12) return false
                x += stepX
            }
            y += stepY
        }
        return true
    }

    /** Alle Fenster dieses Prozesses in Software zeichnen (Reihenfolge = Stapelreihenfolge). */
    @Suppress("UNCHECKED_CAST")
    private fun drawWindows(): Bitmap? {
        var result: Bitmap? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runCatching {
                val wmg = Class.forName("android.view.WindowManagerGlobal")
                val instance = wmg.getMethod("getInstance").invoke(null)
                val views = (wmg.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>)
                    .filter { it.isShown && it.width > 0 && it.height > 0 }
                if (views.isEmpty()) return@runCatching
                val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
                val bmp = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.BLACK)
                val loc = IntArray(2)
                views.forEach { v ->
                    v.getLocationOnScreen(loc)
                    canvas.save()
                    canvas.translate(loc[0].toFloat(), loc[1].toFloat())
                    v.draw(canvas)
                    canvas.restore()
                }
                result = bmp
            }
        }
        return result
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
