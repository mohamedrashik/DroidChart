package com.droid.bootchart.data

import com.droid.bootchart.domain.BootchartImportException
import java.awt.HeadlessException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

internal actual fun createBootchartSource(): BootchartSource = DesktopBootchartSource()

private class DesktopBootchartSource : BootchartSource {
    override suspend fun select(): SelectedArchive? {
        val file = withContext(Dispatchers.Swing) {
            try {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Import Android bootchart"
                    fileFilter = FileNameExtensionFilter("Bootchart ZIP archive", "zip")
                    isAcceptAllFileFilterUsed = false
                }
                when (chooser.showOpenDialog(null)) {
                    JFileChooser.APPROVE_OPTION -> chooser.selectedFile
                    JFileChooser.CANCEL_OPTION -> null
                    else -> throw BootchartImportException("The file picker could not open. Please try again.")
                }
            } catch (_: HeadlessException) {
                throw BootchartImportException("File selection requires a desktop display.")
            }
        } ?: return null
        return withContext(Dispatchers.IO) {
            try {
                if (file.length() > MAX_ARCHIVE_BYTES) {
                    throw BootchartImportException("ZIP exceeds the 32 MiB import limit.")
                }
                val bytes = file.inputStream().use { it.readNBytes(MAX_ARCHIVE_BYTES + 1) }
                if (bytes.size > MAX_ARCHIVE_BYTES) {
                    throw BootchartImportException("ZIP exceeds the 32 MiB import limit.")
                }
                SelectedArchive(file.name, bytes)
            } catch (_: IOException) {
                throw BootchartImportException("Cannot read the selected file. Check its permissions and try again.")
            } catch (_: SecurityException) {
                throw BootchartImportException("Permission denied when reading the selected file.")
            }
        }
    }
}

internal actual suspend fun inflateZipEntry(bytes: ByteArray, expectedSize: Int): ByteArray {
    val inflater = Inflater(true)
    try {
        inflater.setInput(bytes)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            currentCoroutineContext().ensureActive()
            val count = inflater.inflate(buffer)
            if (output.size().toLong() + count > expectedSize) {
                throw BootchartImportException("ZIP entry expands beyond its declared size.")
            }
            output.write(buffer, 0, count)
            if (count == 0 && !inflater.finished()) {
                throw BootchartImportException("ZIP contains truncated or unsupported deflate data.")
            }
        }
        if (inflater.remaining != 0) throw BootchartImportException("ZIP contains trailing deflate data.")
        return output.toByteArray()
    } catch (_: DataFormatException) {
        throw BootchartImportException("ZIP contains damaged deflate data.")
    } finally {
        inflater.end()
    }
}
