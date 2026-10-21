package app.lifeos.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import app.lifeos.feature.planning.NotificationAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Status der Benachrichtigungs-Erlaubnis. Die Anfrage selbst braucht eine Activity – die MainActivity
 * hinterlegt dafür [requester]. Ohne Activity (oder nach endgültiger Ablehnung) öffnen sich die
 * App-Benachrichtigungseinstellungen.
 */
class AndroidNotificationAccess(private val context: Context) : NotificationAccess {
    private val _allowed = MutableStateFlow(ReminderNotifier.canNotify(context))
    override val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    var requester: (() -> Unit)? = null

    override fun request() {
        val r = requester
        if (r != null) r() else openSettings()
    }

    fun refresh() {
        _allowed.value = ReminderNotifier.canNotify(context)
    }

    fun openSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
