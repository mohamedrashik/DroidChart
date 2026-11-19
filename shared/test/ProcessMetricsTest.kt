package com.droid.bootchart

import com.droid.bootchart.data.AndroidBootchartParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ProcessMetricsTest {
    @Test
    fun bootCompletionUsesSystemUiObservationNotBirthOrCaptureEnd() = runTest {
        val report = AndroidBootchartParser().parse(
            "b.zip", TEST_HEADER,
            "1646\n${stat(1725, "com.android.systemui", 1633)}\n" +
                "3000\n${stat(1725, "com.android.systemui", 1633)}\n",
        )
        assertEquals(16.46, report.estimatedBootCompletionSeconds)
        assertEquals(30.0, report.captureEndSeconds)
    }

    @Test
    fun systemUiRestartsDoNotReplaceEarliestObservationAtZeroUptime() = runTest {
        val report = AndroidBootchartParser().parse(
            "b.zip", TEST_HEADER,
            "0\n${stat(1725, "com.android.systemui", 0)}\n" +
                "2500\n${stat(1725, "com.android.systemui", 2400)}\n",
        )
        assertEquals(0.0, report.estimatedBootCompletionSeconds)
    }

    @Test
    fun systemUiEstimateTracksTheObservedNameEvenWhenTheProcessIsRenamed() = runTest {
        val report = AndroidBootchartParser().parse(
            "b.zip", TEST_HEADER,
            "100\n${stat(1725, "zygote64", 50)}\n" +
                "200\n${stat(1725, "com.android.systemui", 50)}\n" +
                "300\n${stat(1725, "renamed", 50)}\n",
        )
        assertEquals(2.0, report.estimatedBootCompletionSeconds)
        assertEquals(1.0, report.processes.single().firstSeenSeconds)
        assertEquals("renamed", report.processes.single().name)
    }

    @Test
    fun absentSystemUiAndSimilarNamesDoNotImplyCompletion() = runTest {
        val report = AndroidBootchartParser().parse(
            "b.zip", TEST_HEADER,
            "100\n${stat(1, "init", 0)}\n" +
                "${stat(2, "com.android.systemui:worker", 0)}\n" +
                "${stat(3, "not.com.android.systemui", 0)}\n",
        )
        assertNull(report.estimatedBootCompletionSeconds)
        assertNull(AndroidBootchartParser().parse("empty.zip", TEST_HEADER, "100\n").estimatedBootCompletionSeconds)
    }

    private fun statWithCpu(pid: Int, start: Long, utime: Long, stime: Long): String {
        val fields = MutableList(20) { "0" }
        fields[0] = "S"
        fields[1] = "1"
        fields[11] = utime.toString()
        fields[12] = stime.toString()
        fields[19] = start.toString()
        return "$pid (worker) ${fields.joinToString(" ")}"
    }

    @Test
    fun cpuTicksAreUserPlusSystemAtLastSample() = runTest {
        val report = AndroidBootchartParser().parse(
            "b.zip", TEST_HEADER,
            "100\n${statWithCpu(7, 50, 10, 5)}\n200\n${statWithCpu(7, 50, 30, 20)}\n",
        )
        val process = report.processes.single()
        assertEquals(50, process.cpuTicks)
        assertEquals(0.5, process.cpuSeconds)
    }

    @Test
    fun startSecondsUsesProcTicksButNeverExceedsFirstSeen() = runTest {
        val early = AndroidBootchartParser().parse("b.zip", TEST_HEADER, "1000\n${stat(1, "init", 50)}\n").processes.single()
        assertEquals(0.5, early.startSeconds)
        val late = AndroidBootchartParser().parse("b.zip", TEST_HEADER, "100\n${stat(2, "x", 99999)}\n").processes.single()
        assertEquals(1.0, late.startSeconds)
    }

    @Test
    fun largeCaptureParsesAllProcesses() = runTest {
        val log = buildString {
            for (sample in 1..4) {
                append(sample * 20).append('\n')
                for (pid in 1..5_000) append(stat(pid, "proc$pid", pid.toLong())).append('\n')
            }
        }
        val report = AndroidBootchartParser().parse("big.zip", TEST_HEADER, log)
        assertEquals(5_000, report.processes.size)
        assertTrue(report.processes.all { it.samples == 4 })
    }
}
