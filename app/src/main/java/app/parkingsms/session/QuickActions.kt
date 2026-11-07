package app.parkingsms.session

import android.content.Context
import app.parkingsms.R
import app.parkingsms.data.AppRepository
import app.parkingsms.data.City
import app.parkingsms.data.EndReason
import app.parkingsms.data.Plate
import app.parkingsms.data.SettingsStore
import kotlinx.coroutines.flow.first

/** What the widget shows while idle. */
data class LastUsed(val city: City, val zone: String, val plate: Plate)

/** Pay/Stop without any UI: used by the widget and the quick tile. */
class QuickActions(
    private val context: Context,
    private val repo: AppRepository,
    private val settings: SettingsStore,
    private val sessionManager: SessionManager,
    private val notifications: NotificationHelper
) {
    suspend fun lastUsed(): LastUsed? {
        val s = settings.snapshot()
        val cities = repo.cities.first()
        val plates = repo.plates.first()
        val city = cities.firstOrNull { it.id == s.lastCityId } ?: cities.firstOrNull() ?: return null
        val plate = plates.firstOrNull { it.id == s.lastPlateId } ?: plates.firstOrNull() ?: return null
        val zone = repo.latestZone(city.id)?.zoneCode ?: return null
        return LastUsed(city, zone, plate)
    }

    suspend fun quickPay(): StartResult {
        val last = lastUsed()
        if (last == null) {
            notifications.showInfo(
                context.getString(R.string.error_quick_title),
                context.getString(R.string.error_quick_incomplete)
            )
            return StartResult.Invalid(InvalidField.ZONE)
        }
        val s = settings.snapshot()
        val result = sessionManager.start(
            StartRequest(
                cityId = last.city.id,
                cityName = last.city.name,
                smsNumber = last.city.smsNumber,
                zoneCode = last.zone,
                plateText = last.plate.plateText,
                timerMinutes = s.lastTimerMinutes,
                mode = s.effectiveMode,
                graceMinutes = s.graceMinutes,
                remindMinutesBefore = if (s.remindEnabled) s.remindMinutes else null
            )
        )
        if (result is StartResult.SmsFailed) {
            notifications.showInfo(
                context.getString(R.string.error_quick_title),
                context.getString(R.string.error_start_failed, result.error ?: "")
            )
        }
        return result
    }

    suspend fun quickStop(): StopResult = sessionManager.stop(EndReason.MANUAL)
}
