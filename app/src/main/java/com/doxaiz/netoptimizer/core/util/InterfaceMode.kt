package com.doxaiz.netoptimizer.core.util

/** Presentation preference only: it never grants permissions or enables network actions. */
enum class InterfaceMode {
    SIMPLE, EXPERT;

    companion object {
        fun fromStored(value: String?): InterfaceMode? = entries.firstOrNull { it.name == value }
    }
}
