package com.doxaiz.netoptimizer.core.net

/**
 * Conditions reported by Android's NetworkCapabilities for the active cellular network.
 *
 * These flags describe what the system last observed. They are not a speed test and do not
 * measure interference from other apps: a validated, non-congested network can still be slow
 * for reasons Android does not report.
 */
data class NetworkConditions(
    val validated: Boolean,
    val captivePortal: Boolean,
    val notCongested: Boolean,
    val notSuspended: Boolean,
    val notRoaming: Boolean,
    val temporarilyUnmetered: Boolean,
    val metered: Boolean,
    val downstreamKbps: Int,
    val upstreamKbps: Int
)

internal enum class NetworkConditionLevel { GOOD, CAUTION, UNUSABLE }

internal data class NetworkConditionVerdict(
    val level: NetworkConditionLevel,
    val headline: String,
    val details: List<String>
)

/**
 * Only documented capabilities are interpreted; unknown bandwidth values are never
 * presented as a measurement. Android documents VALIDATED as the nearest signal that a
 * network provides actual internet access, and NOT_CONGESTED / NOT_SUSPENDED as temporary
 * hints that can change at any moment.
 */
internal object NetworkConditionPolicy {
    const val UNSPECIFIED_BANDWIDTH = 0

    fun verdict(c: NetworkConditions): NetworkConditionVerdict {
        val details = mutableListOf<String>()
        if (!c.validated) {
            details += "Android не подтвердил доступ в интернет (нет VALIDATED): возможен вход на странице провайдера или сбой проверки."
        }
        if (c.captivePortal) {
            details += "Система обнаружила captive portal: до входа в сеть часть запросов может не работать."
        }
        if (!c.notSuspended) {
            details += "Сеть временно приостановлена (SUSPENDED): сейчас передача данных может не идти."
        }
        if (!c.notCongested) {
            details += "Android помечает сеть как перегруженную (NOT_CONGESTED отсутствует). Приложение не проверяет загрузку канала и не доказывает причину."
        }
        if (!c.notRoaming) {
            details += "Роуминг: тариф и качество могут отличаться, сравнение с домашней сетью некорректно."
        }
        if (c.temporarilyUnmetered && c.metered) {
            details += "Сеть временно безлимитная: статус может измениться в любой момент, фоновая передача должна останавливаться."
        }
        details += bandwidthText(c)

        val level = when {
            c.captivePortal || !c.validated || !c.notSuspended -> NetworkConditionLevel.UNUSABLE
            !c.notCongested || !c.notRoaming || (c.temporarilyUnmetered && c.metered) -> NetworkConditionLevel.CAUTION
            else -> NetworkConditionLevel.GOOD
        }
        val headline = when (level) {
            NetworkConditionLevel.UNUSABLE -> "Сеть сейчас непригодна для достоверного замера."
            NetworkConditionLevel.CAUTION -> "Замер возможен, но условия сети нестабильны."
            NetworkConditionLevel.GOOD -> "Android не сообщает об ограничениях сети."
        }
        return NetworkConditionVerdict(level, headline, details)
    }

    /** Android reports 0 for "unspecified", so it must never be shown as 0 kbps. */
    private fun bandwidthText(c: NetworkConditions): String {
        val down = c.downstreamKbps.takeIf { it > UNSPECIFIED_BANDWIDTH }
        val up = c.upstreamKbps.takeIf { it > UNSPECIFIED_BANDWIDTH }
        return when {
            down != null && up != null ->
                "Оценка полосы от Android: ↓ ${down} кбит/с · ↑ ${up} кбит/с. Это оценка системы, а не измеренная скорость; она может отсутствовать или быть завышенной."
            down != null || up != null -> {
                val only = if (down != null) "↓ ${down} кбит/с" else "↑ ${up} кбит/с"
                "Оценка полосы от Android известна только для одного направления: $only. Второе направление не равно нулю, а неизвестно."
            }
            else -> "Оценка полосы от Android недоступна: это не означает нулевую скорость."
        }
    }
}
