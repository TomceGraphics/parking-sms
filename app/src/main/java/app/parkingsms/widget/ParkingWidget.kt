package app.parkingsms.widget

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.parkingsms.MainActivity
import app.parkingsms.R
import app.parkingsms.container
import app.parkingsms.data.Session
import app.parkingsms.session.LastUsed
import kotlinx.coroutines.flow.first

private val WidgetBlue = Color(0xFF1E4FA3)
private val WidgetButton = Color(0xFFFFFFFF)
private val WidgetRed = Color(0xFFD93025)
private val WidgetButtonText = Color(0xFF1E4FA3)

private sealed interface WidgetData {
    data object NotSetUp : WidgetData
    data class Idle(val last: LastUsed) : WidgetData
    data class Active(val session: Session) : WidgetData
}

class ParkingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        val active = c.repository.activeSession.first()
        val data: WidgetData = when {
            active != null -> WidgetData.Active(active)
            else -> {
                val last = c.quickActions.lastUsed()
                if (last != null) WidgetData.Idle(last) else WidgetData.NotSetUp
            }
        }
        val chronometer = (data as? WidgetData.Active)?.let { buildChronometer(context, it.session) }
        provideContent { WidgetContent(context, data, chronometer) }
    }
}

private fun buildChronometer(context: Context, session: Session): RemoteViews {
    val rv = RemoteViews(context.packageName, R.layout.widget_chronometer)
    val elapsed = (System.currentTimeMillis() - session.startedAt).coerceAtLeast(0L)
    rv.setChronometer(R.id.widget_chrono, SystemClock.elapsedRealtime() - elapsed, null, true)
    return rv
}

@Composable
private fun WidgetContent(context: Context, data: WidgetData, chronometer: RemoteViews?) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetBlue).cornerRadius(20.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (data) {
            is WidgetData.NotSetUp -> {
                Text(
                    text = context.getString(R.string.widget_not_set_up),
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
                )
            }
            is WidgetData.Idle -> {
                Text(
                    text = context.getString(R.string.widget_idle_summary, data.last.city.name, data.last.zone),
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = data.last.plate.plateText,
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp)
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Box(
                    modifier = GlanceModifier.fillMaxWidth().height(52.dp).background(WidgetButton)
                        .cornerRadius(16.dp).clickable(actionRunCallback<PayAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.pay),
                        style = TextStyle(color = ColorProvider(WidgetButtonText), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
            is WidgetData.Active -> {
                Text(
                    text = context.getString(
                        R.string.widget_active_summary, data.session.cityNameSnapshot, data.session.zoneCode
                    ),
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp)
                )
                if (chronometer != null) {
                    AndroidRemoteViews(remoteViews = chronometer)
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
                Box(
                    modifier = GlanceModifier.fillMaxWidth().height(52.dp).background(WidgetRed)
                        .cornerRadius(16.dp).clickable(actionRunCallback<StopAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.action_stop),
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

class PayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container.quickActions.quickPay()
        updateParkingWidgets(context)
    }
}

class StopAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container.quickActions.quickStop()
        updateParkingWidgets(context)
    }
}

class ParkingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ParkingWidget()
}

/** Re-renders every placed widget. Safe to call when none exist. */
suspend fun updateParkingWidgets(context: Context) {
    try {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(ParkingWidget::class.java)
        for (id in ids) ParkingWidget().update(context, id)
    } catch (t: Throwable) {
        Log.w("ParkingSms", "Widget update failed", t)
    }
}
