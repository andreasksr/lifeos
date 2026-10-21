package app.lifeos.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.home.AttentionBuilder
import app.lifeos.core.domain.home.AttentionItem
import app.lifeos.core.domain.home.HomeDensity
import app.lifeos.core.domain.home.HomeLayout
import app.lifeos.core.domain.home.HomeSectionId
import app.lifeos.core.domain.ledger.LedgerMonthSnapshot
import app.lifeos.core.domain.ports.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Datenquellen der Startseite. Jedes Modul steuert seinen Teil bei; fehlt ein Modul (noch), ist die
 * Quelle `null` und der Abschnitt erscheint nicht – keine Platzhalter.
 */
class HomeDependencies(
    val settings: SettingsRepository,
    val clock: LifeClock,
    val finance: (YearMonth) -> Flow<LedgerMonthSnapshot>,
    val extraSections: List<HomeSectionSource> = emptyList(),
)

/** Erweiterungspunkt für weitere Module (Planung, Projekte, Weiterbildung). */
interface HomeSectionSource {
    val id: HomeSectionId
    val attention: Flow<List<AttentionItem>>
}

data class HomeState(
    val now: LocalDateTime,
    val today: LocalDate,
    val layout: HomeLayout,
    val sections: List<HomeSectionId>,
    val available: Set<HomeSectionId>,
    val finance: LedgerMonthSnapshot?,
    val attention: List<AttentionItem>,
) {
    val density: HomeDensity get() = layout.density
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val deps: HomeDependencies) : ViewModel() {

    /** Minütlicher Takt – damit Begrüßung, „heute" und Fälligkeiten nach Mitternacht stimmen. */
    private val minute: Flow<LocalDate> = flow {
        while (true) {
            emit(deps.clock.today())
            delay(60_000)
        }
    }

    private val layout = deps.settings.observe(HomeLayout.SETTINGS_KEY).map { HomeLayout.decode(it) }

    private val available: Set<HomeSectionId> =
        setOf(HomeSectionId.TODAY, HomeSectionId.ATTENTION, HomeSectionId.FINANCE, HomeSectionId.CAPTURE) +
            deps.extraSections.map { it.id }

    private val extraAttention: Flow<List<AttentionItem>> =
        if (deps.extraSections.isEmpty()) flowOf(emptyList())
        else combine(deps.extraSections.map { it.attention }) { arrays -> arrays.toList().flatten() }

    private val financeFlow = minute.map { YearMonth.from(it) }.distinctUntilChanged()
        .flatMapLatest { month -> deps.finance(month) }

    val state: StateFlow<HomeState?> = minute.distinctUntilChanged()
        .let { days ->
            combine(financeFlow, layout, extraAttention, days) { finance, l, extra, today ->
                HomeState(
                    now = LocalDateTime.ofInstant(deps.clock.now(), ZoneId.systemDefault()),
                    today = today,
                    layout = l,
                    sections = l.visible(available),
                    available = available,
                    finance = finance,
                    attention = AttentionBuilder.merge(AttentionBuilder.fromFinance(finance), extra),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateLayout(change: (HomeLayout) -> HomeLayout) {
        val current = state.value?.layout ?: HomeLayout()
        viewModelScope.launch { deps.settings.put(HomeLayout.SETTINGS_KEY, HomeLayout.encode(change(current))) }
    }
}
