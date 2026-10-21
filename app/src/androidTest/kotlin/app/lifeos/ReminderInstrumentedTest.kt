package app.lifeos

import android.Manifest
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.lifeos.core.domain.planning.PlanningResult
import app.lifeos.core.domain.planning.ReminderPlanner
import app.lifeos.core.domain.planning.TaskDraft
import app.lifeos.core.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Echte Erinnerung auf dem Emulator: Benachrichtigung wird gezeigt, als gezeigt vermerkt (keine Doppelung)
 * und nach dem Erledigen der Aufgabe nicht mehr geplant (Auftrag Teil 15).
 */
@RunWith(AndroidJUnit4::class)
class ReminderInstrumentedTest {

    @get:Rule
    val notifications: GrantPermissionRule = if (android.os.Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @Test
    fun erinnerungWirdGezeigtUndNachErledigenDeaktiviert() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LifeOsApplication
        val c = (app.start() as StartResult.Ready).container
        val now = LocalDateTime.now(ZoneId.systemDefault()).withSecond(0).withNano(0)

        // Fällig in 2 Minuten, Erinnerung 5 Minuten vorher → sofort fällig
        val id = (c.planning.saveTask(
            TaskDraft(title = "Erinnerungstest ${System.nanoTime()}", dueDate = now.toLocalDate(), dueTime = now.toLocalTime().plusMinutes(2).takeIf { it.isAfter(now.toLocalTime()) }),
        ) as PlanningResult.Success).value
        c.planning.setReminder(id, 5)

        c.reminderNotifier.fireDue()

        val reminder = c.planningRepository.reminders().first { it.nodeId == id }
        assertNotNull("Erinnerung muss als gezeigt vermerkt sein", reminder.lastFiredFor)
        val nm = app.getSystemService(NotificationManager::class.java)
        assertTrue("Benachrichtigung muss sichtbar sein", nm.activeNotifications.any { it.id == reminder.id.value.hashCode() })

        // Zweites Auslösen erzeugt keine neue Benachrichtigung für denselben Termin
        val before = ReminderPlanner.plan(c.planningRepository.reminders(), c.planningRepository.tasks(), c.planningRepository.events(), c.clock.now(), ZoneId.systemDefault())
        assertTrue(before.none { it.reminderId == reminder.id })

        // Erledigen → keine Erinnerung mehr geplant
        val done = (c.planning.completeTask(id) as PlanningResult.Success).value
        assertEquals(TaskStatus.DONE, done.status)
        val after = ReminderPlanner.plan(c.planningRepository.reminders(), c.planningRepository.tasks(), c.planningRepository.events(), c.clock.now(), ZoneId.systemDefault())
        assertTrue(after.none { it.nodeId == id })
        nm.cancel(reminder.id.value.hashCode())
    }
}
