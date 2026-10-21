package app.lifeos.feature.life.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.life.ExamCountdown
import app.lifeos.core.domain.life.ExamLogic
import app.lifeos.core.domain.life.LearningOverview
import app.lifeos.core.domain.life.LifeResult
import app.lifeos.core.domain.life.StudyStatistics
import app.lifeos.core.domain.life.StudyStats
import app.lifeos.core.domain.life.SubjectProgress
import app.lifeos.core.domain.planning.AgendaItem
import app.lifeos.core.domain.planning.TaskGrouping
import app.lifeos.core.model.Exam
import app.lifeos.core.model.ExamPart
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.StudySession
import app.lifeos.core.model.Subject
import app.lifeos.core.model.Topic
import app.lifeos.core.model.TopicStatus
import app.lifeos.feature.life.LifeDependencies
import app.lifeos.feature.life.LifeText
import app.lifeos.feature.life.shared.LifeMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

data class LearningState(
    val subjects: List<SubjectProgress>,
    val topics: List<Topic>,
    val stats: StudyStats,
    val running: StudySession?,
    val recent: List<StudySession>,
    val exams: List<ExamCountdown>,
    val lessons: List<AgendaItem.EventItem>,
    val subjectNames: Map<NodeId, String>,
    val today: LocalDate,
    val now: Instant,
)

data class ManualDraft(
    val subjectId: NodeId?,
    val date: LocalDate,
    val start: LocalTime,
    val minutes: Int = 60,
    val note: String = "",
    val error: String? = null,
)

data class SubjectDraft(val id: NodeId?, val name: String, val part: ExamPart, val newTopic: String = "", val error: String? = null)

data class ExamDraft(
    val exam: Exam,
    val title: String,
    val targetMonth: YearMonth?,
    val targetLabel: String,
    val confirmedDate: LocalDate?,
    val location: String,
    val error: String? = null,
)

class LearningViewModel(private val deps: LifeDependencies) : ViewModel() {
    private val _events = Channel<LifeMessage>(Channel.BUFFERED)
    val events: Flow<LifeMessage> = _events.receiveAsFlow()

    /** Fach, das beim nächsten Start gemessen wird. */
    val timerSubject = MutableStateFlow<NodeId?>(null)
    val manual = MutableStateFlow<ManualDraft?>(null)
    val subjectDraft = MutableStateFlow<SubjectDraft?>(null)
    val examDraft = MutableStateFlow<ExamDraft?>(null)

    /** Minütlicher Takt, damit Statistik einer laufenden Sitzung mitwächst (die Sekundenanzeige zählt die Oberfläche). */
    private val tick = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    private data class Data(val subjects: List<Subject>, val topics: List<Topic>, val sessions: List<StudySession>, val exams: List<Exam>, val events: List<app.lifeos.core.model.CalendarEvent>)

    private val data = combine(deps.learning.observeSubjects(), deps.learning.observeTopics(), deps.learning.observeSessions(), deps.learning.observeExams(), deps.planning.observeEvents(), ::Data)

    val state: StateFlow<LearningState?> = combine(data, deps.links.observeAllLinks(), tick) { d, links, _ ->
        val now = deps.clock.now()
        val today = deps.clock.today()
        val stats = StudyStatistics.compute(d.sessions, now, deps.zone())
        val subjectIds = d.subjects.map { it.id }.toSet()
        val linkedEvents = links.flatMap { listOf(it.from to it.to, it.to to it.from) }.filter { (_, other) -> other in subjectIds }.map { it.first }.toSet()
        val lessonEvents = d.events.filter { e -> e.id in linkedEvents || e.title.contains("Unterricht", ignoreCase = true) || e.title.contains("Meister", ignoreCase = true) }
        LearningState(
            subjects = LearningOverview.progress(d.subjects, d.topics, stats),
            topics = d.topics,
            stats = stats,
            running = d.sessions.firstOrNull { it.running },
            recent = d.sessions.filter { !it.running }.sortedByDescending { it.start }.take(15),
            exams = d.exams.map { ExamLogic.countdown(it, today) }.sortedWith(compareBy({ it.exam.part }, { it.referenceDate })),
            lessons = TaskGrouping.upcomingEvents(lessonEvents, LocalDateTime.now(), 21, 4),
            subjectNames = d.subjects.associate { it.id to it.name },
            today = today,
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(LifeMessage("Speichern fehlgeschlagen. Es wurde nichts verändert.", false))
            }
        }
    }

    // ── Timer ──

    fun selectTimerSubject(id: NodeId?) {
        timerSubject.value = id
    }

    fun start(): Unit = run {
        when (val r = deps.startStudy(timerSubject.value)) {
            is LifeResult.Success -> _events.send(LifeMessage("Lernzeit läuft" + (timerSubject.value?.let { id -> " · " + (state.value?.subjectNames?.get(id) ?: "") } ?: ""), true))
            is LifeResult.Invalid -> _events.send(LifeMessage(LifeText.error(r.errors.first()), false))
        }
    }

    fun stop(): Unit = run {
        when (val r = deps.stopStudy()) {
            is LifeResult.Success -> {
                val s = r.value
                val minutes = java.time.Duration.between(s.start, s.end ?: s.start).toMinutes()
                _events.send(LifeMessage("${LifeFormat.duration(minutes)} Lernzeit erfasst", true))
            }
            is LifeResult.Invalid -> _events.send(LifeMessage(LifeText.error(r.errors.first()), false))
        }
    }

    // ── Manuell nachtragen ──

    fun newManual() {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        manual.value = ManualDraft(timerSubject.value, now.toLocalDate(), now.toLocalTime().minusHours(1))
    }

    fun updateManual(change: (ManualDraft) -> ManualDraft) {
        manual.value = manual.value?.let { change(it).copy(error = null) }
    }

    fun closeManual() {
        manual.value = null
    }

    fun saveManual() {
        val m = manual.value ?: return
        val start = LocalDateTime.of(m.date, m.start).atZone(deps.zone()).toInstant()
        val end = start.plusSeconds(m.minutes * 60L)
        run {
            when (val r = deps.addManualSession(m.subjectId, start, end, m.note)) {
                is LifeResult.Success -> {
                    manual.value = null
                    _events.send(LifeMessage("${LifeFormat.duration(m.minutes.toLong())} nachgetragen", true))
                }
                is LifeResult.Invalid -> manual.value = m.copy(error = LifeText.error(r.errors.first()))
            }
        }
    }

    fun deleteSession(s: StudySession): Unit = run {
        deps.learning.deleteSession(s.id)
        _events.send(LifeMessage("Lerneinheit gelöscht", true, "Rückgängig") { run { deps.learning.saveSession(s) } })
    }

    // ── Fächer & Themen ──

    fun editSubject(s: Subject?) {
        subjectDraft.value = s?.let { SubjectDraft(it.id, it.name, it.part) } ?: SubjectDraft(null, "", ExamPart.BQ)
    }

    fun updateSubject(change: (SubjectDraft) -> SubjectDraft) {
        subjectDraft.value = subjectDraft.value?.let { change(it).copy(error = null) }
    }

    fun closeSubject() {
        subjectDraft.value = null
    }

    fun saveSubject() {
        val d = subjectDraft.value ?: return
        run {
            when (val r = deps.saveSubject(d.name, d.part, d.id)) {
                is LifeResult.Success -> {
                    subjectDraft.value = if (d.id == null) d.copy(id = r.value) else null
                    _events.send(LifeMessage(if (d.id == null) "Fach „${d.name.trim()}“ angelegt – jetzt Themen ergänzen" else "Fach gespeichert", true))
                }
                is LifeResult.Invalid -> subjectDraft.value = d.copy(error = LifeText.error(r.errors.first()))
            }
        }
    }

    fun deleteSubject() {
        val id = subjectDraft.value?.id ?: return
        run {
            deps.learning.deleteSubject(id)
            subjectDraft.value = null
            _events.send(LifeMessage("Fach gelöscht – erfasste Lernzeit bleibt (ohne Fach) erhalten", true))
        }
    }

    fun addTopic() {
        val d = subjectDraft.value ?: return
        val sid = d.id ?: return
        val title = d.newTopic.trim()
        if (title.isEmpty()) return
        run {
            val order = (state.value?.topics?.filter { it.subjectId == sid }?.maxOfOrNull { it.sortOrder } ?: -1) + 1
            deps.learning.saveTopic(Topic(deps.newId(), sid, title.take(120), TopicStatus.OPEN, order), deps.clock.now())
            subjectDraft.value = subjectDraft.value?.copy(newTopic = "")
        }
    }

    fun cycleTopic(t: Topic): Unit = run {
        deps.learning.saveTopic(t.copy(status = LearningOverview.nextStatus(t.status)), deps.clock.now())
    }

    fun deleteTopic(t: Topic): Unit = run {
        deps.learning.deleteTopic(t.id)
        _events.send(LifeMessage("Thema „${t.title}“ entfernt", true))
    }

    // ── Prüfungen ──

    fun seedTargets(): Unit = run {
        val n = deps.seedExamTargets()
        _events.send(LifeMessage(if (n == 0) "Prüfungsteile sind schon angelegt" else "Zielzeiträume eingetragen – als „nicht bestätigt“ markiert", true))
    }

    fun editExam(e: Exam?) {
        val exam = e ?: Exam(deps.newId(), "", ExamPart.OTHER)
        examDraft.value = ExamDraft(exam, exam.title, exam.targetMonth, exam.targetLabel.orEmpty(), exam.confirmedDate, exam.location.orEmpty())
    }

    fun updateExam(change: (ExamDraft) -> ExamDraft) {
        examDraft.value = examDraft.value?.let { change(it).copy(error = null) }
    }

    fun closeExam() {
        examDraft.value = null
    }

    fun saveExam() {
        val d = examDraft.value ?: return
        run {
            val label = d.targetLabel.trim().ifEmpty { null }
            when (val r = deps.saveExam(d.exam.copy(title = d.title, targetMonth = d.targetMonth, targetLabel = if (d.targetMonth == null) null else label, confirmedDate = d.confirmedDate, location = d.location))) {
                is LifeResult.Success -> {
                    examDraft.value = null
                    _events.send(LifeMessage(if (d.confirmedDate != null) "Prüfungstermin gespeichert" else "Zielzeitraum gespeichert", true))
                }
                is LifeResult.Invalid -> examDraft.value = d.copy(error = LifeText.error(r.errors.first()))
            }
        }
    }

    fun deleteExam() {
        val d = examDraft.value ?: return
        run {
            deps.learning.deleteExam(d.exam.id)
            examDraft.value = null
            _events.send(LifeMessage("Prüfung entfernt", true))
        }
    }

}
