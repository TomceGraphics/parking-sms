package app.parkingsms.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.parkingsms.util.MessageTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val onboardingDone: Boolean = false,
    val lastCityId: Long? = null,
    val lastPlateId: Long? = null,
    /** null = "No timer". */
    val lastTimerMinutes: Int? = DEFAULT_TIMER_MINUTES,
    val lastMode: TimerMode? = null,
    val defaultMode: TimerMode = TimerMode.ALARM_ONLY,
    val remindEnabled: Boolean = false,
    val remindMinutes: Int = 10,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val graceMinutes: Int = 5,
    val snoozeMinutes: Int = 5,
    val confirmBeforePay: Boolean = false,
    val testMode: Boolean = false,
    val replyParsing: Boolean = false,
    val startTemplate: String = MessageTemplate.DEFAULT_START,
    val stopTemplate: String = MessageTemplate.DEFAULT_STOP
) {
    /** Mode shown on the dashboard: last used, else the default from Settings. */
    val effectiveMode: TimerMode get() = lastMode ?: defaultMode

    companion object {
        const val DEFAULT_TIMER_MINUTES = 60
    }
}

class SettingsStore(context: Context) {
    private val ds: DataStore<Preferences> = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = ds.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    suspend fun snapshot(): AppSettings = settings.first()

    suspend fun setOnboardingDone(done: Boolean) {
        ds.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setLastCity(id: Long) {
        ds.edit { it[Keys.LAST_CITY] = id }
    }

    suspend fun setLastPlate(id: Long) {
        ds.edit { it[Keys.LAST_PLATE] = id }
    }

    suspend fun setLastUsed(
        cityId: Long,
        plateId: Long,
        timerMinutes: Int?,
        mode: TimerMode,
        remindEnabled: Boolean,
        remindMinutes: Int
    ) {
        ds.edit {
            it[Keys.LAST_CITY] = cityId
            it[Keys.LAST_PLATE] = plateId
            it[Keys.LAST_TIMER] = timerMinutes ?: 0
            it[Keys.LAST_MODE] = mode.name
            it[Keys.REMIND_ENABLED] = remindEnabled
            it[Keys.REMIND_MINUTES] = remindMinutes
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        ds.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        ds.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    /** Changing the default also clears the remembered last-used mode so the change is visible. */
    suspend fun setDefaultMode(mode: TimerMode) {
        ds.edit {
            it[Keys.DEFAULT_MODE] = mode.name
            it.remove(Keys.LAST_MODE)
        }
    }

    suspend fun setGraceMinutes(minutes: Int) {
        ds.edit { it[Keys.GRACE] = minutes }
    }

    suspend fun setSnoozeMinutes(minutes: Int) {
        ds.edit { it[Keys.SNOOZE] = minutes }
    }

    suspend fun setConfirmBeforePay(enabled: Boolean) {
        ds.edit { it[Keys.CONFIRM] = enabled }
    }

    suspend fun setTestMode(enabled: Boolean) {
        ds.edit { it[Keys.TEST_MODE] = enabled }
    }

    suspend fun setReplyParsing(enabled: Boolean) {
        ds.edit { it[Keys.REPLY_PARSING] = enabled }
    }

    suspend fun setTemplates(start: String, stop: String) {
        ds.edit {
            it[Keys.START_TEMPLATE] = start
            it[Keys.STOP_TEMPLATE] = stop
        }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        onboardingDone = this[Keys.ONBOARDING_DONE] ?: false,
        lastCityId = this[Keys.LAST_CITY],
        lastPlateId = this[Keys.LAST_PLATE],
        lastTimerMinutes = (this[Keys.LAST_TIMER] ?: AppSettings.DEFAULT_TIMER_MINUTES).takeIf { it > 0 },
        lastMode = this[Keys.LAST_MODE]?.let { TimerMode.fromName(it) },
        defaultMode = TimerMode.fromName(this[Keys.DEFAULT_MODE]),
        remindEnabled = this[Keys.REMIND_ENABLED] ?: false,
        remindMinutes = this[Keys.REMIND_MINUTES] ?: 10,
        themeMode = ThemeMode.fromName(this[Keys.THEME]),
        dynamicColor = this[Keys.DYNAMIC_COLOR] ?: false,
        graceMinutes = this[Keys.GRACE] ?: 5,
        snoozeMinutes = this[Keys.SNOOZE] ?: 5,
        confirmBeforePay = this[Keys.CONFIRM] ?: false,
        testMode = this[Keys.TEST_MODE] ?: false,
        replyParsing = this[Keys.REPLY_PARSING] ?: false,
        startTemplate = this[Keys.START_TEMPLATE] ?: MessageTemplate.DEFAULT_START,
        stopTemplate = this[Keys.STOP_TEMPLATE] ?: MessageTemplate.DEFAULT_STOP
    )

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LAST_CITY = longPreferencesKey("last_city_id")
        val LAST_PLATE = longPreferencesKey("last_plate_id")
        val LAST_TIMER = intPreferencesKey("last_timer_minutes")
        val LAST_MODE = stringPreferencesKey("last_mode")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val REMIND_ENABLED = booleanPreferencesKey("remind_enabled")
        val REMIND_MINUTES = intPreferencesKey("remind_minutes")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val GRACE = intPreferencesKey("grace_minutes")
        val SNOOZE = intPreferencesKey("snooze_minutes")
        val CONFIRM = booleanPreferencesKey("confirm_before_pay")
        val TEST_MODE = booleanPreferencesKey("test_mode")
        val REPLY_PARSING = booleanPreferencesKey("reply_parsing")
        val START_TEMPLATE = stringPreferencesKey("start_template")
        val STOP_TEMPLATE = stringPreferencesKey("stop_template")
    }
}
