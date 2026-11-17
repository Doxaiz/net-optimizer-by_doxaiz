package com.doxaiz.netoptimizer.core.net

/** Вердикт по сценарию использования сети */
data class ScenarioVerdict(
    val name: String,
    val icon: String,
    val ok: Boolean,
    val text: String
)

/**
 * Тест «потянет ли…» (идея из nPerf и Opensignal):
 * по реальным замерам пинга, джиттера, потерь и скорости даёт понятный
 * вердикт для новичка: 4K-видео, видеозвонок, игры, мессенджеры, файлы.
 */
object ScenarioTester {

    fun evaluate(
        pingMs: Int,
        jitterMs: Int,
        lossPercent: Int,
        downMbps: Double,
        upMbps: Double
    ): List<ScenarioVerdict> {
        val list = mutableListOf<ScenarioVerdict>()

        // 4K видео: нужна скорость и стабильность
        val ok4k = downMbps >= 25 && lossPercent <= 1
        list += ScenarioVerdict(
            "4K видео", "🎬", ok4k,
            when {
                ok4k -> "Скорости хватает с запасом"
                downMbps >= 12 -> "Потянет Full HD, 4K будет буферизовать"
                else -> "Слабая скорость для видео — ищите точку у окна"
            }
        )

        // Видеозвонок: важны пинг, джиттер и отдача
        val okCall = pingMs in 1..150 && jitterMs <= 30 && upMbps >= 3
        list += ScenarioVerdict(
            "Видеозвонок", "📹", okCall,
            when {
                okCall -> "Картинка и звук будут чистыми"
                jitterMs > 30 -> "Звук будет рваться из-за скачков пинга"
                else -> "Слабая отдача — собеседник будет видеть «квадраты»"
            }
        )

        // Конкурентная игра: минимальный пинг и джиттер, 0% потерь
        val okPro = pingMs in 1..60 && jitterMs <= 10 && lossPercent == 0
        list += ScenarioVerdict(
            "Конкурентная игра", "🎮", okPro,
            when {
                okPro -> "Минимальный пинг — можно играть в рейтинг"
                pingMs > 60 -> "Пинг высоковат для шутеров, возможны фризы"
                lossPercent > 0 -> "Есть потери пакетов — будут телепорты персонажа"
                else -> "Скачки пинга мешают прицеливанию"
            }
        )

        // Обычная игра
        val okGame = pingMs in 1..100 && lossPercent <= 2
        list += ScenarioVerdict(
            "Обычная игра", "🕹", okGame,
            when {
                okGame -> "Комфортно играть"
                else -> "Будут заметные лаги"
            }
        )

        // Мессенджеры и соцсети
        val okMsg = pingMs in 1..300 && lossPercent <= 5
        list += ScenarioVerdict(
            "Мессенджеры", "💬", okMsg,
            when {
                okMsg -> "Сообщения и голосовые идут мгновенно"
                else -> "Сообщения могут задерживаться"
            }
        )

        // Скачивание файлов
        val okFile = downMbps >= 10
        list += ScenarioVerdict(
            "Скачивание файлов", "⬇️", okFile,
            when {
                okFile -> "Файлы качаются быстро"
                downMbps >= 3 -> "Качает, но медленно"
                else -> "Очень медленно — проверьте сигнал"
            }
        )

        return list
    }
}
