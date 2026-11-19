package com.droid.bootchart

import com.droid.bootchart.data.BootchartRepositoryImpl
import com.droid.bootchart.data.BootchartSource
import com.droid.bootchart.data.BootchartZipReader
import com.droid.bootchart.data.SelectedArchive
import com.droid.bootchart.domain.BootchartImportException
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class BootchartImportTest {
    @Test
    fun importsStoredAndDeflatedNestedCapturesEndToEnd() = runTest {
        for (stored in listOf(false, true)) {
            val archive = zip(
                mapOf("bootchart/header" to TEST_HEADER, "bootchart/proc_ps.log" to "125\n${stat(7, "init", 1)}"),
                stored,
            )
            val repository = BootchartRepositoryImpl(object : BootchartSource {
                override suspend fun select() = SelectedArchive("bootchart.zip", archive)
            })
            val report = repository.import()!!
            assertEquals("Android boot", report.title)
            assertEquals(7, report.processes.single().pid)
            assertEquals(1.25, report.processes.single().firstSeenSeconds)
        }
    }

    @Test
    fun rejectsMissingAmbiguousUnsafeAndTruncatedArchives() = runTest {
        val invalid = listOf(
            byteArrayOf(1, 2, 3),
            zip(mapOf("proc_ps.log" to "100")),
            zip(mapOf("header" to TEST_HEADER, "proc_ps.log" to "100", "other/proc_ps.log" to "100")),
            zip(mapOf("../header" to TEST_HEADER, "proc_ps.log" to "100")),
            zip(mapOf("header" to TEST_HEADER, "proc_ps.log" to "100")).dropLast(5).toByteArray(),
        )
        for (archive in invalid) {
            assertFailsWith<BootchartImportException> { BootchartZipReader().read(archive) }
        }
    }

    @Test
    fun rejectsDamagedStoredEntryAndOversizedDeclaredExpansion() = runTest {
        val good = zip(mapOf("header" to TEST_HEADER, "proc_ps.log" to "100"), stored = true)
        val damaged = good.copyOf()
        // The first local header is 30 bytes followed by the name "header".
        damaged[36] = (damaged[36].toInt() xor 1).toByte()
        assertFailsWith<BootchartImportException> { BootchartZipReader().read(damaged) }
        val oversized = good.copyOf()
        val directory = oversized.indices.first {
            it + 3 < oversized.size && oversized[it] == 0x50.toByte() &&
                oversized[it + 1] == 0x4b.toByte() && oversized[it + 2] == 1.toByte() &&
                oversized[it + 3] == 2.toByte()
        }
        // Central-directory uncompressed size: 0x7f000000, without allocating it.
        oversized[directory + 27] = 0x7f
        assertFailsWith<BootchartImportException> { BootchartZipReader().read(oversized) }
    }

    private fun zip(files: Map<String, String>, stored: Boolean = false): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, text) in files) {
                val bytes = text.toByteArray()
                zip.putNextEntry(ZipEntry(name).apply {
                    if (stored) {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        crc = CRC32().apply { update(bytes) }.value
                    }
                })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
