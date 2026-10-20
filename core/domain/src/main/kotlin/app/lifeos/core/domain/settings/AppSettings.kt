package app.lifeos.core.domain.settings

import app.lifeos.core.domain.ports.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Typisierte Sicht auf die Schlüssel/Wert-Einstellungen. */
class AppSettings(private val repository: SettingsRepository) {

    /** `true` = CONTINUUM-Animationen aktiv. Standard: aktiv. */
    val motionEnabled: Flow<Boolean> = repository.observe(KEY_MOTION).map { it != "0" }

    suspend fun setMotionEnabled(enabled: Boolean) {
        repository.put(KEY_MOTION, if (enabled) "1" else "0")
    }

    companion object {
        const val KEY_MOTION = "ui.motion.enabled"
    }
}
