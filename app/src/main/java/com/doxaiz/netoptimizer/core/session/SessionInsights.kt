package com.doxaiz.netoptimizer.core.session

/** Describes stored TCP observations, not ICMP ping or packet loss. */
enum class MetricLevel { GOOD, CAUTION, POOR, UNKNOWN }

object SessionInsights {
    fun latencyLevel(ms: Int): MetricLevel = when {
        ms < 0 -> MetricLevel.UNKNOWN
        ms <= 80 -> MetricLevel.GOOD
        ms <= 150 -> MetricLevel.CAUTION
        else -> MetricLevel.POOR
    }

    fun failureLevel(percent: Int): MetricLevel = when (percent) {
        0 -> MetricLevel.GOOD
        in 1..10 -> MetricLevel.CAUTION
        in 11..100 -> MetricLevel.POOR
        else -> MetricLevel.UNKNOWN
    }

    fun latencyExplanation(ms: Int): String = when (latencyLevel(ms)) {
        MetricLevel.GOOD -> "Быстрое TCP-подключение к выбранному сайту."
        MetricLevel.CAUTION -> "Задержка заметна; для игр результат может отличаться."
        MetricLevel.POOR -> "Высокая задержка до сайта; на результат влияет и сам сайт."
        MetricLevel.UNKNOWN -> "Нет данных о задержке."
    }

    /** A and B can have different conditions; returns a numeric difference only. */
    fun latencyDifference(first: Int, second: Int): Long? =
        if (first < 0 || second < 0) null else second.toLong() - first.toLong()

    fun formatDuration(seconds: Long): String {
        if (seconds < 0) return "—"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) "%d ч %02d мин %02d с".format(hours, minutes, secs)
        else "%d мин %02d с".format(minutes, secs)
    }

    /** Stored series consists of successful batch averages; it has no timestamps or failed batches. */
    fun historyValues(encoded: String): List<Int> = encoded.split(',')
        .mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n >= 0 } }
        .takeLast(120)
}
