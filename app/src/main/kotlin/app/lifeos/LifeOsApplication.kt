package app.lifeos

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LifeOsApplication : Application() {

    private val lock = Mutex()
    private var started: StartResult.Ready? = null

    /** Öffnet Datenbank und Container einmalig im Hintergrund; danach sofort verfügbar. */
    suspend fun start(): StartResult = lock.withLock {
        started?.let { return@withLock it }
        val result = withContext(Dispatchers.IO) {
            val r = AppContainer.create(this@LifeOsApplication)
            if (r is StartResult.Ready) r.container.ensureLedgerDefaults()
            r
        }
        if (result is StartResult.Ready) started = result
        result
    }

    suspend fun wipeAndRestart(): StartResult {
        lock.withLock {
            started?.container?.database?.close()
            started = null
            withContext(Dispatchers.IO) { AppContainer.wipeLocalData(this@LifeOsApplication) }
        }
        return start()
    }
}
