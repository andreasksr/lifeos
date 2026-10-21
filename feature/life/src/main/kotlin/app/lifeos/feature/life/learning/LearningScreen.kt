package app.lifeos.feature.life.learning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.core.designsystem.component.ChartSeries
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeBarChart
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeDatePickerOverlay
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeLink
import app.lifeos.core.designsystem.component.LifeMetric
import app.lifeos.core.designsystem.component.LifeMonthStepper
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeOverlay
import app.lifeos.core.designsystem.component.LifePanel
import app.lifeos.core.designsystem.component.LifeSegmented
import app.lifeos.core.designsystem.component.LifeStepper
import app.lifeos.core.designsystem.component.LifeTag
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTimePickerOverlay
import app.lifeos.core.designsystem.component.LifeTimelineRow
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.component.MetricSize
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.PanelEmphasis
import app.lifeos.core.designsystem.component.SpineRow
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.domain.life.ExamCountdown
import app.lifeos.core.domain.life.ExamLogic
import app.lifeos.core.model.ExamPart
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.TopicStatus
import app.lifeos.feature.life.LifeText
import app.lifeos.feature.life.shared.CollectLifeMessages
import app.lifeos.feature.life.shared.SpineInset
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class LearningNavigation(val back: () -> Unit, val newLesson: () -> Unit, val openEvent: (NodeId) -> Unit)

private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearningRoute(vm: LearningViewModel, nav: LearningNavigation) {
    val state by vm.state.collectAsStateWithLifecycle()
    val timerSubject by vm.timerSubject.collectAsStateWithLifecycle()
    CollectLifeMessages(vm.events)
    val s = state ?: return

    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 150.dp)) {
        item(key = "top") { LifeTopBar(overline = "Weiterbildung", title = "Industriemeister", subtitle = "Mechatronik · Lernzeit, Fächer, Prüfungen", onBack = nav.back) }

        // ── Lernzeit (Start/Stopp) ──
        item(key = "timer") {
            SpineSection("Lernzeit", shape = NodeShape.RING, tone = if (s.running != null) NodeTone.ACTIVE else NodeTone.NEUTRAL) {
                LifePanel(emphasis = PanelEmphasis.RAISED) {
                    val running = s.running
                    if (running != null) {
                        RunningClock(running.start)
                        Text(running.subjectId?.let { s.subjectNames[it] } ?: "Ohne Fach", style = Continuum.type.bodyMuted)
                        LifeLine(meaning = LineMeaning.TIME, pulse = true)
                        LifeButton("Stoppen", vm::stop, style = LifeButtonStyle.PRIMARY, glyph = Glyph.STOP, fill = true)
                    } else {
                        Text("Fach wählen und starten – die Zeit läuft auch, wenn du die App schließt.", style = Continuum.type.small)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LifeChoiceChip("Ohne Fach", timerSubject == null, { vm.selectTimerSubject(null) })
                            s.subjects.forEach { p -> LifeChoiceChip(p.subject.name, timerSubject == p.subject.id, { vm.selectTimerSubject(p.subject.id) }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            LifeButton("Starten", vm::start, glyph = Glyph.PLAY)
                            LifeLink("Zeit nachtragen", vm::newManual)
                        }
                    }
                }
            }
        }

        // ── Statistik ──
        item(key = "stats") {
            SpineSection("Statistik", shape = NodeShape.DIAMOND, tone = NodeTone.ACTIVE) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LifeMetric("Diese Woche", LifeFormat.duration(s.stats.weekMinutes), Modifier.weight(1f), size = MetricSize.REGULAR, tone = NodeTone.ACTIVE)
                    LifeMetric(LifeFormat.monthName(YearMonth.from(s.today)), LifeFormat.duration(s.stats.monthMinutes), Modifier.weight(1f))
                    LifeMetric("Gesamt", LifeFormat.duration(s.stats.totalMinutes), Modifier.weight(1f))
                }
                if (s.stats.weekDays.any { it > 0 }) {
                    LifeBarChart(
                        labels = WEEKDAYS,
                        series = listOf(ChartSeries("Minuten", s.stats.weekDays, ContinuumColors.Cyan)),
                        description = "Lernminuten dieser Woche: " + WEEKDAYS.zip(s.stats.weekDays).joinToString { (d, m) -> "$d ${LifeFormat.duration(m)}" },
                    )
                } else {
                    Text("Diese Woche noch keine Lernzeit erfasst.", style = Continuum.type.small)
                }
                Text("Überschneidende Zeiten werden nur einmal gezählt.", style = Continuum.type.small)
            }
        }

        // ── Prüfungen ──
        item(key = "exams") {
            SpineSection("Prüfungen", shape = NodeShape.SQUARE, tone = NodeTone.WARN, fullBleed = true, trailing = { LifeLink("Neue Prüfung", { vm.editExam(null) }) }) {
                if (s.exams.isEmpty()) {
                    Column(SpineInset, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Noch keine Prüfungen eingetragen. Deine bisher genannten Zielzeiträume: BQ Ende April 2027, HQ Mai 2028 – sie werden als „nicht bestätigt“ angelegt und sind jederzeit änderbar.", style = Continuum.type.bodyMuted)
                        LifeButton("Zielzeiträume eintragen", vm::seedTargets, style = LifeButtonStyle.SECONDARY, glyph = Glyph.CALENDAR)
                    }
                }
            }
        }
        items(s.exams, key = { "x-" + it.exam.id.value }) { c -> ExamRow(c) { vm.editExam(c.exam) } }

        // ── Unterricht ──
        item(key = "lessons") {
            SpineSection("Unterricht", shape = NodeShape.RING, fullBleed = true, trailing = { LifeLink("Planen", nav.newLesson) }) {
                if (s.lessons.isEmpty()) {
                    LifeEmpty("Keine Unterrichtstermine in den nächsten 3 Wochen. Termine aus der Planung erscheinen hier, wenn ihr Titel „Unterricht“ oder „Meister“ enthält.", SpineInset, "Unterricht planen", nav.newLesson)
                }
            }
        }
        items(s.lessons, key = { "l-" + it.event.id.value + it.occurrenceStart }) { l ->
            LifeTimelineRow(nodeShape = NodeShape.RING, nodeTone = NodeTone.ACTIVE, onClick = { nav.openEvent(l.event.id) }, onClickLabel = "Termin öffnen") {
                Column(Modifier.weight(1f)) {
                    Text(l.event.title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(LifeFormat.dayTitle(l.occurrenceStart.toLocalDate(), s.today) + if (l.event.allDay) "" else " · " + LifeFormat.time(l.occurrenceStart.toLocalTime()) + "–" + LifeFormat.time(l.occurrenceEnd.toLocalTime()), style = Continuum.type.small)
                }
            }
        }

        // ── Fächer ──
        item(key = "subjects") {
            SpineSection("Fächer & Themen", shape = NodeShape.HEX, fullBleed = true, trailing = { LifeLink("Neues Fach", { vm.editSubject(null) }) }) {
                if (s.subjects.isEmpty()) LifeEmpty("Lege deine Fächer an (z. B. Technische Kommunikation, Automatisierungstechnik) und teile sie in Themen auf.", SpineInset, "Fach anlegen", { vm.editSubject(null) })
            }
        }
        items(s.subjects, key = { "s-" + it.subject.id.value }) { p ->
            LifeTimelineRow(nodeShape = NodeShape.HEX, nodeTone = if (p.permille == 1000) NodeTone.DONE else NodeTone.NEUTRAL, onClick = { vm.editSubject(p.subject) }, onClickLabel = "${p.subject.name} öffnen") {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.subject.name, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        LifeTag(LifeText.part(p.subject.part))
                    }
                    Text(
                        (if (p.topicsTotal == 0) "Keine Themen" else "${p.topicsDone}/${p.topicsTotal} Themen sicher") + " · " + LifeFormat.duration(p.minutes),
                        style = Continuum.type.small,
                    )
                    p.permille?.let { LifeLine(progress = it / 1000f, meaning = if (it == 1000) LineMeaning.DONE else LineMeaning.PROGRESS, progressDescription = "${it / 10} Prozent der Themen sicher") }
                }
            }
        }

        // ── Letzte Einheiten ──
        if (s.recent.isNotEmpty()) {
            item(key = "recent") { SpineSection("Letzte Lerneinheiten", shape = NodeShape.DOT, fullBleed = true, trailing = { LifeLink("Nachtragen", vm::newManual) }) {} }
            items(s.recent, key = { "r-" + it.id.value }) { r ->
                val minutes = Duration.between(r.start, r.end ?: r.start).toMinutes()
                val start = r.start.atZone(ZoneId.systemDefault())
                SpineRow(Modifier.heightIn(min = 52.dp), node = { LifeNode(shape = NodeShape.DOT, tone = NodeTone.DONE, size = 10.dp) }) {
                    Column(Modifier.weight(1f)) {
                        Text(r.subjectId?.let { s.subjectNames[it] } ?: "Ohne Fach", style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(LifeFormat.dayTitle(start.toLocalDate(), s.today) + " · " + LifeFormat.time(start.toLocalTime()) + (if (r.source == app.lifeos.core.model.SessionSource.MANUAL) " · nachgetragen" else ""), style = Continuum.type.small)
                    }
                    Text(LifeFormat.duration(minutes), style = Continuum.type.figures)
                    LifeIconButton(Glyph.TRASH, "Lerneinheit löschen", { vm.deleteSession(r) }, tint = ContinuumColors.Faint)
                }
            }
        }
    }

    ManualOverlay(vm, s.subjects.map { it.subject.id to it.subject.name }, s.today)
    SubjectOverlay(vm, s.topics)
    ExamOverlay(vm, s.today)
}

/** Zählt sekündlich hoch – rein in der Oberfläche; gespeichert wird nur Start und Ende. */
@Composable
private fun RunningClock(start: Instant) {
    var seconds by remember(start) { mutableLongStateOf(Duration.between(start, Instant.now()).seconds) }
    LaunchedEffect(start) {
        while (true) {
            seconds = Duration.between(start, Instant.now()).seconds
            delay(1_000)
        }
    }
    val minutesText = LifeFormat.duration(seconds / 60)
    Text(
        LifeText.clock(seconds), style = Continuum.type.figuresHuge,
        modifier = Modifier.semantics {
            contentDescription = "Lernzeit läuft seit $minutesText"
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
private fun ExamRow(c: ExamCountdown, onEdit: () -> Unit) {
    val days = c.days
    SpineRow(
        modifier = Modifier.heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = "Prüfung bearbeiten", onClick = onEdit),
        node = { LifeNode(shape = NodeShape.SQUARE, tone = if (c.confirmed) NodeTone.WARN else NodeTone.NEUTRAL, size = 14.dp, active = c.confirmed) },
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.exam.title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                LifeTag(LifeText.part(c.exam.part))
            }
            Text(c.label, style = Continuum.type.small, color = if (c.confirmed) ContinuumColors.Frost else ContinuumColors.Muted)
        }
        if (days != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(if (days >= 0) "$days" else "–", style = Continuum.type.figuresMedium)
                Text(if (c.confirmed) "Tage" else "Tage (ca.)", style = Continuum.type.small)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualOverlay(vm: LearningViewModel, subjects: List<Pair<NodeId, String>>, today: java.time.LocalDate) {
    val draft by vm.manual.collectAsStateWithLifecycle()
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val m = draft
    LifeOverlay(visible = m != null, onDismiss = vm::closeManual, title = "Lernzeit nachtragen", overline = "Weiterbildung") {
        if (m == null) return@LifeOverlay
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeChoiceChip("Ohne Fach", m.subjectId == null, { vm.updateManual { it.copy(subjectId = null) } })
                subjects.forEach { (id, name) -> LifeChoiceChip(name, m.subjectId == id, { vm.updateManual { it.copy(subjectId = id) } }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeChoiceChip(LifeFormat.dayTitle(m.date, today), true, { pickDate = true }, glyph = Glyph.CALENDAR)
                LifeChoiceChip("ab " + LifeFormat.time(m.start), true, { pickTime = true }, glyph = Glyph.CLOCK)
            }
            LifeStepper(m.minutes, { n -> vm.updateManual { it.copy(minutes = n) } }, 5..720, "Dauer", format = { LifeFormat.duration(it.toLong()) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 45, 90, 180).forEach { n -> LifeChoiceChip(LifeFormat.duration(n.toLong()), m.minutes == n, { vm.updateManual { it.copy(minutes = n) } }) }
            }
            LifeTextField(m.note, { t -> vm.updateManual { it.copy(note = t.take(500)) } }, "Notiz (optional)", placeholder = "z. B. Kapitel 4 Steuerungstechnik", error = m.error)
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            LifeButton("Nachtragen", vm::saveManual, glyph = Glyph.CHECK)
        }
        LifeDatePickerOverlay(pickDate, m.date, today, { pickDate = false }, { d -> vm.updateManual { it.copy(date = d) } }, "Lerntag")
        LifeTimePickerOverlay(pickTime, m.start, { pickTime = false }, { t -> vm.updateManual { it.copy(start = t) } }, "Beginn")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectOverlay(vm: LearningViewModel, topics: List<app.lifeos.core.model.Topic>) {
    val draft by vm.subjectDraft.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val d = draft
    LifeOverlay(visible = d != null, onDismiss = { confirmDelete = false; vm.closeSubject() }, title = if (d?.id == null) "Neues Fach" else d.name.ifBlank { "Fach" }, overline = "Fach", fullHeight = d?.id != null) {
        if (d == null) return@LifeOverlay
        LifeTextField(d.name, { t -> vm.updateSubject { it.copy(name = t.take(120)) } }, "Name", placeholder = "z. B. Automatisierungstechnik", error = d.error, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        LifeSegmented(listOf(ExamPart.BQ to "BQ", ExamPart.HQ to "HQ", ExamPart.OTHER to "Sonstiges"), d.part, { p -> vm.updateSubject { it.copy(part = p) } })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (d.id != null) LifeButton(if (confirmDelete) "Wirklich löschen" else "Löschen", { if (confirmDelete) vm.deleteSubject() else confirmDelete = true }, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
            Spacer(Modifier.weight(1f))
            LifeButton(if (d.id == null) "Anlegen" else "Speichern", vm::saveSubject, glyph = Glyph.CHECK, style = if (d.id == null) LifeButtonStyle.PRIMARY else LifeButtonStyle.SECONDARY)
        }
        if (d.id != null) {
            val own = topics.filter { it.subjectId == d.id }.sortedBy { it.sortOrder }
            Text("THEMEN · antippen: offen → in Arbeit → sicher", style = Continuum.type.label)
            LazyColumn(Modifier.weight(1f)) {
                if (own.isEmpty()) item { LifeEmpty("Noch keine Themen.") }
                items(own, key = { it.id.value }) { t ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LifeNode(
                            shape = NodeShape.RING,
                            tone = when (t.status) {
                                TopicStatus.OPEN -> NodeTone.NEUTRAL
                                TopicStatus.IN_PROGRESS -> NodeTone.ACTIVE
                                TopicStatus.DONE -> NodeTone.DONE
                            },
                            size = 16.dp, onClick = { vm.cycleTopic(t) }, onClickLabel = "Status von „${t.title}“ ändern, aktuell ${LifeText.topicStatus(t.status)}",
                        )
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = Continuum.type.body.copy(textDecoration = if (t.status == TopicStatus.DONE) TextDecoration.LineThrough else null), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(LifeText.topicStatus(t.status), style = Continuum.type.small)
                        }
                        LifeIconButton(Glyph.CLOSE, "Thema „${t.title}“ entfernen", { vm.deleteTopic(t) }, tint = ContinuumColors.Faint)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeTextField(d.newTopic, { t -> vm.updateSubject { it.copy(newTopic = t.take(120)) } }, "", Modifier.weight(1f), placeholder = "Neues Thema", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                LifeIconButton(Glyph.PLUS, "Thema hinzufügen", vm::addTopic, highlighted = true, enabled = d.newTopic.isNotBlank())
            }
        }
    }
}

@Composable
private fun ExamOverlay(vm: LearningViewModel, today: java.time.LocalDate) {
    val draft by vm.examDraft.collectAsStateWithLifecycle()
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val d = draft
    LifeOverlay(visible = d != null, onDismiss = vm::closeExam, title = d?.title?.ifBlank { "Prüfung" } ?: "Prüfung", overline = d?.let { ExamLogic.partLabel(it.exam.part) }) {
        if (d == null) return@LifeOverlay
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeTextField(d.title, { t -> vm.updateExam { it.copy(title = t.take(120)) } }, "Bezeichnung", error = d.error)
            Text("BESTÄTIGTER TERMIN", style = Continuum.type.label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LifeChoiceChip(d.confirmedDate?.let { "Am " + LifeFormat.numericDate(it) } ?: "Noch nicht bestätigt", d.confirmedDate != null, { pickDate = true }, glyph = Glyph.CALENDAR, accent = ContinuumColors.Amber)
                if (d.confirmedDate != null) LifeIconButton(Glyph.CLOSE, "Termin entfernen", { vm.updateExam { it.copy(confirmedDate = null) } })
            }
            if (d.confirmedDate == null) {
                Text("ZIELZEITRAUM (ORIENTIERUNG)", style = Continuum.type.label)
                val month = d.targetMonth
                if (month == null) {
                    LifeLink("Zielmonat festlegen", { vm.updateExam { it.copy(targetMonth = YearMonth.from(today).plusMonths(6)) } })
                } else {
                    LifeMonthStepper(month, { vm.updateExam { it.copy(targetMonth = month.minusMonths(1), targetLabel = "") } }, { vm.updateExam { it.copy(targetMonth = month.plusMonths(1), targetLabel = "") } })
                    LifeTextField(d.targetLabel, { t -> vm.updateExam { it.copy(targetLabel = t.take(40)) } }, "Bezeichnung des Zeitraums", placeholder = LifeFormat.monthTitle(month))
                    LifeLink("Zielzeitraum entfernen", { vm.updateExam { it.copy(targetMonth = null, targetLabel = "") } })
                }
                Text("Ein Zielzeitraum wird nie als fester Termin angezeigt – erst ein bestätigtes Datum startet den exakten Countdown.", style = Continuum.type.small)
            }
            LifeTextField(d.location, { t -> vm.updateExam { it.copy(location = t.take(120)) } }, "Ort (optional)", placeholder = "z. B. IHK")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeButton("Entfernen", vm::deleteExam, style = LifeButtonStyle.DANGER, glyph = Glyph.TRASH)
            Spacer(Modifier.weight(1f))
            LifeButton("Speichern", vm::saveExam, glyph = Glyph.CHECK)
        }
        LifeDatePickerOverlay(pickDate, d.confirmedDate ?: d.targetMonth?.atEndOfMonth() ?: today, today, { pickDate = false }, { date -> vm.updateExam { it.copy(confirmedDate = date) } }, "Prüfungstermin")
    }
}
