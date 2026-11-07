package app.parkingsms

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.parkingsms.data.AppDatabase
import app.parkingsms.data.AppRepository
import app.parkingsms.data.SettingsStore
import app.parkingsms.session.AndroidAlarmScheduler
import app.parkingsms.session.AndroidSessionEffects
import app.parkingsms.session.NotificationHelper
import app.parkingsms.session.QuickActions
import app.parkingsms.session.RealClock
import app.parkingsms.session.RoomSessionStore
import app.parkingsms.session.SessionManager
import app.parkingsms.session.SettingsSessionConfig
import app.parkingsms.sms.AndroidSmsSender
import app.parkingsms.sms.SmsSender
import app.parkingsms.sms.SwitchingSmsSender
import app.parkingsms.sms.TestSmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection: one instance per process, created in [ParkingApp]. */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "parking-sms.db").build()
    val repository = AppRepository(database)
    val settingsStore = SettingsStore(appContext)

    val notifications = NotificationHelper(appContext)
    val scheduler = AndroidAlarmScheduler(appContext)
    val effects = AndroidSessionEffects(appContext, notifications)

    val smsSender: SmsSender = SwitchingSmsSender(settingsStore, AndroidSmsSender(appContext), TestSmsSender())

    val sessionManager = SessionManager(
        store = RoomSessionStore(database.sessionDao()),
        smsSender = smsSender,
        scheduler = scheduler,
        effects = effects,
        clock = RealClock,
        config = SettingsSessionConfig(settingsStore)
    )

    val quickActions = QuickActions(appContext, repository, settingsStore, sessionManager, notifications)
}

class ParkingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.ensureChannels()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ParkingApp).container
