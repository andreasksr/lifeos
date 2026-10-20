package app.lifeos.core.domain.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

class SplitAllocatorTest {

    @Test
    fun `gleichmaessig mit rest`() {
        assertEquals(listOf(334L, 333L, 333L), SplitAllocator.even(1000, 3))
        assertEquals(listOf(1L, 1L, 0L), SplitAllocator.even(2, 3))
        assertEquals(listOf(0L, 0L), SplitAllocator.even(0, 2))
    }

    @Test
    fun `proportional mit groesstem rest`() {
        assertEquals(listOf(2500L, 7500L), SplitAllocator.proportional(10000, listOf(1, 3)))
        assertEquals(listOf(0L, 100L), SplitAllocator.proportional(100, listOf(0, 7)))
    }

    @Test
    fun `summe ist immer exakt - zufallstest`() {
        val rnd = Random(42)
        repeat(2_000) {
            val total = rnd.nextLong(0, 10_000_000_000)
            val weights = List(rnd.nextInt(1, 12)) { rnd.nextLong(0, 1_000_000) }.let {
                if (it.sum() == 0L) it.mapIndexed { i, w -> if (i == 0) 1 else w } else it
            }
            val parts = SplitAllocator.proportional(total, weights)
            assertEquals(total, parts.sum())
            parts.forEach { org.junit.Assert.assertTrue(it >= 0) }
        }
    }

    @Test
    fun `ungueltige eingaben`() {
        assertThrows(IllegalArgumentException::class.java) { SplitAllocator.even(100, 0) }
        assertThrows(IllegalArgumentException::class.java) { SplitAllocator.proportional(100, listOf(0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { SplitAllocator.proportional(-1, listOf(1)) }
    }
}
