package app.lifeos.core.domain.life

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.home.AttentionItem
import app.lifeos.core.domain.home.AttentionSeverity
import app.lifeos.core.domain.home.AttentionTarget
import app.lifeos.core.model.Exam
import app.lifeos.core.model.ExamPart
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.SessionSource
import app.lifeos.core.model.StudySession
import app.lifeos.core.model.Subject
import app.lifeos.core.model.Topic
import app.lifeos.core.model.TopicStatus
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

interface LearningRepository {
    fun observeSubjects(): Flow<List<Subject>>
    fun observeTopics(): Flow<List<Topic>>
    fun observeSessions(): Flow<List<StudySession>>
    fun observeExams(): Flow<List<Exam>>
    suspend fun subjects(): List<Subject>
    suspend fun sessions(): List<StudySession>
    suspend fun exams(): List<Exam>
    suspend fun saveSubject(subject: Subject, now: Instant)
    suspend fun deleteSubject(id: NodeId)
    suspend fun saveTopic(topic: Topic, now: Instant)
    suspend fun deleteTopic(id: NodeId)
    suspend fun saveSession(session: StudySession)
    suspend fun deleteSession(id: NodeId)
    suspend fun saveExam(exam: Exam, now: Instant)
    suspend fun deleteExam(id: NodeId)
}

// ───────────────────────────── Lernzeit ─────────────────────────────

/**
 * Start/Stopp-Erfassung. Es kann immer nur EINE Sitzung laufen – ein zweiter Start wird abgewiesen
 * (zusätzlich erzwingt ein Datenbank-Trigger diese Regel). So entsteht keine doppelt gezählte Zeit.
 */
class StartStudy(private val repo: LearningRepository, private val ids: IdGenerator, private val clock: LifeClock) {
    suspend operator fun invoke(subjectId: NodeId?): LifeResult<StudySession> {
        if (repo.sessions().any { it.running }) return LifeResult.Invalid(listOf(LifeError.SessionRunning))
        val s = StudySession(ids.newId(), subjectId, clock.now().truncatedTo(ChronoUnit.SECONDS), null, SessionSource.TIMER)
        repo.saveSession(s)
        return LifeResult.Success(s)
    }
}

class StopStudy(private val repo: LearningRepository, private val clock: LifeClock) {
    /** Beendet die laufende Sitzung. Länger als 12 Stunden → wird bei 12 Stunden gekappt (vergessener Timer). */
    suspend operator fun invoke(note: String? = null): LifeResult<StudySession> {
        val running = repo.sessions().firstOrNull { it.running } ?: return LifeResult.Invalid(listOf(LifeError.NoRunningSession))
        val now = clock.now().truncatedTo(ChronoUnit.SECONDS)
        val end = minOf(now, running.start.plus(MAX_SESSION))
        val s = running.copy(end = maxOf(end, running.start), note = note?.trim()?.takeIf { it.isNotEmpty() } ?: running.note)
        repo.saveSession(s)
        return LifeResult.Success(s)
    }

    companion object {
        val MAX_SESSION: Duration = Duration.ofHours(12)
    }
}

/** Nachträglich erfasste Lernzeit. Überschneidungen mit vorhandenen Sitzungen werden abgewiesen. */
class AddManualSession(private val repo: LearningRepository, private val ids: IdGenerator, private val clock: LifeClock) {
    suspend operator fun invoke(subjectId: NodeId?, start: Instant, end: Instant, note: String? = null, id: NodeId? = null): LifeResult<NodeId> {
        val errors = mutableListOf<LifeError>()
        if (!end.isAfter(start)) errors += LifeError.EndBeforeStart
        if (Duration.between(start, end) > StopStudy.MAX_SESSION) errors += LifeError.SessionTooLong
        if (end.isAfter(clock.now().plusSeconds(60))) errors += LifeError.InFuture
        val overlaps = repo.sessions().filter { it.id != id }.any { other ->
            val otherEnd = other.end ?: clock.now()
            start.isBefore(otherEnd) && other.start.isBefore(end)
        }
        if (overlaps) errors += LifeError.SessionOverlaps
        if (errors.isNotEmpty()) return LifeResult.Invalid(errors)
        val s = StudySession(id ?: ids.newId(), subjectId, start, end, SessionSource.MANUAL, note?.trim()?.takeIf { it.isNotEmpty() })
        repo.saveSession(s)
        return LifeResult.Success(s.id)
    }
}

data class StudyStats(
    val totalMinutes: Long,
    val weekMinutes: Long,
    val monthMinutes: Long,
    val bySubjectMinutes: Map<NodeId?, Long>,
    /** Lernminuten je Tag der aktuellen Woche (Mo..So). */
    val weekDays: List<Long>,
)

object StudyStatistics {

    /**
     * Summiert Lernzeit. Zeiträume werden vorher vereinigt – selbst falls (z. B. durch einen Import) zwei
     * Sitzungen überlappen, wird jede Minute nur einmal gezählt. Laufende Sitzungen zählen bis [now].
     */
    fun compute(sessions: List<StudySession>, now: Instant, zone: ZoneId): StudyStats {
        val today = now.atZone(zone).toLocalDate()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val monthStart = today.withDayOfMonth(1)
        val intervals = sessions.map { Triple(it.start, it.end ?: now, it.subjectId) }.filter { it.second.isAfter(it.first) }

        fun minutes(from: Instant, to: Instant, filter: (NodeId?) -> Boolean = { true }): Long {
            val clipped = intervals.filter { filter(it.third) }
                .map { maxOf(it.first, from) to minOf(it.second, to) }
                .filter { it.second.isAfter(it.first) }
                .sortedBy { it.first }
            var total = 0L
            var curStart: Instant? = null
            var curEnd: Instant? = null
            for ((s, e) in clipped) {
                if (curEnd == null || s.isAfter(curEnd)) {
                    if (curStart != null) total += Duration.between(curStart, curEnd).seconds
                    curStart = s
                    curEnd = e
                } else if (e.isAfter(curEnd)) {
                    curEnd = e
                }
            }
            if (curStart != null) total += Duration.between(curStart, curEnd).seconds
            return total / 60
        }

        val epoch = Instant.EPOCH
        val far = now.plusSeconds(1)
        val subjects = sessions.map { it.subjectId }.distinct()
        return StudyStats(
            totalMinutes = minutes(epoch, far),
            weekMinutes = minutes(weekStart.atStartOfDay(zone).toInstant(), far),
            monthMinutes = minutes(monthStart.atStartOfDay(zone).toInstant(), far),
            bySubjectMinutes = subjects.associateWith { sid -> minutes(epoch, far) { it == sid } },
            weekDays = (0L..6L).map { d ->
                val day = weekStart.plusDays(d)
                minutes(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant())
            },
        )
    }
}

// ───────────────────────────── Prüfungen ─────────────────────────────

data class ExamCountdown(
    val exam: Exam,
    /** Bestätigter Termin oder – bei Zielzeitraum – das Monatsende als Orientierung. */
    val referenceDate: LocalDate?,
    val days: Long?,
    val confirmed: Boolean,
    val label: String,
)

object ExamLogic {
    fun countdown(exam: Exam, today: LocalDate): ExamCountdown {
        val confirmed = exam.confirmedDate
        if (confirmed != null) {
            val d = ChronoUnit.DAYS.between(today, confirmed)
            return ExamCountdown(exam, confirmed, d, true, "Prüfung am ${app.lifeos.core.model.LifeFormat.numericDate(confirmed)}")
        }
        val month = exam.targetMonth
        val ref = month?.atEndOfMonth()
        val d = ref?.let { ChronoUnit.DAYS.between(today, it) }
        val label = "Zielzeitraum " + (exam.targetLabel ?: month?.let { app.lifeos.core.model.LifeFormat.monthTitle(it) } ?: "offen") + " · noch nicht bestätigt"
        return ExamCountdown(exam, ref, d, false, label)
    }

    /** Die Zielzeiträume aus dem Auftrag – werden nur auf ausdrücklichen Wunsch angelegt, nie als bestätigt. */
    val DEFAULT_TARGETS: List<Triple<ExamPart, String, Pair<YearMonth, String>>> = listOf(
        Triple(ExamPart.BQ, "Basisqualifikationen", YearMonth.of(2027, 4) to "Ende April 2027"),
        Triple(ExamPart.HQ, "Handlungsspezifische Qualifikationen", YearMonth.of(2028, 5) to "Mai 2028"),
    )

    fun attention(exams: List<Exam>, today: LocalDate): List<AttentionItem> = exams.mapNotNull { e ->
        val date = e.confirmedDate ?: return@mapNotNull null
        val days = ChronoUnit.DAYS.between(today, date)
        if (days in 0..30) {
            AttentionItem(e.title, "Prüfung in $days Tagen", if (days <= 7) AttentionSeverity.WARN else AttentionSeverity.INFO, AttentionTarget.Node(e.id), days)
        } else {
            null
        }
    }

    fun partLabel(p: ExamPart): String = when (p) {
        ExamPart.BQ -> "Basisqualifikationen"
        ExamPart.HQ -> "Handlungsspezifische Qualifikationen"
        ExamPart.OTHER -> "Sonstiges"
    }
}

class SeedExamTargets(private val repo: LearningRepository, private val ids: IdGenerator, private val clock: LifeClock) {
    /** Legt nur fehlende Teile an; vorhandene Prüfungen bleiben unberührt. */
    suspend operator fun invoke(): Int {
        val existing = repo.exams().map { it.part }.toSet()
        var created = 0
        ExamLogic.DEFAULT_TARGETS.filter { it.first !in existing }.forEach { (part, title, target) ->
            repo.saveExam(Exam(ids.newId(), title, part, target.first, target.second), clock.now())
            created++
        }
        return created
    }
}

class SaveExam(private val repo: LearningRepository, private val clock: LifeClock) {
    suspend operator fun invoke(exam: Exam): LifeResult<NodeId> {
        if (exam.title.isBlank()) return LifeResult.Invalid(listOf(LifeError.NameRequired))
        repo.saveExam(exam.copy(title = exam.title.trim(), location = exam.location?.trim()?.takeIf { it.isNotEmpty() }), clock.now())
        return LifeResult.Success(exam.id)
    }
}

// ───────────────────────────── Fächer & Themen ─────────────────────────────

class SaveSubject(private val repo: LearningRepository, private val ids: IdGenerator, private val clock: LifeClock) {
    suspend operator fun invoke(name: String, part: ExamPart, id: NodeId? = null, note: String? = null): LifeResult<NodeId> {
        val n = name.trim()
        if (n.isEmpty()) return LifeResult.Invalid(listOf(LifeError.NameRequired))
        if (n.length > 120) return LifeResult.Invalid(listOf(LifeError.NameTooLong))
        val all = repo.subjects()
        val existing = id?.let { sid -> all.firstOrNull { it.id == sid } }
        if (id != null && existing == null) return LifeResult.Invalid(listOf(LifeError.NotFound))
        val s = Subject(id ?: ids.newId(), n, part, existing?.sortOrder ?: ((all.maxOfOrNull { it.sortOrder } ?: -1) + 1), note?.trim()?.takeIf { it.isNotEmpty() })
        repo.saveSubject(s, clock.now())
        return LifeResult.Success(s.id)
    }
}

data class SubjectProgress(val subject: Subject, val topicsDone: Int, val topicsTotal: Int, val minutes: Long) {
    /** Anteil erledigter Themen in Promille; `null` ohne Themen (keine erfundene Zahl). */
    val permille: Int? get() = if (topicsTotal == 0) null else topicsDone * 1000 / topicsTotal
}

object LearningOverview {
    fun progress(subjects: List<Subject>, topics: List<Topic>, stats: StudyStats): List<SubjectProgress> = subjects.sortedBy { it.sortOrder }.map { s ->
        val own = topics.filter { it.subjectId == s.id }
        SubjectProgress(s, own.count { it.status == TopicStatus.DONE }, own.size, stats.bySubjectMinutes[s.id] ?: 0)
    }

    fun nextStatus(s: TopicStatus): TopicStatus = when (s) {
        TopicStatus.OPEN -> TopicStatus.IN_PROGRESS
        TopicStatus.IN_PROGRESS -> TopicStatus.DONE
        TopicStatus.DONE -> TopicStatus.OPEN
    }
}
