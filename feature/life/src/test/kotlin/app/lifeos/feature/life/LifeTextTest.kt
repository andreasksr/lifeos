package app.lifeos.feature.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeTextTest {
    @Test
    fun formate() {
        assertEquals("0:00:00", LifeText.clock(0))
        assertEquals("1:02:03", LifeText.clock(3723))
        assertEquals("150.000 km", LifeText.km(150_000))
        assertEquals("999 km", LifeText.km(999))
        assertEquals("1,5 MB", LifeText.fileSize(1_572_864))
        assertEquals("840 KB", LifeText.fileSize(860_160))
        assertEquals("12 B", LifeText.fileSize(12))
        assertEquals(150_000, LifeText.parseInt("150.000").getOrNull())
        assertEquals(null, LifeText.parseInt(" ").getOrNull())
        assertTrue(LifeText.parseInt("12a").isFailure)
    }
}
