package com.droid.bootchart.data

import com.droid.bootchart.domain.BootchartImportException
import com.droid.bootchart.domain.BootchartProcess
import com.droid.bootchart.domain.BootchartReport
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/**
 * Parser for standard AOSP Android bootchart captures (`header` and `proc_ps.log`).
 *
 * Designed to conform to the Android `init` bootchart format (specifically version `Android init 0.8`):
 * - Timestamps in `proc_ps.log` represent system uptime measured in 1/100 of a second.
 * - Each sample block contains a timestamp line followed by `/proc/[pid]/stat` lines.
 * - Command lines in parentheses can contain spaces or nested parentheses; parsed between the
 *   first '(' and last ')'.
 * - Start ticks from field 20 (index 19 after closing parenthesis) establish process identity
 *   alongside PID to guard against PID reuse.
 * - The earliest sample naming exactly `com.android.systemui` supplies the user-selected
 *   boot-completion estimate, not a verified Android completion event.
 *
 * @throws BootchartImportException if the header is unsupported, logs are truncated, or fields are malformed.
 */
internal class AndroidBootchartParser {
    /**
     * Parses the log files from a bootchart archive into a validated [BootchartReport].
     *
     * @param fileName Originating archive filename.
     * @param header Content of `/data/bootchart/header`. Must declare `version = Android init 0.8`.
     * @param processLog Content of `/data/bootchart/proc_ps.log`.
     * @param statLog Optional content of `/data/bootchart/proc_stat.log` for CPU telemetry.
     * @param diskLog Optional content of `/data/bootchart/proc_diskstats.log` for disk telemetry.
     * @return Fully populated [BootchartReport] with parsed processes and telemetry series.
     * @throws BootchartImportException on unsupported versions, malformed lines, or if process records exceed safety limits.
     */
    suspend fun parse(
        fileName: String,
        header: String,
        processLog: String,
        statLog: String = "",
        diskLog: String = "",
    ): BootchartReport {
        val metadata = header.lineSequence().filter { '=' in it }.associate {
            it.substringBefore('=').trim() to it.substringAfter('=').trim()
        }
        if (metadata["version"] != "Android init 0.8") {
            throw BootchartImportException("Unsupported capture. Expected an Android init 0.8 bootchart header.")
        }
        val processes = linkedMapOf<Pair<Int, Long>, Observation>()
        var timestamp: Long? = null
        var sampleCount = 0
        var records = 0
        var estimatedBootCompletionSeconds: Double? = null
        val pidsInSample = mutableSetOf<Int>()
        processLog.lineSequence().forEachIndexed { index, raw ->
            if (index % 512 == 0) {
                currentCoroutineContext().ensureActive()
                yield()
            }
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed
            val sample = line.toLongOrNull()
            if (sample != null) {
                if (sample < 0 || (timestamp != null && sample <= timestamp)) {
                    invalid(index, "sample timestamps must be non-negative and increasing")
                }
                timestamp = sample
                sampleCount++
                pidsInSample.clear()
                return@forEachIndexed
            }
            val time = timestamp ?: invalid(index, "missing sample timestamp")
            val open = line.indexOf('(')
            val close = line.lastIndexOf(')')
            if (open <= 0 || close <= open) invalid(index, "invalid process record")
            val pid = line.substring(0, open).trim().toIntOrNull()
                ?.takeIf { it > 0 } ?: invalid(index, "invalid PID")
            if (!pidsInSample.add(pid)) invalid(index, "duplicate PID in a sample")
            val fields = line.substring(close + 1).trim().split(Regex("\\s+"))
            if (fields.size < 20) invalid(index, "truncated /proc stat record")
            val parent = fields[1].toIntOrNull()?.takeIf { it >= 0 }
                ?: invalid(index, "invalid parent PID")
            val start = fields[19].toLongOrNull()?.takeIf { it >= 0 }
                ?: invalid(index, "invalid process start ticks")
            if (fields[0].length != 1) invalid(index, "invalid process state")
            val name = line.substring(open + 1, close)
            if (name.isBlank()) invalid(index, "missing process name")
            if (++records > 1_000_000) {
                throw BootchartImportException("Capture exceeds the limit of 1,000,000 process records.")
            }
            if (estimatedBootCompletionSeconds == null && name == "com.android.systemui") {
                estimatedBootCompletionSeconds = time / 100.0
            }
            val key = pid to start
            val existing = processes[key]
            processes[key] = Observation(
                pid, start, parent, name, fields[0],
                existing?.first ?: time, time, (existing?.samples ?: 0) + 1,
                (fields[11].toLongOrNull() ?: 0L) + (fields[12].toLongOrNull() ?: 0L),
            )
        }
        if (sampleCount == 0) throw BootchartImportException("proc_ps.log contains no samples.")
        return BootchartReport(
            fileName, metadata["title"]?.takeIf { it.isNotBlank() } ?: fileName,
            processes.values.map {
                BootchartProcess(
                    it.pid, it.start, it.parent, it.name, it.state,
                    it.first / 100.0, (it.last - it.first) / 100.0, it.samples, it.cpu,
                )
            }.sortedWith(compareBy({ it.firstSeenSeconds }, { it.pid }, { it.startTicks })),
            sampleCount,
            metadata,
            TelemetryParser.cpu(statLog),
            TelemetryParser.disk(diskLog),
            estimatedBootCompletionSeconds = estimatedBootCompletionSeconds,
        )
    }

    private fun invalid(line: Int, reason: String): Nothing =
        throw BootchartImportException("Invalid proc_ps.log at line ${line + 1}: $reason.")

    private data class Observation(
        val pid: Int,
        val start: Long,
        val parent: Int,
        val name: String,
        val state: String,
        val first: Long,
        val last: Long,
        val samples: Int,
        val cpu: Long,
    )
}
