package com.example.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.R
import com.example.ScheduleApp

/**
 * Control-center Quick Settings tile allowing instant launch into RipplePlan / Quick Smart Capture
 * while displaying the currently active Live Activity title when available.
 */
class RipplePlanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTileState()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHORTCUT_SMART_CAPTURE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val launchBlock = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    TILE_LAUNCH_REQUEST_CODE,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(launchIntent)
            }
        }

        if (isLocked) {
            unlockAndRun { launchBlock() }
        } else {
            launchBlock()
        }
    }

    private fun refreshTileState() {
        val tile = qsTile ?: return
        val activeItem = (applicationContext as? ScheduleApp)
            ?.container
            ?.liveUpdateManager
            ?.activeLiveActivity
            ?.value

        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_flow_schedule)
        tile.state = if (activeItem != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = activeItem?.title?.takeIf { it.isNotBlank() }
                ?: getString(R.string.tile_quick_subtitle)
        }
        runCatching { tile.updateTile() }
    }

    companion object {
        private const val TILE_LAUNCH_REQUEST_CODE = 2005
    }
}
