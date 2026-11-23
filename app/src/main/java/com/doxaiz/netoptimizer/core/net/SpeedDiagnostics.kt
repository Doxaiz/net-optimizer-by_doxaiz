package com.doxaiz.netoptimizer.core.net

/** Hints are observations from one short test, never a diagnosis of a network or an app. */
enum class SpeedHint { INCOMPLETE, SLOW_DOWNLOAD, SLOW_UPLOAD, HIGH_IDLE_TCP, LOADED_TCP_INCREASE, UPLOAD_LOADED_TCP_INCREASE, NO_STRONG_INDICATION }

data class SpeedDifference(val downloadMbps: Double?, val uploadMbps: Double?)

enum class SpeedComparisonStatus { READY, INCOMPLETE, MISSING_METADATA, DIFFERENT_NETWORK, EXPIRED, INVALID_TIME }

object SpeedDiagnostics {
    const val COMPARISON_WINDOW_MS = 10 * 60_000L

    /** Same handle at test completion is necessary but cannot prove uninterrupted connectivity between tests. */
    fun comparisonStatus(first: SpeedResult, second: SpeedResult, nowElapsedMs: Long): SpeedComparisonStatus {
        if (first.status != SpeedStatus.COMPLETED || second.status != SpeedStatus.COMPLETED) return SpeedComparisonStatus.INCOMPLETE
        val firstNetwork = first.networkHandle ?: return SpeedComparisonStatus.MISSING_METADATA
        val secondNetwork = second.networkHandle ?: return SpeedComparisonStatus.MISSING_METADATA
        val firstTime = first.completedAtElapsedMs ?: return SpeedComparisonStatus.MISSING_METADATA
        val secondTime = second.completedAtElapsedMs ?: return SpeedComparisonStatus.MISSING_METADATA
        if (firstTime < 0 || secondTime < firstTime || nowElapsedMs < secondTime) return SpeedComparisonStatus.INVALID_TIME
        if (firstNetwork != secondNetwork) return SpeedComparisonStatus.DIFFERENT_NETWORK
        if (nowElapsedMs - firstTime >= COMPARISON_WINDOW_MS) return SpeedComparisonStatus.EXPIRED
        return SpeedComparisonStatus.READY
    }
    private fun validSpeed(value: Double?): Double? = value?.takeIf { it.isFinite() && it > 0.0 }

    fun hints(result: SpeedResult): List<SpeedHint> {
        if (result.status != SpeedStatus.COMPLETED) return emptyList()
        val download = validSpeed(result.downloadMbps)
        val upload = validSpeed(result.uploadMbps)
        val hints = mutableListOf<SpeedHint>()
        if (download == null || upload == null) hints += SpeedHint.INCOMPLETE
        if (download != null && download < 2.0) hints += SpeedHint.SLOW_DOWNLOAD
        if (upload != null && upload < 1.0) hints += SpeedHint.SLOW_UPLOAD
        val latency = result.downloadLatency
        val idle = latency?.idleMedianMs?.takeIf { it >= 0 && latency.idleSuccesses >= 3 }
        val loaded = latency?.loadedMedianMs?.takeIf { it >= 0 && latency.loadedSuccesses >= 3 }
        if (idle != null && idle > 150) hints += SpeedHint.HIGH_IDLE_TCP
        if (idle != null && loaded != null && loaded.toLong() - idle.toLong() > 100) hints += SpeedHint.LOADED_TCP_INCREASE
        val uploadLatency = result.uploadLatency
        val uploadIdle = uploadLatency?.idleMedianMs?.takeIf { it >= 0 && uploadLatency.idleSuccesses >= 3 }
        val uploadLoaded = uploadLatency?.loadedMedianMs?.takeIf { it >= 0 && uploadLatency.loadedSuccesses >= 3 }
        if (upload != null && uploadIdle != null && uploadLoaded != null && uploadLoaded.toLong() - uploadIdle.toLong() > 100)
            hints += SpeedHint.UPLOAD_LOADED_TCP_INCREASE
        if (hints.isEmpty()) hints += SpeedHint.NO_STRONG_INDICATION
        return hints
    }

    /** Missing directions stay missing, never zero. Incompatible tests are never compared. */
    fun difference(first: SpeedResult, second: SpeedResult, nowElapsedMs: Long): SpeedDifference? {
        if (comparisonStatus(first, second, nowElapsedMs) != SpeedComparisonStatus.READY) return null
        val aDown = validSpeed(first.downloadMbps)
        val bDown = validSpeed(second.downloadMbps)
        val aUp = validSpeed(first.uploadMbps)
        val bUp = validSpeed(second.uploadMbps)
        val deltaDown = if (aDown != null && bDown != null) bDown - aDown else null
        val deltaUp = if (aUp != null && bUp != null) bUp - aUp else null
        return if (deltaDown == null && deltaUp == null) null else SpeedDifference(deltaDown, deltaUp)
    }
}
