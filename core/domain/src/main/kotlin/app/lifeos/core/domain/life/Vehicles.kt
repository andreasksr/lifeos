package app.lifeos.core.domain.life

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.home.AttentionItem
import app.lifeos.core.domain.home.AttentionSeverity
import app.lifeos.core.domain.home.AttentionTarget
import app.lifeos.core.model.LedgerTransaction
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeLink
import app.lifeos.core.model.TransactionKind
import app.lifeos.core.model.Vehicle
import app.lifeos.core.model.VehicleEvent
import app.lifeos.core.model.VehicleEventKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

interface VehicleRepository {
    fun observeVehicles(): Flow<List<Vehicle>>
    fun observeEvents(): Flow<List<VehicleEvent>>
    suspend fun vehicle(id: NodeId): Vehicle?
    suspend fun events(): List<VehicleEvent>
    suspend fun saveVehicle(vehicle: Vehicle, now: Instant)
    suspend fun deleteVehicle(id: NodeId)
    suspend fun saveEvent(event: VehicleEvent, now: Instant)
    suspend fun deleteEvent(id: NodeId)
}

sealed interface LifeError {
    data object NameRequired : LifeError
    data object NameTooLong : LifeError
    data object InvalidYear : LifeError
    data object InvalidOdometer : LifeError
    data object InvalidAmount : LifeError
    data object NotFound : LifeError
    data object EndBeforeStart : LifeError
    data object SessionTooLong : LifeError
    data object SessionOverlaps : LifeError
    data object SessionRunning : LifeError
    data object NoRunningSession : LifeError
    data object InFuture : LifeError
    data object InvalidDate : LifeError
}

sealed interface LifeResult<out T> {
    data class Success<T>(val value: T) : LifeResult<T>
    data class Invalid(val errors: List<LifeError>) : LifeResult<Nothing>
}

data class VehicleDraft(
    val id: NodeId? = null,
    val name: String,
    val make: String,
    val model: String,
    val buildYear: Int? = null,
    val plate: String? = null,
    val vin: String? = null,
    val odometerKm: Int? = null,
    val purchaseDate: LocalDate? = null,
    val purchasePriceMinor: Long? = null,
    val note: String? = null,
)

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

class SaveVehicle(private val repo: VehicleRepository, private val ids: IdGenerator, private val clock: LifeClock) {
    suspend operator fun invoke(d: VehicleDraft): LifeResult<NodeId> {
        val errors = mutableListOf<LifeError>()
        val name = d.name.trim().ifEmpty { listOf(d.make.trim(), d.model.trim()).filter { it.isNotEmpty() }.joinToString(" ") }
        if (name.isEmpty()) errors += LifeError.NameRequired
        if (name.length > 120) errors += LifeError.NameTooLong
        if (d.buildYear != null && d.buildYear !in 1886..(clock.today().year + 1)) errors += LifeError.InvalidYear
        if (d.odometerKm != null && d.odometerKm !in 0..5_000_000) errors += LifeError.InvalidOdometer
        if (d.purchasePriceMinor != null && d.purchasePriceMinor < 0) errors += LifeError.InvalidAmount
        val existing = d.id?.let { repo.vehicle(it) }
        if (d.id != null && existing == null) errors += LifeError.NotFound
        if (errors.isNotEmpty()) return LifeResult.Invalid(errors)
        val v = Vehicle(
            id = d.id ?: ids.newId(),
            name = name,
            make = d.make.trim(),
            model = d.model.trim(),
            buildYear = d.buildYear,
            plate = d.plate.clean()?.uppercase(),
            vin = d.vin.clean()?.uppercase(),
            odometerKm = d.odometerKm,
            purchaseDate = d.purchaseDate,
            purchasePriceMinor = d.purchasePriceMinor,
            note = d.note.clean(),
            isSample = existing?.isSample ?: false,
        )
        repo.saveVehicle(v, clock.now())
        return LifeResult.Success(v.id)
    }
}

data class VehicleEventDraft(
    val id: NodeId? = null,
    val vehicleId: NodeId,
    val kind: VehicleEventKind,
    val title: String,
    val date: LocalDate,
    val odometerKm: Int? = null,
    val done: Boolean = true,
    val note: String? = null,
)

/**
 * Speichert ein Ereignis. Ein erledigtes Ereignis mit höherem Kilometerstand schreibt den Stand des
 * Fahrzeugs fort – so bleibt der Kilometerstand ohne doppelte Pflege aktuell.
 */
class SaveVehicleEvent(private val repo: VehicleRepository, private val ids: IdGenerator, private val clock: LifeClock) {
    suspend operator fun invoke(d: VehicleEventDraft): LifeResult<NodeId> {
        val errors = mutableListOf<LifeError>()
        val title = d.title.trim().ifEmpty { VehicleText.kind(d.kind) }
        if (title.length > 120) errors += LifeError.NameTooLong
        if (d.odometerKm != null && d.odometerKm !in 0..5_000_000) errors += LifeError.InvalidOdometer
        val vehicle = repo.vehicle(d.vehicleId)
        if (vehicle == null) errors += LifeError.NotFound
        if (errors.isNotEmpty() || vehicle == null) return LifeResult.Invalid(errors)
        val e = VehicleEvent(d.id ?: ids.newId(), d.vehicleId, d.kind, title, d.date, d.odometerKm, d.done, d.note.clean())
        val now = clock.now()
        repo.saveEvent(e, now)
        val eventKm = e.odometerKm
        val vehicleKm = vehicle.odometerKm
        if (e.done && eventKm != null && (vehicleKm == null || eventKm > vehicleKm)) {
            repo.saveVehicle(vehicle.copy(odometerKm = eventKm), now)
        }
        return LifeResult.Success(e.id)
    }
}

object VehicleText {
    fun kind(k: VehicleEventKind): String = when (k) {
        VehicleEventKind.PURCHASE -> "Kauf"
        VehicleEventKind.MAINTENANCE -> "Wartung"
        VehicleEventKind.REPAIR -> "Reparatur"
        VehicleEventKind.PARTS -> "Ersatzteile"
        VehicleEventKind.INSPECTION -> "Hauptuntersuchung"
        VehicleEventKind.TIRES -> "Reifen"
        VehicleEventKind.OTHER -> "Sonstiges"
    }
}

/** Ein Punkt auf der Fahrzeug-LifeLine. */
sealed interface VehicleLineItem {
    val date: LocalDate

    data class Purchase(override val date: LocalDate, val priceMinor: Long?) : VehicleLineItem
    data class Event(val event: VehicleEvent) : VehicleLineItem { override val date: LocalDate get() = event.date }
    data class Cost(val tx: LedgerTransaction) : VehicleLineItem { override val date: LocalDate get() = tx.bookingDate }
}

data class VehicleSnapshot(
    val vehicle: Vehicle,
    val line: List<VehicleLineItem>,
    val upcoming: List<VehicleEvent>,
    val transactions: List<LedgerTransaction>,
    /** Laufende Kosten = verknüpfte Ausgaben − Erstattungen (ohne Kaufpreis). */
    val runningCostsMinor: Long,
    val costsThisYearMinor: Long,
    val odometerKm: Int?,
)

object VehicleSnapshotBuilder {
    /**
     * Kosten eines Fahrzeugs: alle Buchungen, die mit dem Fahrzeug ODER einem seiner Ereignisse verknüpft
     * sind – jede Buchung genau einmal (Menge), auch wenn sie mehrfach verknüpft ist.
     */
    fun linkedTransactionIds(vehicleId: NodeId, events: List<VehicleEvent>, links: List<NodeLink>): Set<NodeId> {
        val anchors = (events.filter { it.vehicleId == vehicleId }.map { it.id } + vehicleId).toSet()
        return links.flatMap { l ->
            when {
                l.to in anchors -> listOf(l.from)
                l.from in anchors -> listOf(l.to)
                else -> emptyList()
            }
        }.toSet()
    }

    fun build(vehicle: Vehicle, events: List<VehicleEvent>, links: List<NodeLink>, transactions: List<LedgerTransaction>, today: LocalDate): VehicleSnapshot {
        val own = events.filter { it.vehicleId == vehicle.id }
        val ids = linkedTransactionIds(vehicle.id, events, links)
        val txs = transactions.filter { it.id in ids }.sortedByDescending { it.bookingDate }
        fun net(list: List<LedgerTransaction>) = list.fold(0L) { acc, tx ->
            when (tx.kind) {
                TransactionKind.EXPENSE -> Math.addExact(acc, tx.amount.minor)
                TransactionKind.REFUND -> Math.subtractExact(acc, tx.amount.minor)
                else -> acc
            }
        }
        val line = buildList<VehicleLineItem> {
            vehicle.purchaseDate?.let { add(VehicleLineItem.Purchase(it, vehicle.purchasePriceMinor)) }
            own.forEach { add(VehicleLineItem.Event(it)) }
            txs.forEach { add(VehicleLineItem.Cost(it)) }
        }.sortedByDescending { it.date }
        val odo = (listOfNotNull(vehicle.odometerKm) + own.filter { it.done }.mapNotNull { it.odometerKm }).maxOrNull()
        return VehicleSnapshot(
            vehicle = vehicle,
            line = line,
            upcoming = own.filter { !it.done }.sortedBy { it.date },
            transactions = txs,
            runningCostsMinor = net(txs),
            costsThisYearMinor = net(txs.filter { it.bookingDate.year == today.year }),
            odometerKm = odo,
        )
    }

    /** Hinweise: geplante Ereignisse (z. B. HU) in ≤ 30 Tagen oder überfällig. */
    fun attention(vehicles: List<Vehicle>, events: List<VehicleEvent>, today: LocalDate): List<AttentionItem> {
        val names = vehicles.associate { it.id to it.name }
        return events.filter { !it.done }.mapNotNull { e ->
            val days = ChronoUnit.DAYS.between(today, e.date)
            val vehicle = names[e.vehicleId] ?: return@mapNotNull null
            when {
                days < 0 -> AttentionItem(e.title, "$vehicle · ${LifeFormat.relativeDue(e.date, today)}", AttentionSeverity.ALERT, AttentionTarget.Node(e.vehicleId), days)
                days <= 30 -> AttentionItem(e.title, "$vehicle · ${LifeFormat.relativeDue(e.date, today)}", if (days <= 7) AttentionSeverity.WARN else AttentionSeverity.INFO, AttentionTarget.Node(e.vehicleId), days)
                else -> null
            }
        }
    }
}
