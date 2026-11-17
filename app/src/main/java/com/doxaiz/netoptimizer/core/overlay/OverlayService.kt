package com.doxaiz.netoptimizer.core.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Тонкая строка пинга у верхнего края экрана (18–22 dp).
 * FLAG_NOT_TOUCHABLE — никогда не мешает нажатиям в играх.
 * Плюс тихая строка в шторке уведомлений (IMPORTANCE_LOW, без звука).
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var label: TextView? = null

    companion object {
        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(android.content.Intent(context, OverlayService::class.java))
            } else {
                context.startService(android.content.Intent(context, OverlayService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(android.content.Intent(context, OverlayService::class.java))
        }

        fun updateText(text: String) {
            instance?.label?.text = text
        }

        private var instance: OverlayService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundNotification()
        showOverlay()
    }

    private fun startForegroundNotification() {
        val channelId = "doxaiz_overlay"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Doxaiz", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Doxaiz активен")
            .setContentText("Оптимизация сети работает в фоне")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)
    }

    private fun showOverlay() {
        if (Build.VERSION.SDK_INT >= 26 && !android.provider.Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        label = TextView(this).apply {
            text = "Пинг — мс"
            textSize = 10f
            setTextColor(0xFFCFE6FF.toInt())
            setBackgroundColor(0x3300E5FF)
            setPadding(24, 2, 24, 2)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 4
        }
        windowManager?.addView(label, params)
    }

    override fun onDestroy() {
        try { label?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: android.content.Intent?): IBinder? = null
}
