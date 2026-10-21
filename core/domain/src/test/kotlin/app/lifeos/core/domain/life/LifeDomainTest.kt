package app.lifeos.core.domain.life

import app.lifeos.core.domain.home.AttentionSeverity
import app.lifeos.core.domain.testing.FakeAssetRepository
import app.lifeos.core.domain.testing.FakeDocumentRepository
import app.lifeos.core.domain.testing.FakeDocumentStore
import app.lifeos.core.domain.testing.FakeLearningRepository
import app.lifeos.core.domain.testing.FakeVehicleRepository
import app.lifeos.core.domain.testing.FixedClock
import app.lifeos.core.domain.testing.SequentialIds
import app.lifeos.core.domain.testing.id
import app.lifeos.core.model.Asset
import app.lifeos.core.model.AssetKind
import app.lifeos.core.model.Exam
import app.lifeos.core.model.ExamPart
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.Money
import app.lifeos.core.model.NodeLink
import app.lifeos.core.model.SessionSource
import app.lifeos.core.model.Split
import app.lifeos.core.model.StudySession
import app.lifeos.core.model.TransactionKind
import app.lifeos.core.model.VehicleEventKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class LifeDomainTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val clock = FixedClock(now, today)
    private val ids = SequentialIds()

    // ── Fahrzeuge ──

    @Test
    fun `fahrzeug validierung und kilometerstand fortschreiben`() = runBlocking {
        val repo = FakeVehicleRepository()
        val save = SaveVehicle(repo, ids, clock)
        assertTrue(save(VehicleDraft(name = "", make = "", model = "")) is LifeResult.Invalid)
        assertTrue(save(VehicleDraft(name = "x", make = "", model = "", buildYear = 1700)) is LifeResult.Invalid)
        val vid = (save(VehicleDraft(name = "", make = "Suzuki", model = "Samurai", buildYear = 1993, plate = "ab-cd 12", odometerKm = 150_000)) as LifeResult.Success).value
        val v = repo.vehicle(vid)!!
        assertEquals("Suzuki Samurai", v.name)
        assertEquals("AB-CD 12", v.plate)

        val event = SaveVehicleEvent(repo, ids, clock)
        event(VehicleEventDraft(vehicleId = vid, kind = VehicleEventKind.MAINTENANCE, title = "", date = today, odometerKm = 151_200))
        assertEquals(151_200, repo.vehicle(vid)!!.odometerKm)
        // Geplantes Ereignis schreibt den Stand nicht fort; niedrigerer Stand auch nicht
        event(VehicleEventDraft(vehicleId = vid, kind = VehicleEventKind.INSPECTION, title = "HU", date = today.plusDays(20), odometerKm = 160_000, done = false))
        event(VehicleEventDraft(vehicleId = vid, kind = VehicleEventKind.REPAIR, title = "Auspuff", date = today, odometerKm = 140_000))
        assertEquals(151_200, repo.vehicle(vid)!!.odometerKm)
        assertEquals("Wartung", repo.events().first { it.kind == VehicleEventKind.MAINTENANCE }.title)
    }

    @Test
    fun `fahrzeugkosten aus fahrzeug und ereignissen ohne doppelzaehlung`() = runBlocking {
        val repo = FakeVehicleRepository()
        val vid = (SaveVehicle(repo, ids, clock)(VehicleDraft(name = "Samurai", make = "Suzuki", model = "Samurai", purchaseDate = LocalDate.of(2024, 5, 1), purchasePriceMinor = 650_000)) as LifeResult.Success).value
        val eid = (SaveVehicleEvent(repo, ids, clock)(VehicleEventDraft(vehicleId = vid, kind = VehicleEventKind.REPAIR, title = "Bremsen", date = today)) as LifeResult.Success).value
        val hu = (SaveVehicleEvent(repo, ids, clock)(VehicleEventDraft(vehicleId = vid, kind = VehicleEventKind.INSPECTION, title = "HU", date = today.plusDays(5), done = false)) as LifeResult.Success).value
        fun tx(n: Int, kind: TransactionKind, amount: Long, date: LocalDate) =
            LedgerTransaction(id(n), kind, id(90), null, Money.eur(amount), date, "T$n", null, listOf(Split(id(n + 100), id(91), amount)))
        val txs = listOf(tx(1, TransactionKind.EXPENSE, 8_990, today), tx(2, TransactionKind.EXPENSE, 12_000, LocalDate.of(2025, 3, 1)), tx(3, TransactionKind.REFUND, 990, today), tx(4, TransactionKind.EXPENSE, 50_000, today))
        val links = listOf(
            NodeLink(id(50), id(1), vid, LinkRelation.CONCERNS),
            NodeLink(id(51), id(1), eid, LinkRelation.CONCERNS), // dieselbe Buchung zusätzlich am Ereignis
            NodeLink(id(52), id(2), vid, LinkRelation.CONCERNS),
            NodeLink(id(53), id(3), eid, LinkRelation.CONCERNS),
        )
        val snap = VehicleSnapshotBuilder.build(repo.vehicle(vid)!!, repo.events(), links, txs, today)
        assertEquals(8_990L + 12_000L - 990L, snap.runningCostsMinor)
        assertEquals(8_990L - 990L, snap.costsThisYearMinor)
        assertEquals(3, snap.transactions.size)
        assertEquals(listOf(hu), snap.upcoming.map { it.id })
        assertTrue(snap.line.last() is VehicleLineItem.Purchase)

        val attention = VehicleSnapshotBuilder.attention(listOf(repo.vehicle(vid)!!), repo.events(), today)
        assertEquals(1, attention.size)
        assertEquals(AttentionSeverity.WARN, attention.single().severity)
    }

    // ── Inventar ──

    @Test
    fun `garantiestatus und hinweis`() = runBlocking {
        val repo = FakeAssetRepository()
        val save = SaveAsset(repo, ids, clock)
        assertTrue(save(AssetDraft(name = " ", kind = AssetKind.TOOL)) is LifeResult.Invalid)
        assertTrue(save(AssetDraft(name = "Bohrhammer", kind = AssetKind.TOOL, purchaseDate = today, warrantyUntil = today.minusDays(1))) is LifeResult.Invalid)
        val a = Asset(id(1), "Bohrhammer", AssetKind.TOOL, warrantyUntil = today.plusDays(20))
        assertEquals(WarrantyState.EXPIRING, AssetLogic.warranty(a, today))
        assertEquals(WarrantyState.ACTIVE, AssetLogic.warranty(a.copy(warrantyUntil = today.plusDays(400)), today))
        assertEquals(WarrantyState.EXPIRED, AssetLogic.warranty(a.copy(warrantyUntil = today.minusDays(1)), today))
        assertEquals(WarrantyState.NONE, AssetLogic.warranty(a.copy(warrantyUntil = null), today))
        assertEquals("Garantie endet in 20 Tagen (10.11.2026)", AssetLogic.attention(listOf(a), today).single().detail)
    }

    // ── Dokumente ──

    @Test
    fun `gleiche datei wird nicht doppelt gespeichert und erst zuletzt geloescht`() = runBlocking {
        val repo = FakeDocumentRepository()
        val store = FakeDocumentStore()
        val import = ImportDocument(repo, store, ids, clock)
        val first = import("PDF-Inhalt".byteInputStream(), "Rechnung Bremsen.pdf", "application/pdf")
        val again = import("PDF-Inhalt".byteInputStream(), "Kopie.pdf", "application/pdf")
        assertFalse(first.duplicate)
        assertTrue(again.duplicate)
        assertEquals(first.documentId, again.documentId)
        assertEquals(1, repo.docs.value.size)
        assertEquals(1, store.files.size)
        assertEquals("Rechnung Bremsen", repo.docs.value.single().title)
        assertNull(repo.docs.value.single().ocrText)

        DeleteDocument(repo, store)(first.documentId)
        assertTrue(repo.docs.value.isEmpty())
        assertTrue(store.files.isEmpty())
    }

    // ── Lernzeit ──

    @Test
    fun `nur eine laufende sitzung und kappung nach 12 stunden`() = runBlocking {
        val repo = FakeLearningRepository()
        val c = FixedClock(now, today)
        val first = StartStudy(repo, ids, c)(id(7))
        assertTrue(first is LifeResult.Success)
        assertEquals(LifeResult.Invalid(listOf(LifeError.SessionRunning)), StartStudy(repo, ids, c)(id(8)))
        c.instant = now.plusSeconds(20 * 3600)
        val stopped = (StopStudy(repo, c)() as LifeResult.Success).value
        assertEquals(now.plusSeconds(12 * 3600), stopped.end)
        assertEquals(LifeResult.Invalid(listOf(LifeError.NoRunningSession)), StopStudy(repo, c)())
    }

    @Test
    fun `manuelle zeit ohne ueberschneidung`() = runBlocking {
        val repo = FakeLearningRepository()
        val add = AddManualSession(repo, ids, clock)
        val s = now.minusSeconds(4 * 3600)
        assertTrue(add(id(7), s, s.plusSeconds(3600)) is LifeResult.Success)
        assertEquals(LifeResult.Invalid(listOf(LifeError.SessionOverlaps)), add(id(7), s.plusSeconds(1800), s.plusSeconds(5400)))
        assertTrue(add(id(7), s.plusSeconds(3600), s.plusSeconds(5400)) is LifeResult.Success) // direkt anschließend erlaubt
        assertTrue(add(id(7), s, s) is LifeResult.Invalid)
        assertTrue(add(id(7), now.plusSeconds(3600), now.plusSeconds(7200)) is LifeResult.Invalid)
    }

    @Test
    fun `statistik zaehlt ueberschneidungen nur einmal`() {
        val zone = ZoneOffset.UTC
        val mon = LocalDate.of(2026, 10, 19).atStartOfDay(zone).toInstant()
        val sessions = listOf(
            StudySession(id(1), id(10), mon.plusSeconds(18 * 3600), mon.plusSeconds(20 * 3600), SessionSource.MANUAL),
            // überlappt die erste um 1 h (z. B. aus einem Import) – darf nicht doppelt zählen
            StudySession(id(2), id(11), mon.plusSeconds(19 * 3600), mon.plusSeconds(21 * 3600), SessionSource.MANUAL),
            StudySession(id(3), id(10), Instant.parse("2026-10-01T10:00:00Z"), Instant.parse("2026-10-01T10:45:00Z"), SessionSource.TIMER),
            StudySession(id(4), id(10), Instant.parse("2026-09-30T10:00:00Z"), Instant.parse("2026-09-30T11:00:00Z"), SessionSource.TIMER),
            StudySession(id(5), null, now.minusSeconds(600), null, SessionSource.TIMER), // läuft
        )
        val st = StudyStatistics.compute(sessions, now, zone)
        assertEquals(180L + 10L, st.weekMinutes)
        assertEquals(180L + 45L + 10L, st.monthMinutes)
        assertEquals(180L + 45L + 60L + 10L, st.totalMinutes)
        assertEquals(120L + 45L + 60L, st.bySubjectMinutes[id(10)])
        assertEquals(listOf(180L, 0L, 10L, 0L, 0L, 0L, 0L), st.weekDays)
    }

    // ── Prüfungen ──

    @Test
    fun `zielzeitraum ist nie ein bestaetigter termin`() = runBlocking {
        val repo = FakeLearningRepository()
        assertEquals(2, SeedExamTargets(repo, ids, clock)())
        assertEquals(0, SeedExamTargets(repo, ids, clock)())
        val bq = repo.exams().first { it.part == ExamPart.BQ }
        val cd = ExamLogic.countdown(bq, today)
        assertFalse(cd.confirmed)
        assertEquals(YearMonth.of(2027, 4), bq.targetMonth)
        assertEquals(LocalDate.of(2027, 4, 30), cd.referenceDate)
        assertEquals("Zielzeitraum Ende April 2027 · noch nicht bestätigt", cd.label)
        assertTrue(ExamLogic.attention(repo.exams(), today).isEmpty())

        val confirmed = bq.copy(confirmedDate = LocalDate.of(2026, 11, 5))
        val cd2 = ExamLogic.countdown(confirmed, today)
        assertTrue(cd2.confirmed)
        assertEquals(15L, cd2.days)
        assertEquals(1, ExamLogic.attention(listOf(confirmed), today).size)
        assertEquals("Prüfung am 05.11.2026", cd2.label)
        assertTrue(SaveExam(repo, clock)(Exam(id(99), " ", ExamPart.OTHER)) is LifeResult.Invalid)
    }
}
