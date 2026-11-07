package app.parkingsms.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.parkingsms.util.TimerMath

enum class TimerMode {
    ALARM_ONLY, AUTO_STOP, ALARM_THEN_AUTO_STOP;

    companion object {
        fun fromName(name: String?): TimerMode = values().firstOrNull { it.name == name } ?: ALARM_ONLY
    }
}

enum class SmsStatus {
    NONE, SENDING, SENT, FAILED;

    companion object {
        fun fromName(name: String?): SmsStatus = values().firstOrNull { it.name == name } ?: NONE
    }
}

enum class EndReason {
    MANUAL, AUTO_STOP, ALARM_STOPPED, FAILED;

    companion object {
        fun fromName(name: String?): EndReason? = values().firstOrNull { it.name == name }
    }
}

enum class AlarmKind {
    RING, AUTO_STOP, REMINDER;

    companion object {
        fun fromName(name: String?): AlarmKind? = values().firstOrNull { it.name == name }
    }
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromName(name: String?): ThemeMode = values().firstOrNull { it.name == name } ?: SYSTEM
    }
}

@Entity(tableName = "cities")
data class City(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val smsNumber: String
)

@Entity(tableName = "plates")
data class Plate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plateText: String,
    val label: String? = null,
    /** Reserved for a future feature (car Bluetooth). Unused for now. */
    val carBluetoothAddress: String? = null
)

@Entity(
    tableName = "recent_zones",
    indices = [Index(value = ["cityId", "zoneCode"], unique = true)]
)
data class RecentZone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cityId: Long,
    val zoneCode: String,
    val lastUsedAt: Long
)

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Not a foreign key on purpose: deleting a city keeps its history. */
    val cityId: Long?,
    val cityNameSnapshot: String,
    val smsNumberSnapshot: String,
    val zoneCode: String,
    val plateText: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val timerMinutes: Int? = null,
    val timerMode: String = TimerMode.ALARM_ONLY.name,
    val graceMinutes: Int = 5,
    val remindMinutesBefore: Int? = null,
    val endReason: String? = null,
    val startSmsStatus: String = SmsStatus.NONE.name,
    val stopSmsStatus: String = SmsStatus.NONE.name,
    val replyText: String? = null,
    /** Plain-text audit log of every SMS attempt (also used by Test mode). */
    val smsLog: String? = null,
    /** Next main alarm (RING or AUTO_STOP) and when; persisted so it survives reboots. */
    val alarmKind: String? = null,
    val alarmAt: Long? = null,
    val reminderAt: Long? = null,
    val active: Boolean = true
)

val Session.mode: TimerMode get() = TimerMode.fromName(timerMode)
val Session.startStatus: SmsStatus get() = SmsStatus.fromName(startSmsStatus)
val Session.stopStatus: SmsStatus get() = SmsStatus.fromName(stopSmsStatus)
val Session.reason: EndReason? get() = EndReason.fromName(endReason)
val Session.endsAt: Long? get() = TimerMath.endTime(startedAt, timerMinutes)
