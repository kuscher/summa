package io.github.kuscher.summa

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.kuscher.summa.ui.MiniActivity

/** Quick Settings tile: opens the mini calculator. */
class MiniTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= 29) subtitle = "Quick sums"
            updateTile()
        }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // only below Android 14
    override fun onClick() {
        val i = Intent(this, MiniActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            // Small window, bottom-right (desktop mode would otherwise pick a big default).
            val dm = resources.displayMetrics
            val w = (380 * dm.density).toInt(); val h = (460 * dm.density).toInt(); val m = (24 * dm.density).toInt()
            val opts = android.app.ActivityOptions.makeBasic()
                .setLaunchBounds(android.graphics.Rect(dm.widthPixels - w - m, dm.heightPixels - h - m * 3, dm.widthPixels - m, dm.heightPixels - m * 3))
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, opts.toBundle()))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
    }
}
