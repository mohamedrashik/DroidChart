package com.droid.bootchart

import com.droid.bootchart.domain.BootchartProcess
import com.droid.bootchart.presentation.ProcessCategory
import com.droid.bootchart.presentation.TimeScale
import com.droid.bootchart.presentation.filterProcesses
import com.droid.bootchart.presentation.processCategory
import com.droid.bootchart.presentation.scrollbarThumb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessTimelineTest {
    private fun process(
        name: String,
        pid: Int = 42,
        parent: Int = 1,
        startTicks: Long = 100,
        first: Double = 1.0,
        span: Double = 1.0,
    ) = BootchartProcess(pid, startTicks, parent, name, "S", first, span, 2)

    @Test
    fun presetsUseNamesAndParentageWithoutCallingEveryUnknownProcessAnApp() {
        assertEquals(ProcessCategory.Kernel, processCategory(process("kworker/0:1", parent = 2)))
        assertEquals(ProcessCategory.Native, processCategory(process("/system/bin/vold")))
        assertEquals(ProcessCategory.Native, processCategory(process("/vendor/bin/hw/vendor.example.camera-service")))
        assertEquals(ProcessCategory.Framework, processCategory(process("/system/bin/zygote64")))
        assertEquals(ProcessCategory.Framework, processCategory(process("com.android.systemui")))
        assertEquals(ProcessCategory.Apps, processCategory(process("com.example.viewer:worker")))
        assertEquals(ProcessCategory.Other, processCategory(process("unknown")))
    }

    @Test
    fun queryCategoryAndThresholdAreCombinedAndClearingQueryPreservesCategory() {
        val native = process("/system/bin/vold", pid = 123)
        val framework = process("zygote64", pid = 124)
        val short = process("/system/bin/vold", pid = 125, span = 0.05)
        val processes = listOf(native, framework, short)
        assertEquals(listOf(native), filterProcesses(processes, "VOLD", ProcessCategory.Native, 0.2))
        assertEquals(listOf(native), filterProcesses(processes, "", ProcessCategory.Native, 0.2))
        assertEquals(listOf(native), filterProcesses(processes, "23", null, 0.0))
        assertEquals(processes, filterProcesses(processes, "", null, 0.0))
    }

    @Test
    fun strictThresholdMatchesBarBoundsAndAvoidsFloatingPointBoundaryErrors() {
        val exact = process("vold", first = 1.0, span = 0.2)
        val longer = process("vold", first = 1.0, span = 0.21)
        val bornEarlier = process("init", startTicks = 0, first = 1.0, span = 0.0)
        assertEquals(listOf(longer), filterProcesses(listOf(exact, longer), "", null, 0.2))
        assertEquals(listOf(bornEarlier), filterProcesses(listOf(bornEarlier), "", null, 0.2))
    }

    @Test
    fun filtersDoNotMergeReusedPids() {
        val first = process("app", pid = 50)
        val reused = process("app", pid = 50, startTicks = 300, first = 3.0)
        assertEquals(listOf(first, reused), filterProcesses(listOf(first, reused), "50", null, 0.0))
    }

    @Test
    fun scaleAlignsBarsWithRulerAtEveryZoomAndBoundsTicksForLongCaptures() {
        assertEquals(250f, TimeScale(20.0, 1000f).x(5.0))
        assertEquals(500f, TimeScale(20.0, 2000f).x(5.0))
        assertEquals(0f, TimeScale(0.0, 1000f).x(5.0))
        val ticks = TimeScale(100_000.0, 20_000f).ticks()
        assertTrue(ticks.size <= 201)
        assertTrue(ticks.zipWithNext().all { (a, b) -> b > a })
        assertTrue(ticks.all { it in 0.0..100_000.0 })
    }

    @Test
    fun scrollbarReachesBothEndsWithoutOvershooting() {
        assertEquals(0f to 250f, scrollbarThumb(500f, 1000f, 0f))
        assertEquals(250f to 250f, scrollbarThumb(500f, 1000f, 500f))
        assertEquals(250f to 250f, scrollbarThumb(500f, 1000f, 1000f))
        assertEquals(0f to 500f, scrollbarThumb(500f, 500f, 0f))
    }
}
