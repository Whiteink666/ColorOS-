package com.whiteink.coloros.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.whiteink.coloros.R
import com.whiteink.coloros.ui.MainActivity

/**
 * ColorOS 16 流体云服务
 *
 * 关键:
 * - setRequestPromotedOngoing(true) — 流体云入口
 * - NotificationManager.notify() — 不依赖 startForeground()
 * - AlarmManager tick — 保活 + 通知自恢复
 */
class FluidCloudService : Service() {

    private var title = ""
    private var subtitle = ""
    private var stoppedByUser = false

    companion object {
        private const val TAG = "FluidCloudService"
        private const val CHANNEL_ID = "fluid_cloud_channel"
        private const val NOTIFY_ID = 10086
        private const val TICK_RC = 1

        const val ACTION_UPDATE = "com.whiteink.coloros.UPDATE"
        const val ACTION_TICK = "com.whiteink.coloros.TICK"
        const val ACTION_STOP = "com.whiteink.coloros.STOP"
        const val ACTION_SET_AUTO_START = "com.whiteink.coloros.AUTO_START"
        const val EXTRA_TITLE = "capsule_title"
        const val EXTRA_SUBTITLE = "capsule_subtitle"
        const val EXTRA_AUTO_START = "auto_start"
    }

    override fun onCreate() {
        super.onCreate()
        val p = getSharedPreferences("fluidcloud", MODE_PRIVATE)
        title = p.getString("title", "") ?: ""
        subtitle = p.getString("subtitle", "") ?: ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ★ 只要不是停止操作，始终调度 tick 保持服务存活
        if (intent?.action != ACTION_STOP) scheduleTick()

        when (intent?.action) {
            ACTION_UPDATE -> {
                stoppedByUser = false
                title = intent.getStringExtra(EXTRA_TITLE) ?: title
                subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: subtitle
                val p = getSharedPreferences("fluidcloud", MODE_PRIVATE)
                p.edit().putString("title", title).putString("subtitle", subtitle).apply()
                showNotification(title, subtitle)
                return START_STICKY
            }
            ACTION_TICK -> {
                if (title.isEmpty() && subtitle.isEmpty()) {
                    val p = getSharedPreferences("fluidcloud", MODE_PRIVATE)
                    title = p.getString("title", "") ?: ""
                    subtitle = p.getString("subtitle", "") ?: ""
                }
                if (!isNotificationActive()) {
                    Log.w(TAG, "通知丢失，补发")
                    showNotification(title, subtitle)
                }
                checkFluidCloudStatus()
                return START_STICKY
            }
            ACTION_STOP -> {
                stoppedByUser = true
                cancelTick()
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFY_ID)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_AUTO_START -> {
                val enabled = intent.getBooleanExtra(EXTRA_AUTO_START, false)
                getSharedPreferences("fluidcloud", MODE_PRIVATE)
                    .edit().putBoolean("auto_start_enabled", enabled).apply()
                if (enabled) {
                    stoppedByUser = false
                    scheduleTick()
                } else {
                    // 关闭自启动时也保持 tick（用户可能还在手动模式）
                    if (!stoppedByUser) scheduleTick()
                }
                return START_STICKY
            }
            else -> {
                stoppedByUser = false
                showNotification(title, subtitle)
                return START_STICKY
            }
        }
    }

    /** 发送流体云通知 — 大标题+小标题分开展示 */
    private fun showNotification(t: String, s: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ColorOS 流体云", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Fluid Cloud live capsule"
                setShowBadge(false)
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
        )

        val openPending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentTitle = t.ifBlank { "流体云" }
        val contentText = s.ifBlank { "实时活动" }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fluid_cloud)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(openPending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setColor(Color.parseColor("#1A73E8"))
            .setRequestPromotedOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle(contentTitle)
                .bigText(contentText))
            .build()
            .also { it.extras.putBoolean("oplus_smallicon_use_app_icon", false) }

        nm.notify(NOTIFY_ID, notification)
        checkFluidCloudStatus()
    }

    private fun isNotificationActive(): Boolean {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return nm.activeNotifications.any { it.id == NOTIFY_ID }
    }

    private fun checkFluidCloudStatus() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val sbn = nm.activeNotifications.find { it.id == NOTIFY_ID } ?: return
            val f = android.app.Notification::class.java.getDeclaredField("flags")
            f.isAccessible = true
            val enabled = (f.getInt(sbn.notification) and 0x40000000) != 0
            getSharedPreferences("fluidcloud", MODE_PRIVATE)
                .edit().putBoolean("fluid_cloud_enabled", enabled).apply()
        } catch (_: Exception) {}
    }

    private fun scheduleTick() {
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getService(
            this, TICK_RC,
            Intent(this, FluidCloudService::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val trigger = System.currentTimeMillis() + 10
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
        catch (_: SecurityException) {
            try { am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, null), pi) }
            catch (_: SecurityException) { am.set(AlarmManager.RTC_WAKEUP, trigger, pi) }
        }
    }

    private fun cancelTick() {
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getService(
            this, TICK_RC,
            Intent(this, FluidCloudService::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        pi?.let { am.cancel(it) }
    }

    private fun isAutoStart() =
        getSharedPreferences("fluidcloud", MODE_PRIVATE).getBoolean("auto_start_enabled", false)

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isAutoStart()) {
            try { startService(Intent(this, FluidCloudService::class.java).setAction(ACTION_TICK)) }
            catch (_: Exception) {}
            scheduleTick()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (isAutoStart() && !stoppedByUser) scheduleTick()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
