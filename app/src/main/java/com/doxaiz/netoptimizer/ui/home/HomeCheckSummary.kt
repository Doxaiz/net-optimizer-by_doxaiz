package com.doxaiz.netoptimizer.ui.home

import com.doxaiz.netoptimizer.core.net.PingResult
import com.doxaiz.netoptimizer.core.net.PingStatus

/** Wording for a real TCP test. A successful TCP handshake is not proof of full internet access. */
internal data class HomeCheckSummary(
    val headline: String,
    val explanation: String,
    val hasMeasurement: Boolean
)

internal fun homeCheckSummary(result: PingResult): HomeCheckSummary = when (result.status) {
    PingStatus.NO_CELLULAR -> HomeCheckSummary("Нет мобильной сети", "Включите мобильный интернет и повторите.", false)
    PingStatus.NETWORK_CHANGED -> HomeCheckSummary("Сеть изменилась", "Повторите проверку на мобильной сети.", false)
    PingStatus.LOW_BATTERY -> HomeCheckSummary("Проверка приостановлена", "Нужен заряд не ниже 20% и доступные данные батареи.", false)
    PingStatus.RESOLUTION_FAILED -> HomeCheckSummary("Сайт не найден", "Не удалось определить адрес Яндекса. Проверьте подключение.", false)
    PingStatus.COMPLETED -> if (result.avgMs < 0 || result.successfulAttempts <= 0 || result.attemptsMade <= 0) {
        HomeCheckSummary("Нет ответа", "Сайт не ответил на TCP-подключения. Попробуйте ещё раз.", false)
    } else {
        HomeCheckSummary("${result.avgMs} мс", "До сайта Яндекса на момент замера · ответов ${result.successfulAttempts}/${result.attemptsMade}. В играх может отличаться.", true)
    }
}
