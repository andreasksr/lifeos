package app.lifeos.feature.life.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.life.LifeError
import app.lifeos.core.domain.life.LifeResult
import app.lifeos.core.domain.life.VehicleDraft
import app.lifeos.core.domain.life.VehicleEventDraft
import app.lifeos.core.domain.life.VehicleSnapshot
import app.lifeos.core.domain.life.VehicleSnapshotBuilder
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.Vehicle
import app.lifeos.core.model.VehicleEvent
import app.lifeos.core.model.VehicleEventKind
import app.lifeos.feature.life.LifeDependencies
import app.lifeos.feature.life.LifeText
import app.lifeos.feature.life.shared.LifeMessage
import app.lifeos.feature.life.shared.LinksState
import app.lifeos.feature.life.shared.NodeLinksController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

// ───────────────────────────── Liste ─────────────────────────────

data class VehicleCard(val vehicle: Vehicle, val odometerKm: Int?, val costsThisYearMinor: Long, val next: VehicleEvent?)

data class VehiclesState(val cards: List<VehicleCard>, val today: LocalDate)

class VehiclesViewModel(deps: LifeDependencies) : ViewModel() {
    val state: StateFlow<VehiclesState?> = combine(
        deps.vehicles.observeVehicles(), deps.vehicles.observeEvents(), deps.links.observeAllLinks(), deps.ledger.observeAllTransactions(),
    ) { vs, es, links, txs ->
        val today = deps.clock.today()
        VehiclesState(
            vs.map { v ->
                val snap = VehicleSnapshotBuilder.build(v, es, links, txs, today)
                VehicleCard(v, snap.odometerKm, snap.costsThisYearMinor, snap.upcoming.firstOrNull())
            },
            today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

// ───────────────────────────── Detail ─────────────────────────────

data class VehicleDetailState(val snapshot: VehicleSnapshot?, val missing: Boolean, val today: LocalDate, val links: LinksState?)

data class EventDraftState(
    val id: NodeId? = null,
    val kind: VehicleEventKind = VehicleEventKind.MAINTENANCE,
    val title: String = "",
    val date: LocalDate,
    val odometerText: String = "",
    val done: Boolean = true,
    val note: String = "",
    val error: String? = null,
)

class VehicleDetailViewModel(private val deps: LifeDependencies, val vehicleId: NodeId) : ViewModel() {
    private val _events = Channel<LifeMessage>(Channel.BUFFERED)
    val events: Flow<LifeMessage> = _events.receiveAsFlow()
    val links = NodeLinksController(deps, vehicleId, viewModelScope) { _events.send(it) }

    val eventDraft = MutableStateFlow<EventDraftState?>(null)
    val confirmDelete = MutableStateFlow(false)
    val deleted = MutableStateFlow(false)

    val state: StateFlow<VehicleDetailState?> = combine(
        deps.vehicles.observeVehicles(), deps.vehicles.observeEvents(), deps.links.observeAllLinks(), deps.ledger.observeAllTransactions(), links.state,
    ) { vs, es, all, txs, ls ->
        val today = deps.clock.today()
        val v = vs.firstOrNull { it.id == vehicleId }
        VehicleDetailState(v?.let { VehicleSnapshotBuilder.build(it, es, all, txs, today) }, v == null, today, ls)
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

    fun newEvent(planned: Boolean) {
        val today = deps.clock.today()
        eventDraft.value = if (planned) {
            EventDraftState(kind = VehicleEventKind.INSPECTION, date = today.plusMonths(1), done = false)
        } else {
            EventDraftState(date = today, odometerText = state.value?.snapshot?.odometerKm?.toString().orEmpty())
        }
    }

    fun editEvent(e: VehicleEvent) {
        eventDraft.value = EventDraftState(e.id, e.kind, e.title, e.date, e.odometerKm?.toString().orEmpty(), e.done, e.note.orEmpty())
    }

    fun updateEvent(change: (EventDraftState) -> EventDraftState) {
        eventDraft.update { it?.let { d -> change(d).copy(error = null) } }
    }

    fun closeEvent() {
        eventDraft.value = null
    }

    fun saveEvent() {
        val d = eventDraft.value ?: return
        val km = LifeText.parseInt(d.odometerText).getOrElse {
            eventDraft.value = d.copy(error = LifeText.error(LifeError.InvalidOdometer))
            return
        }
        run {
            when (val r = deps.saveVehicleEvent(VehicleEventDraft(d.id, vehicleId, d.kind, d.title, d.date, km, d.done, d.note))) {
                is LifeResult.Success -> {
                    eventDraft.value = null
                    _events.send(LifeMessage(if (d.done) "Eintrag in der Fahrzeughistorie gespeichert" else "Geplant: ${d.title.ifBlank { LifeText.eventKind(d.kind) }}", true))
                }
                is LifeResult.Invalid -> eventDraft.value = d.copy(error = LifeText.error(r.errors.first()))
            }
        }
    }

    fun markDone(e: VehicleEvent): Unit = run {
        deps.saveVehicleEvent(VehicleEventDraft(e.id, vehicleId, e.kind, e.title, deps.clock.today(), e.odometerKm, done = true, note = e.note))
        _events.send(LifeMessage("„${e.title}“ erledigt", true))
    }

    fun deleteEvent() {
        val id = eventDraft.value?.id ?: return
        run {
            deps.vehicles.deleteEvent(id)
            eventDraft.value = null
            _events.send(LifeMessage("Eintrag gelöscht – verknüpfte Buchungen bleiben in der Haushaltskasse", true))
        }
    }

    fun requestDelete() {
        confirmDelete.value = true
    }

    fun cancelDelete() {
        confirmDelete.value = false
    }

    fun confirmDeleteVehicle(): Unit = run {
        deps.vehicles.deleteVehicle(vehicleId)
        confirmDelete.value = false
        deleted.value = true
    }
}

// ───────────────────────────── Editor ─────────────────────────────

data class VehicleForm(
    val name: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val plate: String = "",
    val vin: String = "",
    val odometer: String = "",
    val purchaseDate: LocalDate? = null,
    val price: String = "",
    val note: String = "",
)

data class VehicleEditorState(
    val form: VehicleForm = VehicleForm(),
    val isEdit: Boolean,
    val loading: Boolean,
    val saving: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val savedId: NodeId? = null,
    val today: LocalDate,
)

class VehicleEditorViewModel(private val deps: LifeDependencies, private val editId: NodeId?) : ViewModel() {
    private val _state = MutableStateFlow(VehicleEditorState(isEdit = editId != null, loading = editId != null, today = deps.clock.today()))
    val state: StateFlow<VehicleEditorState> = _state.asStateFlow()

    init {
        if (editId != null) {
            viewModelScope.launch {
                val v = deps.vehicles.vehicle(editId)
                _state.update {
                    it.copy(
                        loading = false,
                        form = v?.let { x ->
                            VehicleForm(
                                x.name, x.make, x.model, x.buildYear?.toString().orEmpty(), x.plate.orEmpty(), x.vin.orEmpty(),
                                x.odometerKm?.toString().orEmpty(), x.purchaseDate, x.purchasePriceMinor?.let(MoneyText::toEditText).orEmpty(), x.note.orEmpty(),
                            )
                        } ?: it.form,
                        errors = if (v == null) mapOf("general" to LifeText.error(LifeError.NotFound)) else emptyMap(),
                    )
                }
            }
        }
    }

    fun update(change: (VehicleForm) -> VehicleForm) {
        _state.update { it.copy(form = change(it.form), errors = emptyMap()) }
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        val f = s.form
        val errors = mutableMapOf<String, String>()
        val year = LifeText.parseInt(f.year).getOrElse { errors["year"] = LifeText.error(LifeError.InvalidYear); null }
        val km = LifeText.parseInt(f.odometer).getOrElse { errors["odometer"] = LifeText.error(LifeError.InvalidOdometer); null }
        val price = if (f.price.isBlank()) null else when (val r = MoneyText.parse(f.price)) {
            is MoneyText.ParseResult.Success -> r.minor
            is MoneyText.ParseResult.Failure -> { errors["price"] = "Bitte einen gültigen Betrag eingeben."; null }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                when (val r = deps.saveVehicle(VehicleDraft(editId, f.name, f.make, f.model, year, f.plate, f.vin, km, f.purchaseDate, price, f.note))) {
                    is LifeResult.Success -> _state.update { it.copy(saving = false, savedId = r.value) }
                    is LifeResult.Invalid -> _state.update {
                        it.copy(saving = false, errors = r.errors.associate { e -> fieldOf(e) to LifeText.error(e) })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, errors = mapOf("general" to "Speichern fehlgeschlagen. Es wurde nichts verändert.")) }
            }
        }
    }

    private fun fieldOf(e: LifeError) = when (e) {
        LifeError.NameRequired, LifeError.NameTooLong -> "name"
        LifeError.InvalidYear -> "year"
        LifeError.InvalidOdometer -> "odometer"
        LifeError.InvalidAmount -> "price"
        else -> "general"
    }
}
