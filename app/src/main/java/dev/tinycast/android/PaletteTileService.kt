package dev.tinycast.android

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

class PaletteTileService : TileService() {
    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent=Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        // Android 13 and earlier do not expose the PendingIntent overload.
        else startActivityAndCollapse(intent)
    }
}
