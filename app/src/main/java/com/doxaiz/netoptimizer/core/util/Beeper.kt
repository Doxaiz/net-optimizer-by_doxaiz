package com.doxaiz.netoptimizer.core.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import kotlin.math.PI
import kotlin.math.sin

/**
 * Короткий звуковой бип для радара сигнала: чем лучше сигнал — тем выше тон.
 * Генерирует синусоиду через AudioTrack (без файлов и ToneGenerator).
 */
object Beeper {

    fun beep(strength01: Float, durationMs: Int = 70) {
        try {
            val sampleRate = 22050
            val freq = 500f + strength01.coerceIn(0f, 1f) * 900f // 500–1400 Гц
            val samples = (sampleRate * durationMs / 1000).toInt()
            val buffer = ShortArray(samples)
            for (i in 0 until samples) {
                val t = i.toDouble() / sampleRate
                val envelope = 1.0 - (i.toDouble() / samples) // затухание
                buffer[i] = (sin(2 * PI * freq * t) * 12000 * envelope).toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(buffer, 0, buffer.size)
            track.play()
            // Освобождаем после проигрывания
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { track.stop(); track.release() } catch (_: Exception) {}
            }, durationMs.toLong() + 100)
        } catch (_: Exception) {
            // Звук недоступен — молча пропускаем
        }
    }

    fun vibrate(context: android.content.Context, ms: Long = 30) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(ms)
        } catch (_: Exception) {
            // Вибрации нет — пропускаем
        }
    }
}
