package app.lifeos.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.lifeos.LifeOsApplication
import app.lifeos.MainActivity
import app.lifeos.StartResult
import app.lifeos.core.domain.planning.AgendaItem
import app.lifeos.core.domain.planning.TaskGrouping
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.Task
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

/** Was das Widget zeigt – ausschließlich echte Daten. */
private sealed interface WidgetData {
    data class Ready(val today: LocalDate, val tasks: List<Task>, val overdue: Int, val next: AgendaItem.EventItem?) : WidgetData
    data object Locked : WidgetData
    data object Unavailable : WidgetData
}

private val Obsidian = ColorProvider(Color(0xFF080C14))
private val Frost = ColorProvider(Color(0xFFF0F5FF))
private val Muted = ColorProvider(Color(0xFF91A5BF))
private val Cyan = ColorProvider(Color(0xFF8DEAFF))
private val Violet = ColorProvider(Color(0xFFB29AFF))
private val Alert = ColorProvider(Color(0xFFFF7D9C))

/**
 * Startbildschirm-Widget: nächster Termin + heute fällige/überfällige Aufgaben, Schnellzugriff auf
 * LifeOS und die Ausgabe-Erfassung. Bei aktiver App-Sperre werden keine Inhalte gezeigt.
 */
class LifeOsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { Content(context, data) }
    }

    private suspend fun load(context: Context): WidgetData {
        val app = context.applicationContext as LifeOsApplication
        val c = (runCatching { app.start() }.getOrNull() as? StartResult.Ready)?.container ?: return WidgetData.Unavailable
        if (c.settings.appLockEnabled.first()) return WidgetData.Locked
        val today = c.clock.today()
        val groups = TaskGrouping.group(c.planningRepository.tasks(), today)
        val next = TaskGrouping.upcomingEvents(c.planningRepository.events(), LocalDateTime.now(), 14, 1).firstOrNull()
        return WidgetData.Ready(today, (groups.overdue + groups.today).take(4), groups.overdue.size, next)
    }

    companion object {
        fun openIntent(context: Context, target: String?): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("lifeos://open/" + (target ?: "home")))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { if (target != null) putExtra(MainActivity.EXTRA_OPEN, target) }

        /** Nach Änderungen an Aufgaben/Terminen aufrufen. */
        suspend fun refresh(context: Context) {
            runCatching { LifeOsWidget().updateAll(context) }
        }
    }
}

class LifeOsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeOsWidget()
}

@Composable
private fun Content(context: Context, data: WidgetData) {
    Column(
        GlanceModifier.fillMaxSize().background(Obsidian).cornerRadius(6.dp).padding(14.dp)
            .clickable(actionStartActivity(LifeOsWidget.openIntent(context, null))),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(8.dp).background(Cyan).cornerRadius(4.dp)) {}
            Spacer(GlanceModifier.width(8.dp))
            Text("LIFEOS", style = TextStyle(color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
            if (data is WidgetData.Ready) {
                Text(LifeFormat.weekday(data.today.dayOfWeek) + " " + LifeFormat.numericDate(data.today).take(6), style = TextStyle(color = Muted, fontSize = 11.sp))
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    "+ Ausgabe", style = TextStyle(color = Frost, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.clickable(actionStartActivity(LifeOsWidget.openIntent(context, "capture:expense"))),
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Violet)) {}
        Spacer(GlanceModifier.height(8.dp))
        when (data) {
            WidgetData.Locked -> Text("Gesperrt – Inhalte ausgeblendet. Tippen zum Entsperren.", style = TextStyle(color = Muted, fontSize = 13.sp))
            WidgetData.Unavailable -> Text("LifeOS öffnen", style = TextStyle(color = Muted, fontSize = 13.sp))
            is WidgetData.Ready -> {
                val e = data.next
                if (e != null) {
                    Text(
                        (if (e.event.allDay) LifeFormat.dayTitle(e.occurrenceStart.toLocalDate(), data.today) else LifeFormat.dayTitle(e.occurrenceStart.toLocalDate(), data.today) + " · " + LifeFormat.time(e.occurrenceStart.toLocalTime())),
                        style = TextStyle(color = Cyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    )
                    Text(
                        e.event.title, maxLines = 1, style = TextStyle(color = Frost, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier.clickable(actionStartActivity(LifeOsWidget.openIntent(context, "event:" + e.event.id.value))),
                    )
                    Spacer(GlanceModifier.height(8.dp))
                }
                if (data.tasks.isEmpty()) {
                    Text(if (e == null) "Keine Termine und keine fälligen Aufgaben." else "Keine fälligen Aufgaben.", style = TextStyle(color = Muted, fontSize = 13.sp))
                } else {
                    data.tasks.forEach { t ->
                        val overdue = t.dueDate?.isBefore(data.today) == true
                        Row(
                            GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(actionStartActivity(LifeOsWidget.openIntent(context, "task:" + t.id.value))),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(GlanceModifier.size(7.dp).background(if (overdue) Alert else Frost).cornerRadius(4.dp)) {}
                            Spacer(GlanceModifier.width(8.dp))
                            Text(t.title, maxLines = 1, style = TextStyle(color = Frost, fontSize = 13.sp))
                        }
                    }
                    if (data.overdue > 0) Text("${data.overdue} überfällig", style = TextStyle(color = Alert, fontSize = 11.sp))
                }
            }
        }
    }
}
