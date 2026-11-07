package app.parkingsms.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.parkingsms.AppContainer
import app.parkingsms.R
import app.parkingsms.data.AppSettings
import app.parkingsms.data.City
import app.parkingsms.data.EndReason
import app.parkingsms.data.Plate
import app.parkingsms.data.RecentZone
import app.parkingsms.data.Session
import app.parkingsms.data.TimerMode
import app.parkingsms.session.InvalidField
import app.parkingsms.session.StartRequest
import app.parkingsms.session.StartResult
import app.parkingsms.session.StopResult
import app.parkingsms.ui.UiEvent
import app.parkingsms.util.TextCleaner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FormState(
    val cityId: Long? = null,
    val zone: String = "",
    val plateId: Long? = null,
    val timerMinutes: Int? = AppSettings.DEFAULT_TIMER_MINUTES,
    val mode: TimerMode = TimerMode.ALARM_ONLY,
    val remind: Boolean = false,
    val remindMinutes: Int = 10
)

data class DashboardUi(
    val form: FormState,
    val cities: List<City>,
    val plates: List<Plate>,
    val zones: List<RecentZone>,
    val active: Session?,
    val settings: AppSettings,
    val busy: Boolean
) {
    val selectedCity: City? get() = cities.firstOrNull { it.id == form.cityId }
    val selectedPlate: Plate? get() = plates.firstOrNull { it.id == form.plateId } ?: plates.firstOrNull()
    val canPay: Boolean
        get() = selectedCity != null && selectedPlate != null && form.zone.isNotEmpty() && !busy && active == null
}

private data class Part(
    val form: FormState,
    val cities: List<City>,
    val plates: List<Plate>,
    val zones: List<RecentZone>
)

class DashboardViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val settingsStore = c.settingsStore

    private val form = MutableStateFlow(FormState())
    private val busy = MutableStateFlow(false)
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val zones: Flow<List<RecentZone>> = form
        .map { it.cityId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf<List<RecentZone>>(emptyList()) else repo.recentZones(id)
        }

    val ui: StateFlow<DashboardUi?> = combine(
        combine(form, repo.cities, repo.plates, zones) { f, ci, pl, z -> Part(f, ci, pl, z) },
        repo.activeSession,
        settingsStore.settings,
        busy
    ) { p, active, s, b -> DashboardUi(p.form, p.cities, p.plates, p.zones, active, s, b) }
        .stateIn<DashboardUi?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val s = settingsStore.snapshot()
            val cities = repo.cities.first()
            val plates = repo.plates.first()
            val city = cities.firstOrNull { it.id == s.lastCityId } ?: cities.firstOrNull()
            val plate = plates.firstOrNull { it.id == s.lastPlateId } ?: plates.firstOrNull()
            val zone = city?.let { repo.latestZone(it.id)?.zoneCode } ?: ""
            form.value = FormState(
                cityId = city?.id,
                zone = zone,
                plateId = plate?.id,
                timerMinutes = s.lastTimerMinutes,
                mode = s.effectiveMode,
                remind = s.remindEnabled,
                remindMinutes = s.remindMinutes
            )
            // Keep the selection valid when cities change (deleted / added elsewhere).
            repo.cities.collect { list ->
                val current = form.value.cityId
                if (list.none { it.id == current }) selectCity(list.firstOrNull()?.id)
            }
        }
        viewModelScope.launch {
            repo.plates.collect { list ->
                val current = form.value.plateId
                if (list.none { it.id == current }) form.update { it.copy(plateId = list.firstOrNull()?.id) }
            }
        }
    }

    fun selectCity(id: Long?) {
        form.update { it.copy(cityId = id) }
        viewModelScope.launch {
            val z = if (id != null) repo.latestZone(id)?.zoneCode ?: "" else ""
            form.update { it.copy(zone = z) }
        }
    }

    fun setZone(text: String) = form.update { it.copy(zone = TextCleaner.cleanZone(text)) }
    fun selectPlate(id: Long) = form.update { it.copy(plateId = id) }
    fun setTimer(minutes: Int?) = form.update { it.copy(timerMinutes = minutes) }
    fun setMode(mode: TimerMode) = form.update { it.copy(mode = mode) }
    fun setRemind(enabled: Boolean) = form.update { it.copy(remind = enabled) }
    fun setRemindMinutes(minutes: Int) = form.update { it.copy(remindMinutes = minutes) }

    fun addCity(name: String, number: String) {
        viewModelScope.launch {
            val id = repo.addCity(name, number)
            selectCity(id)
        }
    }

    fun removeZone(zone: RecentZone) {
        viewModelScope.launch { repo.removeZone(zone.id) }
    }

    fun pay() {
        viewModelScope.launch {
            if (busy.value) return@launch
            busy.value = true
            try {
                val f = form.value
                val s = settingsStore.snapshot()
                val city = repo.cities.first().firstOrNull { it.id == f.cityId }
                val plate = repo.plates.first().let { list -> list.firstOrNull { it.id == f.plateId } ?: list.firstOrNull() }
                val zone = TextCleaner.cleanZone(f.zone)
                if (city == null) {
                    _events.tryEmit(UiEvent(R.string.error_no_city))
                    return@launch
                }
                if (plate == null) {
                    _events.tryEmit(UiEvent(R.string.error_no_plate))
                    return@launch
                }
                if (zone.isEmpty()) {
                    _events.tryEmit(UiEvent(R.string.error_no_zone))
                    return@launch
                }
                settingsStore.setLastUsed(city.id, plate.id, f.timerMinutes, f.mode, f.remind, f.remindMinutes)
                // Zones are remembered only when Pay is pressed, never while typing.
                repo.recordZone(city.id, zone, System.currentTimeMillis())
                val result = c.sessionManager.start(
                    StartRequest(
                        cityId = city.id,
                        cityName = city.name,
                        smsNumber = city.smsNumber,
                        zoneCode = zone,
                        plateText = plate.plateText,
                        timerMinutes = f.timerMinutes,
                        mode = f.mode,
                        graceMinutes = s.graceMinutes,
                        remindMinutesBefore = if (f.remind) f.remindMinutes else null
                    )
                )
                when (result) {
                    is StartResult.SmsFailed ->
                        _events.tryEmit(UiEvent(R.string.error_start_failed, listOf(result.error ?: "")))
                    is StartResult.Invalid -> _events.tryEmit(
                        UiEvent(
                            when (result.field) {
                                InvalidField.CITY -> R.string.error_no_city
                                InvalidField.ZONE -> R.string.error_no_zone
                                InvalidField.PLATE -> R.string.error_no_plate
                                InvalidField.TEMPLATE -> R.string.error_bad_template
                            }
                        )
                    )
                    is StartResult.Started, StartResult.AlreadyActive -> Unit
                }
            } finally {
                busy.value = false
            }
        }
    }

    fun stop() {
        viewModelScope.launch {
            val result = c.sessionManager.stop(EndReason.MANUAL)
            if (result is StopResult.Failed) {
                _events.tryEmit(UiEvent(R.string.error_stop_failed, listOf(result.error ?: "")))
            }
        }
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(c) }
        }
    }
}
