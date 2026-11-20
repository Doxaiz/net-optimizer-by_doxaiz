package com.doxaiz.netoptimizer.core.traffic

import com.doxaiz.netoptimizer.core.net.CustomTarget
import com.doxaiz.netoptimizer.core.net.PingTarget

/** A user-selected TCP endpoint; parsed locally and never silently substituted. */
internal object ComparisonEndpoint {
    const val DEFAULT_HOST = "yandex.ru"
    const val DEFAULT_PORT = "443"

    fun parse(host: String, port: String): PingTarget? = CustomTarget.parse(host, port)

    fun label(target: PingTarget): String =
        (if (':' in target.host) "[${target.host}]" else target.host) + ":${target.port}"
}
