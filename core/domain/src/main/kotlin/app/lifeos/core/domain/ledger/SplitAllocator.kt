package app.lifeos.core.domain.ledger

/**
 * Verteilt einen Betrag cent-genau auf mehrere Teile (Verfahren der größten Reste).
 * Die Summe der Teile ist immer exakt gleich dem Gesamtbetrag.
 */
object SplitAllocator {

    /** Gleichmäßig: 10,00 € auf 3 → 3,34 / 3,33 / 3,33. */
    fun even(totalMinor: Long, parts: Int): List<Long> {
        require(parts > 0) { "parts muss > 0 sein" }
        return proportional(totalMinor, List(parts) { 1L })
    }

    /**
     * Proportional zu [weights] (alle ≥ 0, Summe > 0). Reste werden an die Teile mit dem größten
     * Nachkommarest vergeben, bei Gleichstand an den früheren Index – deterministisch.
     */
    fun proportional(totalMinor: Long, weights: List<Long>): List<Long> {
        require(totalMinor >= 0) { "totalMinor muss ≥ 0 sein" }
        require(weights.isNotEmpty() && weights.all { it >= 0 }) { "Gewichte müssen ≥ 0 sein" }
        val weightSum = weights.fold(0L) { a, w -> Math.addExact(a, w) }
        require(weightSum > 0) { "Summe der Gewichte muss > 0 sein" }

        val total = totalMinor.toBigInteger()
        val sum = weightSum.toBigInteger()
        val base = LongArray(weights.size)
        val remainders = ArrayList<Pair<Int, java.math.BigInteger>>(weights.size)
        weights.forEachIndexed { i, w ->
            val (q, r) = total.multiply(w.toBigInteger()).divideAndRemainder(sum)
            base[i] = q.toLong()
            remainders += i to r
        }
        var leftover = totalMinor - base.sum()
        remainders.sortedWith(compareByDescending<Pair<Int, java.math.BigInteger>> { it.second }.thenBy { it.first })
            .forEach { (i, _) ->
                if (leftover > 0) {
                    base[i] += 1
                    leftover--
                }
            }
        check(leftover == 0L)
        return base.toList()
    }
}
