package com.whiteink.coloros.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.whiteink.coloros.service.FluidCloudService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("fluidcloud", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_start_enabled", false)) return

        val title = prefs.getString("title", "") ?: ""
        val subtitle = prefs.getString("subtitle", "") ?: ""
        val si = Intent(context, FluidCloudService::class.java)
            .setAction(FluidCloudService.ACTION_UPDATE)
            .putExtra(FluidCloudService.EXTRA_TITLE, title)
            .putExtra(FluidCloudService.EXTRA_SUBTITLE, subtitle)
        ContextCompat.startForegroundService(context, si)
    }
}
