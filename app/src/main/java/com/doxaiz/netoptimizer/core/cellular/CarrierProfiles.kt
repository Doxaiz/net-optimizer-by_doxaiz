package com.doxaiz.netoptimizer.core.cellular

/** Профили операторов РФ/СНГ: частоты, MTU, особенности */
data class CarrierProfile(
    val name: String,
    val mccMnc: Set<String>,
    val mainBands: String,
    val recommendedMtu: Int,
    val note: String
)

object CarrierProfiles {
    val all = listOf(
        CarrierProfile("МТС", setOf("25001"), "B3 1800 + B7 2600 + B20 800", 1440, "Хорошая поддержка VoLTE и агрегации B3+B7"),
        CarrierProfile("МегаФон / Yota", setOf("25002", "25011"), "B3 1800 + B7 2600 + B20 800", 1440, "Широкая агрегация LTE-A до 3–4 полос"),
        CarrierProfile("Билайн", setOf("25099"), "B3 1800 + B7 2600 + B20 800", 1420, "Активный рефарминг 2100/900 МГц в 4G"),
        CarrierProfile("t2 (Tele2)", setOf("25020"), "B3 1800 + B7 2600 + B20 800 + B40", 1440, "Есть TDD B40 и дальнобойный B20"),
        CarrierProfile("Операторы СНГ", setOf("40101", "40102", "40107", "25701", "25702", "25501", "25502"), "B1/B3/B7/B8/B20/B28, n78 (5G)", 1420, "Поддержка 5G NR SA/NSA")
    )

    fun find(mccMnc: String): CarrierProfile? =
        all.firstOrNull { it.mccMnc.contains(mccMnc) }

    /** Человеческая расшифровка частоты */
    fun bandHint(band: String): String = when (band) {
        "B20", "B8", "B28" -> "Дальнобойная частота — отлично пробивает стены и ловит за городом, но ёмкость меньше"
        "B3", "B1" -> "Золотая середина — высокая скорость, низкий пинг и уверенный приём в помещении"
        "B7", "B38", "B40" -> "Скоростная городская частота — максимум мегабит, но хуже проходит через толстые стены"
        "n78", "n41", "n1" -> "Сверхбыстрый 5G — максимальная скорость и минимальный пинг на короткой дистанции от вышки"
        else -> "Рабочая частота сотовой сети"
    }
}
