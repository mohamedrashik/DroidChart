package com.droid.bootchart

import com.droid.bootchart.data.AndroidBootchartParser
import com.droid.bootchart.domain.BootchartImportException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

internal const val TEST_HEADER = "version = Android init 0.8\ntitle = Android boot"

internal fun stat(pid: Int, name: String, start: Long, parent: Int = 1): String {
    val fields = MutableList(20) { "0" }
    fields[0] = "S"
    fields[1] = parent.toString()
    fields[19] = start.toString()
    return "$pid ($name) ${fields.joinToString(" ")}"
}

class AndroidBootchartParserTest {
    @Test
    fun parsesAndroidSampleTimesAndParenthesizedNamesWithoutGuessingClockRate() = runTest {
        val report = AndroidBootchartParser().parse(
            "bootchart.zip", TEST_HEADER,
            "100\n${stat(42, "app (worker)", 9000)}\n\n120\n${stat(42, "app (worker)", 9000, 2)}\n",
        )
        val process = report.processes.single()
        assertEquals("app (worker)", process.name)
        assertEquals(9000, process.startTicks)
        assertEquals(1.0, process.firstSeenSeconds)
        assertEquals(0.2, process.observedSpanSeconds)
        assertEquals(2, process.parentPid)
        assertEquals(2, process.samples)
        assertEquals(2, report.sampleCount)
    }

    @Test
    fun separatesReusedPidsAndReportsSingleObservationsAsZeroSpan() = runTest {
        val report = AndroidBootchartParser().parse(
            "capture.zip", TEST_HEADER,
            "100\n${stat(42, "old", 50)}\n\n120\n${stat(42, "new", 110)}",
        )
        assertEquals(listOf("old", "new"), report.processes.map { it.name })
        assertEquals(listOf(0.0, 0.0), report.processes.map { it.observedSpanSeconds })
    }

    @Test
    fun rejectsMalformedRecordsAndNonIncreasingTimestamps() = runTest {
        for (log in listOf(
            stat(42, "missing timestamp", 1),
            "100\n42 (truncated) S 1",
            "100\n${stat(42, "duplicate", 1)}\n${stat(42, "duplicate", 1)}",
            "100\n\n99",
            "-1",
        )) {
            assertFailsWith<BootchartImportException> {
                AndroidBootchartParser().parse("bad.zip", TEST_HEADER, log)
            }
        }
    }

    @Test
    fun distinguishesEmptySamplesFromMissingSamplesAndRejectsOtherFormats() = runTest {
        assertEquals(0, AndroidBootchartParser().parse("empty.zip", TEST_HEADER, "100\n\n120").processes.size)
        assertFailsWith<BootchartImportException> {
            AndroidBootchartParser().parse("empty.zip", TEST_HEADER, "")
        }
        assertFailsWith<BootchartImportException> {
            AndroidBootchartParser().parse("other.zip", "version = unknown", "100")
        }
    }
}
