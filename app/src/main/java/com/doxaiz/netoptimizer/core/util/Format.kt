package com.doxaiz.netoptimizer.core.util

/** Человеческие подписи качества для метрик */
object QualityText {
    fun signal(dbm: Int): String = when {
        dbm >= -80 -> "Сильный сигнал"
        dbm >= -90 -> "Хороший уровень сигнала"
        dbm >= -100 -> "Слабо: возможны скачки скорости"
        else -> "Очень слабо: ищите точку у окна"
    }

    fun ping(ms: Int): String = when {
        ms <= 40 -> "Низкая задержка до цели"
        ms <= 70 -> "Умеренная задержка до цели"
        ms <= 120 -> "Высокая задержка до цели"
        else -> "Очень высокая задержка до цели"
    }

    fun scoreVerdict(score: Int): String = when {
        score >= 85 -> "Высокая оценка"
        score >= 65 -> "Хорошая оценка"
        score >= 45 -> "Средняя оценка"
        else -> "Низкая оценка"
    }
}
