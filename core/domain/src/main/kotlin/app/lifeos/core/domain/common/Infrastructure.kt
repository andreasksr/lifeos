package app.lifeos.core.domain.common

import app.lifeos.core.model.NodeId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Zeitquelle – in Tests ersetzbar. */
interface LifeClock {
    fun now(): Instant
    fun today(): LocalDate

    companion object {
        val System: LifeClock = object : LifeClock {
            override fun now(): Instant = Instant.now()
            override fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())
        }
    }
}

/** ID-Quelle – in Tests deterministisch ersetzbar. */
fun interface IdGenerator {
    fun newId(): NodeId

    companion object {
        val Random: IdGenerator = IdGenerator { NodeId.random() }
    }
}
