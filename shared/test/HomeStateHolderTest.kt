package com.droid.bootchart

import com.droid.bootchart.domain.BootchartImportException
import com.droid.bootchart.domain.BootchartReport
import com.droid.bootchart.domain.BootchartRepository
import com.droid.bootchart.domain.ImportBootchart
import com.droid.bootchart.presentation.HomeStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeStateHolderTest {
    @Test
    fun cancelledOrFailedReplacementPreservesTheCurrentReportAndAllowsRetry() = runTest {
        val report = BootchartReport("bootchart.zip", "Boot", emptyList(), 1)
        var call = 0
        val holder = HomeStateHolder(ImportBootchart(object : BootchartRepository {
            override suspend fun import(): BootchartReport? = when (call++) {
                0, 3 -> report
                1 -> null
                else -> throw BootchartImportException("Invalid capture.")
            }
        }))
        holder.import()
        holder.import()
        assertEquals(report, holder.state.report)
        assertNull(holder.state.error)
        holder.import()
        assertEquals(report, holder.state.report)
        assertEquals("Invalid capture.", holder.state.error)
        assertFalse(holder.state.importing)
        holder.import()
        assertNull(holder.state.error)
    }

    @Test
    fun preventsConcurrentImportsAndClearsLoadingOnCancellation() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val holder = HomeStateHolder(ImportBootchart(object : BootchartRepository {
            override suspend fun import(): BootchartReport? {
                calls++
                gate.await()
                return null
            }
        }))
        val job = launch { holder.import() }
        runCurrent()
        assertTrue(holder.state.importing)
        holder.import()
        assertEquals(1, calls)
        job.cancel()
        job.join()
        assertFalse(holder.state.importing)
        assertNull(holder.state.error)
    }
}
