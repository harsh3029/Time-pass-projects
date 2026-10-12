package com.timepass.anytranslate.anywhere

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.timepass.anytranslate.MainActivity
import com.timepass.anytranslate.R

/** "Translate screen" Quick Settings tile. */
class TranslateTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val service = TranslateAccessibilityService.instance
        if (service != null) {
            service.translateFromQuickSettings()
        } else {
            // Not set up yet: take the user to the setup screen.
            openApp(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ANYWHERE)
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun openApp(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val ready = TranslateAccessibilityService.instance != null
        // It's a one-shot action, so the tile stays clickable either way (unset-up taps open the app).
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (ready) R.string.tile_ready else R.string.tile_setup)
        }
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, TranslateTileService::class.java))
            }
        }
    }
}
