package app.parkingsms.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.parkingsms.MainActivity
import app.parkingsms.R
import app.parkingsms.container
import app.parkingsms.ui.permissions.PermissionChecks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ParkingTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { handleClick() }
        } else {
            handleClick()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handleClick() {
        scope.launch {
            val c = container
            val active = c.repository.activeSession.first()
            if (active != null) {
                c.quickActions.quickStop()
            } else {
                val testMode = c.settingsStore.snapshot().testMode
                if (!testMode && !PermissionChecks.smsGranted(this@ParkingTileService)) {
                    openApp()
                    return@launch
                }
                c.quickActions.quickPay()
            }
            refreshTile()
        }
    }

    private suspend fun refreshTile() {
        val tile = qsTile ?: return
        val active = container.repository.activeSession.first()
        if (active != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.tile_label_active)
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = getString(R.string.tile_subtitle_active, active.zoneCode)
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_label_idle)
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = getString(R.string.tile_subtitle_idle)
            }
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
