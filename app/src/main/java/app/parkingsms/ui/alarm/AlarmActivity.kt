package app.parkingsms.ui.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import app.parkingsms.ui.PlateBadge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.parkingsms.R
import app.parkingsms.container
import app.parkingsms.data.AppSettings
import app.parkingsms.data.EndReason
import app.parkingsms.data.Session
import app.parkingsms.data.SmsStatus
import app.parkingsms.data.stopStatus
import kotlinx.coroutines.launch

/** Full-screen alarm shown over the lock screen when the parking timer ends (or a stop SMS failed). */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val c = container

        // Close when the session is over, or when the alarm was dismissed elsewhere (notification action).
        lifecycleScope.launch {
            c.repository.activeSession.collect { active -> if (active == null) finish() }
        }
        lifecycleScope.launch {
            var seen = false
            c.effects.alarmState.collect { state ->
                if (state != null) seen = true else if (seen) finish()
            }
        }

        setContent {
            val session by c.repository.activeSession.collectAsStateWithLifecycle<Session?>(initialValue = null)
            val settings by c.settingsStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            MaterialTheme {
                AlarmScreen(
                    session = session,
                    snoozeMinutes = settings.snoozeMinutes,
                    onStop = { c.appScope.launch { c.sessionManager.stop(EndReason.ALARM_STOPPED) } },
                    onSnooze = {
                        c.appScope.launch { c.sessionManager.snooze() }
                        finish()
                    }
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            addLegacyLockScreenFlags()
        }
    }

    @Suppress("DEPRECATION")
    private fun addLegacyLockScreenFlags() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }
}

@Composable
private fun AlarmScreen(
    session: Session?,
    snoozeMinutes: Int,
    onStop: () -> Unit,
    onSnooze: () -> Unit
) {
    val stopFailed = session?.stopStatus == SmsStatus.FAILED
    val bg = if (stopFailed) Color(0xFFB3261E) else Color(0xFF0B1F44)
    val fg = Color.White

    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "pulseValue"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size((170f * pulse).dp)
                    .clip(CircleShape)
                    .background(fg.copy(alpha = 0.16f))
            )
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(fg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.sign_p),
                    color = bg,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(if (stopFailed) R.string.alarm_title_stop_failed else R.string.alarm_title),
            color = fg,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        if (session != null) {
            PlateBadge(session.plateText)
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.alarm_zone_plate, session.cityNameSnapshot, session.zoneCode),
                color = fg,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onStop,
            shape = RoundedCornerShape(28.dp),
            colors = if (stopFailed) {
                ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF8C1A12))
            } else {
                ButtonDefaults.buttonColors(containerColor = Color(0xFFD93025), contentColor = Color.White)
            },
            modifier = Modifier.fillMaxWidth().height(112.dp)
        ) {
            Text(
                stringResource(if (stopFailed) R.string.alarm_retry else R.string.alarm_stop),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
        if (!stopFailed) {
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSnooze,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(80.dp)
            ) {
                Text(
                    stringResource(R.string.alarm_snooze, snoozeMinutes),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
