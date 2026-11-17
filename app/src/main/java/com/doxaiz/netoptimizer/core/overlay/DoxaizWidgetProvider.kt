package com.doxaiz.netoptimizer.core.overlay

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.doxaiz.netoptimizer.MainActivity
import com.doxaiz.netoptimizer.R

/**
 * Виджет на рабочий стол: скорость, пинг и кнопка «Улучшить».
 * Данные обновляются из приложения через update().
 */
class DoxaizWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.doxaiz_widget)
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        fun update(context: Context, pingMs: Int, downMbps: Double) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, DoxaizWidgetProvider::class.java)
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.doxaiz_widget)
                views.setTextViewText(R.id.widget_speed, if (downMbps > 0) "%.1f Мбит/с".format(downMbps) else "— Мбит/с")
                views.setTextViewText(R.id.widget_ping, "Пинг: %d мс".format(pingMs))
                val openApp = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, openApp)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
