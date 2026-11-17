package com.doxaiz.netoptimizer.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.doxaiz.netoptimizer.core.data.SessionEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Красивая PNG-карточка сессии для шаринга (идея из GlassWire / Ookla):
 * статистика + мини-график пинга на фирменном фоне Doxaiz.
 */
object SessionCard {

    fun create(context: Context, session: SessionEntity): File {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Фон: глубокий графит
        canvas.drawColor(Color.parseColor("#070B14"))

        val titlePaint = Paint().apply { color = Color.WHITE; textSize = 56f; isFakeBoldText = true }
        val subPaint = Paint().apply { color = Color.parseColor("#8FA3C0"); textSize = 36f }
        val bigPaint = Paint().apply { color = Color.parseColor("#00E676"); textSize = 110f; isFakeBoldText = true }
        val labelPaint = Paint().apply { color = Color.parseColor("#9FB2CC"); textSize = 34f }
        val accentPaint = Paint().apply { color = Color.parseColor("#00E5FF"); textSize = 40f; isFakeBoldText = true }

        // Шапка
        canvas.drawText("Net Optimizer by Doxaiz", 60f, 110f, accentPaint)
        val shortName = if (session.name.length > 30) session.name.take(28) + "…" else session.name
        canvas.drawText(shortName, 60f, 180f, titlePaint)
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(session.timestamp))
        canvas.drawText("$date · ${session.mode} · ${session.operator}", 60f, 235f, subPaint)

        // Большой пинг
        canvas.drawText("${session.avgPingMs} мс", 60f, 400f, bigPaint)
        canvas.drawText("средний пинг · потери ${session.lossPercent}% · макс ${session.maxPingMs} мс", 60f, 460f, labelPaint)

        // Скорость и сигнал
        canvas.drawText("Скорость: %.1f Мбит/с".format(session.avgSpeedMbps), 60f, 560f, labelPaint)
        canvas.drawText("Сигнал: ${session.avgRsrp} dBm · Band ${session.band}", 60f, 620f, labelPaint)
        canvas.drawText("DNS: ${session.dnsName} · провалов связи: ${session.dropsCount}", 60f, 680f, labelPaint)

        // Мини-график пинга
        val history = session.pingHistory.split(",").mapNotNull { it.toIntOrNull() }
        if (history.size > 1) {
            val min = (history.minOrNull() ?: 0) - 5
            val max = (history.maxOrNull() ?: 100) + 5
            val graphTop = 760f
            val graphBottom = 1150f
            val graphLeft = 60f
            val graphRight = 1020f
            val linePaint = Paint().apply {
                color = Color.parseColor("#A855F7")
                strokeWidth = 6f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            val path = Path()
            history.forEachIndexed { i, v ->
                val x = graphLeft + (graphRight - graphLeft) * i / (history.size - 1)
                val y = graphBottom - ((v - min).toFloat() / (max - min)) * (graphBottom - graphTop)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, linePaint)
            canvas.drawText("График пинга за сессию", 60f, graphTop - 20f, labelPaint)
        }

        // Подпись
        canvas.drawText("100% бесплатно · без регистрации · Open Source", 60f, 1280f, subPaint)

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "doxaiz_session_${session.id}.png")
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
