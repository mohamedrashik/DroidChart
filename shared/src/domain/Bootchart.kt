package com.droid.bootchart.domain

/**
 * A snapshot-aggregated process representation identified uniquely by [pid] and raw [startTicks].
 *
 * In Linux/Android `/proc`, PIDs can be recycled over long captures; pairing [pid] with
 * the process start time ([startTicks] from `/proc/[pid]/stat` field 22) maintains distinct
 * identity across PID reuse.
 *
 * Note: Observed spans represent the duration between the first and last observation samples
 * in `proc_ps.log`, not verified kernel lifespans or process execution lifecycles.
 *
 * @property pid Linux Process ID (must be positive).
 * @property startTicks Process start time in kernel jiffies from `/proc/[pid]/stat`.
 * @property parentPid Parent process ID (PPID) recorded at the last observation.
 * @property name Process executable or command line name extracted from `/proc/[pid]/stat`.
 * @property state Last observed process state character (e.g. 'R', 'S', 'D', 'Z').
 * @property firstSeenSeconds Uptime in seconds at which the process was first observed in the capture.
 * @property observedSpanSeconds Elapsed seconds between first and last observations in the capture.
 * @property samples Total count of sample periods in which this process was present.
 * @property cpuTicks Cumulative user (`utime`) and system (`stime`) CPU ticks recorded at the last sample.
 */
data class BootchartProcess(
    val pid: Int,
    val startTicks: Long,
    val parentPid: Int,
    val name: String,
    val state: String,
    val firstSeenSeconds: Double,
    val observedSpanSeconds: Double,
    val samples: Int,
    val cpuTicks: Long = 0,
) {
    /** Cumulative user+system CPU seconds at the last sample (assumes USER_HZ of 100). */
    val cpuSeconds: Double
        get() = cpuTicks / 100.0

    /**
     * Approximate birth time in seconds since boot derived from [startTicks] (assuming standard
     * Android `USER_HZ` of 100 ticks per second), clamped to [0.0, [firstSeenSeconds]].
     */
    val startSeconds: Double
        get() = (startTicks / 100.0).coerceIn(0.0, firstSeenSeconds)
}

/**
 * A normalized, locally imported Android bootchart capture report.
 *
 * Contains extracted metadata from the capture header, observed process timeline records,
 * and aggregated CPU and disk I/O metrics when present.
 *
 * @property fileName Originating archive or source file name.
 * @property title Human-readable capture title extracted from header or fallback file name.
 * @property processes List of all observed processes, ordered chronologically by [BootchartProcess.firstSeenSeconds].
 * @property sampleCount Total number of observation sample intervals in `proc_ps.log`.
 * @property metadata Key-value map parsed from `header` (e.g., `version`, `system.uname`, `system.cpu`).
 * @property cpu Calculated CPU utilization delta series parsed from `proc_stat.log`.
 * @property disk Calculated disk I/O delta throughput series parsed from `proc_diskstats.log`.
 */
data class BootchartReport(
    val fileName: String,
    val title: String,
    val processes: List<BootchartProcess>,
    val sampleCount: Int,
    val metadata: Map<String, String> = emptyMap(),
    val cpu: List<CpuSample> = emptyList(),
    val disk: List<DiskSample> = emptyList(),
    /**
     * User-selected boot-completion estimate: earliest observation of the exact
     * `com.android.systemui` process in `proc_ps.log`, in seconds since boot.
     *
     * This is a sampling-based proxy, not an Android `BOOT_COMPLETED` event or a
     * process birth timestamp. Restarts do not replace the earliest observation.
     * Returns `null` when SystemUI is not observed; this does not imply boot failure.
     */
    val estimatedBootCompletionSeconds: Double? = null,
) {
    /**
     * Last observed sample timestamp in seconds since boot across processes and telemetry series.
     * Note: This represents capture termination, NOT `BOOT_COMPLETED`.
     */
    val captureEndSeconds: Double
        get() = maxOf(
            processes.maxOfOrNull { it.firstSeenSeconds + it.observedSpanSeconds } ?: 0.0,
            cpu.lastOrNull()?.timeSeconds ?: 0.0,
            disk.lastOrNull()?.timeSeconds ?: 0.0,
        )
}

/**
 * CPU utilization metrics for an interval ending at [timeSeconds].
 *
 * @property timeSeconds Interval endpoint timestamp in seconds since system boot.
 * @property busyFraction Aggregate non-idle CPU load fraction in range `0.0..1.0`.
 * @property iowaitFraction IO wait fraction in range `0.0..1.0` (treated as idle in total load calculation).
 */
data class CpuSample(val timeSeconds: Double, val busyFraction: Double, val iowaitFraction: Double)

/**
 * Disk throughput metrics for whole block devices over an interval ending at [timeSeconds].
 *
 * @property timeSeconds Interval endpoint timestamp in seconds since system boot.
 * @property readKibPerSecond Aggregate read throughput in KiB/s based on 512-byte sectors read.
 * @property writeKibPerSecond Aggregate write throughput in KiB/s based on 512-byte sectors written.
 */
data class DiskSample(val timeSeconds: Double, val readKibPerSecond: Double, val writeKibPerSecond: Double)

/**
 * Represents an actionable format, validation, or extraction error encountered during bootchart import.
 *
 * Messages are sanitized to ensure they can be safely rendered in the UI without exposing
 * sensitive or unformatted raw log content.
 *
 * @param message Human-readable error description explaining the parsing or validation failure.
 */
class BootchartImportException(message: String) : Exception(message)

/**
 * Boundary contract for acquiring and importing a local bootchart capture.
 */
interface BootchartRepository {
    /**
     * Prompts for and imports a local bootchart archive.
     *
     * @return The parsed [BootchartReport], or `null` if the user aborted or cancelled file selection.
     * @throws BootchartImportException if the archive is corrupted, oversized, or fails validation.
     */
    suspend fun import(): BootchartReport?
}

/**
 * Use case to import and validate a bootchart capture without platform file system or UI coupling.
 *
 * @property repository Concrete [BootchartRepository] implementation providing capture archives.
 */
class ImportBootchart(private val repository: BootchartRepository) {
    /**
     * Executes bootchart import.
     *
     * @return Parsed [BootchartReport], or `null` if cancelled by user.
     * @throws BootchartImportException on malformed data or unsupported capture format.
     */
    suspend operator fun invoke(): BootchartReport? = repository.import()
}
