package com.droid.bootchart.data

import com.droid.bootchart.domain.CpuSample
import com.droid.bootchart.domain.DiskSample

/**
 * Derives CPU and disk telemetry series from Android's `proc_stat.log` and `proc_diskstats.log`.
 *
 * Both logs are structured as recurring sample blocks consisting of a timestamp line
 * (representing uptime in 1/100 s) followed by raw `/proc` file dumps. Values in the logs
 * are cumulative counters; each metric is calculated as a delta against the previous block.
 *
 * Counter resets (e.g., system reboots or overflow) and malformed blocks are discarded
 * safely rather than fabricating or corrupting telemetry values.
 */
internal object TelemetryParser {
    private val wholeDisk = Regex("^(mmcblk\\d+|sd[a-z]+|vd[a-z]+|nvme\\d+n\\d+)$")
    private const val SECTOR_KIB = 0.5

    /**
     * Parses cumulative CPU ticks from `proc_stat.log` and computes interval utilization.
     *
     * In accordance with Linux CPU accounting standards:
     * - `total = user + nice + system + idle + iowait + irq + softirq`
     * - `busy = total - (idle + iowait)`
     * - `iowait` is accounted as idle time for overall busy fraction computation.
     *
     * @param log Full string content of `proc_stat.log`.
     * @return Ordered list of [CpuSample] containing busy and iowait load fractions.
     */
    fun cpu(log: String): List<CpuSample> {
        val out = mutableListOf<CpuSample>()
        var previous: LongArray? = null
        var previousTime = 0L
        blocks(log) { time, lines ->
            val fields = lines.firstOrNull { it.startsWith("cpu ") }
                ?.trim()?.split(Regex("\\s+"))?.drop(1)?.mapNotNull { it.toLongOrNull() }
                ?: return@blocks
            if (fields.size < 5) return@blocks
            val now = fields.toLongArray()
            val before = previous
            if (before != null && time > previousTime && before.size == now.size) {
                val total = now.sum() - before.sum()
                val idle = (now[3] - before[3]) + (now[4] - before[4])
                if (total > 0 && idle >= 0 && idle <= total) {
                    out += CpuSample(
                        time / 100.0,
                        (total - idle).toDouble() / total,
                        (now[4] - before[4]).toDouble().coerceAtLeast(0.0) / total,
                    )
                }
            }
            previous = now
            previousTime = time
        }
        return out
    }

    /**
     * Parses disk sector read/write counters from `proc_diskstats.log` into throughput rates in KiB/s.
     *
     * Filters strictly to whole block devices (e.g. `mmcblk0`, `sda`, `nvme0n1`), ignoring partition
     * entries (e.g. `mmcblk0p1`) to avoid double-counting sector operations.
     *
     * @param log Full string content of `proc_diskstats.log`.
     * @return Ordered list of [DiskSample] containing read and write KiB/s rates.
     */
    fun disk(log: String): List<DiskSample> {
        val out = mutableListOf<DiskSample>()
        var previous: Pair<Long, Long>? = null
        var previousTime = 0L
        blocks(log) { time, lines ->
            var read = 0L
            var write = 0L
            var found = false
            for (line in lines) {
                val f = line.trim().split(Regex("\\s+"))
                if (f.size < 10 || !wholeDisk.matches(f[2])) continue
                val r = f[5].toLongOrNull() ?: continue
                val w = f[9].toLongOrNull() ?: continue
                read += r
                write += w
                found = true
            }
            if (!found) return@blocks
            val before = previous
            val seconds = (time - previousTime) / 100.0
            if (before != null && seconds > 0 && read >= before.first && write >= before.second) {
                out += DiskSample(
                    time / 100.0,
                    (read - before.first) * SECTOR_KIB / seconds,
                    (write - before.second) * SECTOR_KIB / seconds,
                )
            }
            previous = read to write
            previousTime = time
        }
        return out
    }

    private inline fun blocks(log: String, block: (Long, List<String>) -> Unit) {
        var time: Long? = null
        val lines = mutableListOf<String>()
        for (raw in log.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val stamp = line.toLongOrNull()
            if (stamp != null) {
                time?.let { block(it, lines) }
                lines.clear()
                time = stamp
            } else if (time != null) {
                lines += line
            }
        }
        time?.let { block(it, lines) }
    }
}
