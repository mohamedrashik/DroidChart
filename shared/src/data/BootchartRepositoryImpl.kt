package com.droid.bootchart.data

import com.droid.bootchart.domain.BootchartReport
import com.droid.bootchart.domain.BootchartRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024
internal const val MAX_EXPANDED_BYTES = 64 * 1024 * 1024

internal data class SelectedArchive(val name: String, val bytes: ByteArray)

internal interface BootchartSource {
    suspend fun select(): SelectedArchive?
}

internal expect fun createBootchartSource(): BootchartSource

internal expect suspend fun inflateZipEntry(bytes: ByteArray, expectedSize: Int): ByteArray

internal class BootchartRepositoryImpl(
    private val source: BootchartSource,
    private val parser: AndroidBootchartParser = AndroidBootchartParser(),
) : BootchartRepository {
    override suspend fun import(): BootchartReport? {
        val selected = source.select() ?: return null
        return withContext(Dispatchers.Default) {
            val logs = BootchartZipReader().read(selected.bytes)
            parser.parse(selected.name, logs.header, logs.processLog, logs.statLog, logs.diskLog)
        }
    }
}
