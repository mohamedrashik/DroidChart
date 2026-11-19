package com.droid.bootchart

import com.droid.bootchart.data.TelemetryParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelemetryParserTest {
    @Test
    fun cpuUsesCounterDeltasAndTreatsIowaitAsIdle() {
        // user nice system idle iowait: delta total 100, idle 40 + iowait 10
        val log = "100\ncpu  100 0 0 100 0 0 0 0\n200\ncpu  150 0 0 140 10 0 0 0\n"
        val sample = TelemetryParser.cpu(log).single()
        assertEquals(2.0, sample.timeSeconds)
        assertEquals(0.5, sample.busyFraction, 1e-9)
        assertEquals(0.1, sample.iowaitFraction, 1e-9)
    }

    @Test
    fun cpuSkipsResetsAndMalformedBlocks() {
        val log = "100\ncpu  100 0 0 100 0\n200\ncpu  garbage\n300\ncpu  10 0 0 10 0\n"
        assertTrue(TelemetryParser.cpu(log).isEmpty())
    }

    @Test
    fun diskSumsWholeDevicesOnlyAndConvertsSectorsToKib() {
        fun line(name: String, read: Long, write: Long) = "179 0 $name 0 0 $read 0 0 0 $write 0 0 0 0"
        val log = "100\n${line("mmcblk0", 0, 0)}\n${line("mmcblk0p1", 0, 0)}\n" +
            "200\n${line("mmcblk0", 2000, 1000)}\n${line("mmcblk0p1", 9999, 9999)}\n"
        val sample = TelemetryParser.disk(log).single()
        assertEquals(1000.0, sample.readKibPerSecond, 1e-9)
        assertEquals(500.0, sample.writeKibPerSecond, 1e-9)
    }

    @Test
    fun missingLogsYieldNoSeries() {
        assertTrue(TelemetryParser.cpu("").isEmpty())
        assertTrue(TelemetryParser.disk("").isEmpty())
    }
}
