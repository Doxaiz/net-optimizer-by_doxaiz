package com.doxaiz.netoptimizer.core.session

import java.util.concurrent.atomic.AtomicBoolean

/** A network change remains invalid even if the original network becomes active again. */
internal class SessionNetworkGuard<T>(private val original: T) {
    private val changed = AtomicBoolean(false)
    fun available(network: T) { if (network != original) changed.set(true) }
    fun lost(network: T) { if (network == original) changed.set(true) }
    fun capabilities(network: T, isCellularWithInternet: Boolean) {
        if (network == original && !isCellularWithInternet) changed.set(true)
    }
    fun valid(current: T?): Boolean = !changed.get() && current == original
}
