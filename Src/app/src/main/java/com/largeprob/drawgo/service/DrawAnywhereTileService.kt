package com.largeprob.drawgo.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.largeprob.drawgo.MainActivity

class DrawAnywhereTileService: TileService(){

    override fun onClick() {
        super.onClick()
        val serviceIntent = Intent(this, MainService::class.java)

        if (isServiceRunning) {
            stopService(serviceIntent)
            qsTile.state = Tile.STATE_INACTIVE
        } else {
            if (!Settings.canDrawOverlays(this)) {
                val permissionIntent = Intent(this, MainActivity::class.java)
                permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(this, 0, permissionIntent, PendingIntent.FLAG_IMMUTABLE)
                    pendingIntent?.let { startActivityAndCollapse(it) }
                } else {
                    @SuppressLint("StartActivityAndCollapseDeprecated")
                    startActivityAndCollapse(permissionIntent)
                }
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // API 26 及以上：使用新的 startForegroundService()
                this.startForegroundService(serviceIntent)
            } else {
                // API 25 及以下（包括 API 24）：使用旧的 startService()
                this.startService(serviceIntent)
            }
            qsTile.state = Tile.STATE_ACTIVE
        }

        qsTile.updateTile()
    }

    private val isServiceRunning: Boolean
        get() = MainService::class.java.name in (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .map { it.service.className }

    override fun onStartListening() {
        super.onStartListening()
        qsTile.state = if (isServiceRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

}