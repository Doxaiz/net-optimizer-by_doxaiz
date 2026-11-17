package com.doxaiz.netoptimizer.core.dns

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Одна запись лога DNS-запросов */
data class DnsLogEntry(val domain: String, val timestamp: Long, val blocked: Boolean)

/**
 * Лог DNS-запросов (идея из RethinkDNS): какие домены дёргают приложения
 * и сколько рекламных/трекинговых запросов заблокировано локально.
 * Всё хранится только на устройстве.
 */
object DnsLog {

    private val _entries = MutableStateFlow<List<DnsLogEntry>>(emptyList())
    val entries: StateFlow<List<DnsLogEntry>> = _entries

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount

    /** Локальный список рекламных и трекинговых доменов (суффиксное совпадение) */
    private val blocklist = setOf(
        "googlesyndication.com", "doubleclick.net", "google-analytics.com",
        "googletagmanager.com", "connect.facebook.net", "an.facebook.com",
        "analytics.yahoo.com", "adsrvr.org", "criteo.com", "hotjar.com",
        "mc.yandex.ru", "metrica.yandex.ru", "mc.yandex.com", "adfox.ru",
        "taboola.com", "outbrain.com", "appsflyer.com", "adjust.com",
        "branch.io", "mixpanel.com", "amplitude.com", "segment.io",
        "fullstory.com", "mouseflow.com", "clarity.microsoft.com",
        "bat.bing.com", "scorecardresearch.com", "quantserve.com",
        "newrelic.com", "datadoghq.com", "sentry.io"
    )

    /** Компания-владелец домена (для понятного лога, как в TrackerControl) */
    private val companies = mapOf(
        "google.com" to "Google", "googleapis.com" to "Google",
        "gstatic.com" to "Google", "youtube.com" to "Google",
        "facebook.com" to "Meta", "instagram.com" to "Meta",
        "yandex.ru" to "Яндекс", "yandex.com" to "Яндекс",
        "vk.com" to "VK", "mail.ru" to "VK",
        "microsoft.com" to "Microsoft", "apple.com" to "Apple",
        "amazon.com" to "Amazon", "cloudflare.com" to "Cloudflare",
        "telegram.org" to "Telegram", "whatsapp.com" to "Meta",
        "tiktok.com" to "ByteDance", "bytedance.com" to "ByteDance",
        "samsung.com" to "Samsung", "xiaomi.com" to "Xiaomi",
        "huawei.com" to "Huawei", "ozon.ru" to "Ozon",
        "avito.ru" to "Avito", "wildberries.ru" to "Wildberries"
    )

    fun companyOf(domain: String): String? {
        val d = domain.lowercase()
        return companies.entries.firstOrNull { d == it.key || d.endsWith("." + it.key) }?.value
    }

    fun isBlocked(domain: String): Boolean {
        val d = domain.lowercase()
        return blocklist.any { d == it || d.endsWith(".$it") }
    }

    fun log(domain: String, blocked: Boolean) {
        val entry = DnsLogEntry(domain, System.currentTimeMillis(), blocked)
        _entries.value = (listOf(entry) + _entries.value).take(100)
        if (blocked) _blockedCount.value++
    }

    fun clear() {
        _entries.value = emptyList()
        _blockedCount.value = 0
    }
}
