package app.lifeos.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.lifeos.AppContainer
import app.lifeos.LifeOsApplication
import app.lifeos.MainActivity
import app.lifeos.R
import app.lifeos.StartResult
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.planning.MarkReminderFired
import app.lifeos.core.domain.planning.PlannedAlarm
import app.lifeos.core.domain.planning.PlanningRepository
import app.lifeos.core.domain.planning.ReminderPlanner
import app.lifeos.core.domain.planning.ReminderScheduler
import app.lifeos.core.domain.planning.SnoozeReminder
import app.lifeos.core.model.NodeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZoneId

/**
 * Plant genau EINEN Alarm: die nächste fällige Erinnerung. Beim Auslösen zeigt [ReminderReceiver] alle
 * fälligen Benachrichtigungen, vermerkt sie als gezeigt und plant den nächsten Alarm. Dadurch gibt es
 * keine verwaisten Alarme – erledigte, gelöschte oder deaktivierte Einträge fallen automatisch weg.
 *
 * Genauigkeit: exakter Alarm, wenn erlaubt (USE_EXACT_ALARM ab Android 13, SCHEDULE_EXACT_ALARM bis 12);
 * sonst ein ungenauer Alarm im Doze-Fenster. Nach Neustart/Update/Zeitzonenwechsel plant [SystemEventReceiver] neu.
 */
class AlarmReminderScheduler(
    private val context: Context,
    private val repo: PlanningRepository,
    private val clock: LifeClock,
    private val onChanged: () -> Unit = {},
) : ReminderScheduler {

    override suspend fun reschedule() {
        val plan = ReminderPlanner.plan(repo.reminders(), repo.tasks(), repo.events(), clock.now(), ZoneId.systemDefault())
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = firePendingIntent(context)
        val next = plan.firstOrNull()
        if (next == null) {
            am.cancel(pi)
        } else {
            val at = next.triggerAt.toEpochMilli()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
        onChanged()
    }

    companion object {
        fun firePendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Zeigt fällige Erinnerungen als Benachrichtigung und vermerkt sie (keine Doppelungen). */
class ReminderNotifier(
    private val context: Context,
    private val repo: PlanningRepository,
    private val clock: LifeClock,
    private val scheduler: ReminderScheduler,
) {
    private val markFired = MarkReminderFired(repo, scheduler)

    suspend fun fireDue() {
        val now = clock.now()
        val plan = ReminderPlanner.plan(repo.reminders(), repo.tasks(), repo.events(), now, ZoneId.systemDefault())
        val due = plan.filter { !it.triggerAt.isAfter(now.plusSeconds(30)) }
        if (due.isEmpty()) {
            scheduler.reschedule()
            return
        }
        due.forEach { alarm ->
            show(alarm)
            markFired(alarm.reminderId, alarm.firesFor)
        }
    }

    private fun show(alarm: PlannedAlarm) {
        if (!canNotify(context)) return
        val notificationId = alarm.reminderId.value.hashCode()
        val open = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(MainActivity.EXTRA_OPEN, (if (alarm.isTask) "task:" else "event:") + alarm.nodeId.value)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_lifeos)
            .setContentTitle(alarm.title)
            .setContentText(alarm.text)
            .setCategory(if (alarm.isTask) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF8DEAFF.toInt())
            .setAutoCancel(true)
            .setContentIntent(open)
        if (alarm.isTask) {
            builder.addAction(0, "Erledigt", actionIntent(ReminderActionReceiver.ACTION_DONE, alarm, notificationId))
        }
        builder.addAction(0, "In 1 Std erneut", actionIntent(ReminderActionReceiver.ACTION_SNOOZE, alarm, notificationId))
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Berechtigung wurde zwischen Prüfung und Anzeige entzogen – nichts anzeigen, nicht abstürzen.
        }
    }

    private fun actionIntent(action: String, alarm: PlannedAlarm, notificationId: Int): PendingIntent = PendingIntent.getBroadcast(
        context, (action + alarm.reminderId.value).hashCode(),
        Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderActionReceiver.EXTRA_NODE, alarm.nodeId.value)
            .putExtra(ReminderActionReceiver.EXTRA_REMINDER, alarm.reminderId.value)
            .putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION, notificationId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_REMINDERS = "reminders"

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_REMINDERS, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Fällige Aufgaben und anstehende Termine"
            }
            nm.createNotificationChannel(channel)
        }

        fun canNotify(context: Context): Boolean =
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

/** Gemeinsamer Ablauf für alle Empfänger: App-Container im Hintergrund öffnen, Arbeit erledigen, abschließen. */
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

internal fun BroadcastReceiver.withContainer(context: Context, block: suspend (AppContainer) -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            val app = context.applicationContext as LifeOsApplication
            val r = app.start()
            if (r is StartResult.Ready) block(r.container)
        } catch (e: Exception) {
            // Bewusst keine Inhalte loggen (Datenschutz). Nächster Anlass plant erneut.
        } finally {
            pending.finish()
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        withContainer(context) { it.reminderNotifier.fireDue() }
    }

    companion object {
        const val ACTION_FIRE = "app.lifeos.action.REMINDER_FIRE"
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        NotificationManagerCompat.from(context).cancel(notificationId)
        withContainer(context) { c ->
            when (intent.action) {
                ACTION_DONE -> intent.getStringExtra(EXTRA_NODE)?.let { c.planning.completeTask(NodeId(it)) }
                ACTION_SNOOZE -> intent.getStringExtra(EXTRA_REMINDER)?.let {
                    SnoozeReminder(c.planningRepository, c.clock, c.reminderScheduler)(NodeId(it), Duration.ofHours(1))
                }
            }
        }
    }

    companion object {
        const val ACTION_DONE = "app.lifeos.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "app.lifeos.action.REMINDER_SNOOZE"
        const val EXTRA_NODE = "node"
        const val EXTRA_REMINDER = "reminder"
        const val EXTRA_NOTIFICATION = "notification"
    }
}

/** Neustart, App-Update, Uhrzeit-/Zeitzonenwechsel, geänderte Alarm-Berechtigung → neu planen. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        withContainer(context) { it.reminderScheduler.reschedule() }
    }
}
