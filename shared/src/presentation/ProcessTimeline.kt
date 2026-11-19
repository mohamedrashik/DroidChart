package com.droid.bootchart.presentation

import com.droid.bootchart.domain.BootchartProcess
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** Display-only categories inferred from process names and parentage, not scheduler evidence. */
internal enum class ProcessCategory(val label: String) {
    Kernel("Kernel"),
    Native("Native Daemons"),
    Framework("Framework"),
    Apps("Apps"),
    Other("Other"),
}

private val frameworkCommands = setOf("system_server", "surfaceflinger", "com.android.systemui")
private val nativeCommands = setOf(
    "init", "ueventd", "vold", "servicemanager", "hwservicemanager", "vndservicemanager",
    "bootanimation", "logd", "adbd", "lmkd", "netd", "installd", "apexd", "statsd",
    "audioserver", "cameraserver", "mediaserver", "keystore2", "tombstoned", "traced",
)

/**
 * Name/parent heuristics for the reference design's category filters.
 * Unrecognized names stay in Other (visible in All), rather than fabricating a classification.
 */
internal fun processCategory(process: BootchartProcess): ProcessCategory {
    val rawCommand = process.name.substringBefore(' ').lowercase()
    val command = rawCommand.substringAfterLast('/')
    return when {
        process.pid == 2 || process.parentPid == 2 || rawCommand.startsWith("kworker/") ||
            rawCommand.startsWith("swapper/") || command == "kthreadd" -> ProcessCategory.Kernel
        command in frameworkCommands ||
            command.startsWith("zygote") || command.startsWith("app_process") -> ProcessCategory.Framework
        '.' in command && '/' !in process.name.substringBefore(' ') &&
            !command.startsWith("android.hardware.") -> ProcessCategory.Apps
        command in nativeCommands || process.name.startsWith("/system/") || process.name.startsWith("/vendor/") ||
            process.name.startsWith("/apex/") || command.startsWith("android.hardware.") ||
            command.startsWith("dex2oat") -> ProcessCategory.Native
        else -> ProcessCategory.Other
    }
}

/** End of the observed bar; not an exit timestamp. */
internal val BootchartProcess.barEndSeconds: Double
    get() = firstSeenSeconds + observedSpanSeconds

/** Estimated start to last observation, using the same bounds as the displayed bar. */
internal val BootchartProcess.barDurationSeconds: Double
    get() = (barEndSeconds - startSeconds).coerceAtLeast(0.0)

/** Intersects query, category and strict duration threshold, retaining PID+start-tick identity. */
internal fun filterProcesses(
    processes: List<BootchartProcess>,
    query: String,
    category: ProcessCategory?,
    minimumSeconds: Double,
): List<BootchartProcess> {
    val text = query.trim()
    return processes.filter {
        (category == null || processCategory(it) == category) &&
            (minimumSeconds == 0.0 || it.barDurationSeconds - minimumSeconds > 1e-9) &&
            (text.isEmpty() || it.name.contains(text, ignoreCase = true) || it.pid.toString().contains(text))
    }
}

/**
 * Shared time-to-pixel linear coordinate transform.
 *
 * Scales timestamps into pixel coordinates for timeline canvas drawing.
 * Dynamically computes round tick positions based on available width and zoom.
 *
 * @property endSeconds Maximum capture duration in seconds.
 * @property widthPx Viewport width in physical canvas pixels.
 */
internal class TimeScale(
    private val endSeconds: Double,
    private val widthPx: Float,
) {
    /**
     * Converts a timestamp in seconds to a horizontal pixel offset.
     *
     * @param seconds System uptime timestamp in seconds.
     * @return X coordinate in pixels.
     */
    fun x(seconds: Double): Float =
        if (endSeconds <= 0.0) 0f else (seconds / endSeconds * widthPx).toFloat()

    /**
     * Calculates rounded ruler tick intervals suitable for the current zoom and pixel width.
     *
     * @return Sorted list of timestamp markers in seconds.
     */
    fun ticks(): List<Double> {
        if (endSeconds <= 0.0 || widthPx <= 0f) return emptyList()
        val divisions = (widthPx / 100f).coerceIn(1f, 200f)
        val rawStep = (endSeconds / divisions).coerceAtLeast(0.01)
        val magnitude = 10.0.pow(floor(log10(rawStep)))
        val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * magnitude >= rawStep } * magnitude
        return (0..floor(endSeconds / step).toInt()).map { it * step }
    }
}

/** Two decimal places without relying on JVM-only number formatting. */
internal fun timelineSeconds(value: Double): String {
    val hundredths = (value * 100).roundToLong()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}

/** One DP-independent thumb geometry for the always-visible cross-platform timeline scrollbar. */
internal fun scrollbarThumb(viewport: Float, content: Float, offset: Float): Pair<Float, Float> {
    if (content <= viewport || viewport <= 0f) return 0f to viewport.coerceAtLeast(0f)
    val width = (viewport * viewport / content).coerceAtLeast(24f).coerceAtMost(viewport)
    val left = (offset / (content - viewport)).coerceIn(0f, 1f) * (viewport - width)
    return left to width
}
