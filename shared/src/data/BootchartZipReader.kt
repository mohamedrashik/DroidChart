package com.droid.bootchart.data

import com.droid.bootchart.domain.BootchartImportException
import kotlinx.coroutines.yield

/**
 * Log bundle extracted from an in-memory Android bootchart archive.
 *
 * @property header Content of the `header` file.
 * @property processLog Content of `proc_ps.log`.
 * @property statLog Content of `proc_stat.log`, or empty string if absent.
 * @property diskLog Content of `proc_diskstats.log`, or empty string if absent.
 */
internal data class BootchartLogs(
    val header: String,
    val processLog: String,
    val statLog: String = "",
    val diskLog: String = "",
)

/**
 * In-memory reader for standard `.zip` archives containing Android bootchart logs.
 *
 * Validates CRC32 checksums, entry counts, directory boundaries, and decompressed sizes
 * to protect against ZIP bombs and malicious paths (ZIP path traversal) without extracting
 * files to disk.
 */
internal class BootchartZipReader {
    /**
     * Reads and inflates the bootchart log entries from [bytes].
     *
     * @param bytes Raw binary content of the ZIP archive.
     * @return Extracted [BootchartLogs] containing UTF-8 decoded log files.
     * @throws BootchartImportException if the archive is malformed, oversized, or missing required files.
     */
    suspend fun read(bytes: ByteArray): BootchartLogs {
        if (bytes.size > MAX_ARCHIVE_BYTES) fail("ZIP exceeds the 32 MiB import limit.")
        val zip = ZipBytes(bytes)
        val end = (bytes.size - 22 downTo maxOf(0, bytes.size - 65557)).firstOrNull {
            zip.u32(it) == 0x06054b50L && it + 22 + zip.u16(it + 20) == bytes.size
        } ?: fail("Not a valid ZIP archive.")
        val count = zip.u16(end + 10)
        val directorySize = zip.u32(end + 12)
        val directoryOffset = zip.u32(end + 16)
        if (zip.u16(end + 4) != 0 || zip.u16(end + 6) != 0 ||
            zip.u16(end + 8) != count || count !in 1..1024 ||
            directoryOffset + directorySize != end.toLong()
        ) fail("Unsupported ZIP layout, ZIP64, or too many entries (maximum 1024).")
        var cursor = directoryOffset.toInt()
        var totalSize = 0L
        val entries = linkedMapOf<String, Entry>()
        repeat(count) {
            yield()
            if (zip.u32(cursor) != 0x02014b50L) fail("Invalid ZIP directory.")
            val flags = zip.u16(cursor + 8)
            val method = zip.u16(cursor + 10)
            val crc = zip.u32(cursor + 16)
            val compressed = zip.u32(cursor + 20)
            val expanded = zip.u32(cursor + 24)
            val nameLength = zip.u16(cursor + 28)
            val extraLength = zip.u16(cursor + 30)
            val commentLength = zip.u16(cursor + 32)
            val localOffset = zip.u32(cursor + 42)
            if (flags and 1 != 0 || method !in listOf(0, 8) ||
                compressed > MAX_ARCHIVE_BYTES || expanded > MAX_EXPANDED_BYTES ||
                localOffset >= directoryOffset || zip.u16(cursor + 34) != 0
            ) fail("Encrypted, oversized, or unsupported ZIP entry.")
            val name = zip.slice(cursor + 46, nameLength).decodeToString()
            val components = name.trimEnd('/').split('/')
            if (name.isBlank() || name.startsWith('/') || '\\' in name ||
                ':' in name || '\u0000' in name ||
                components.any { it == ".." || it == "." || it.isEmpty() }
            ) fail("ZIP contains an unsafe entry path.")
            totalSize += expanded
            if (totalSize > MAX_EXPANDED_BYTES) fail("ZIP exceeds the 64 MiB expanded limit.")
            if (entries.put(name, Entry(name, method, flags, crc, compressed.toInt(), expanded.toInt(), localOffset.toInt())) != null) {
                fail("ZIP contains duplicate entry names.")
            }
            cursor += 46 + nameLength + extraLength + commentLength
            if (cursor > end) fail("Invalid ZIP directory bounds.")
        }
        if (cursor.toLong() != directoryOffset + directorySize) fail("Invalid ZIP directory size.")
        val candidates = entries.keys.filter { it.substringAfterLast('/') == "proc_ps.log" }
        if (candidates.size != 1) fail("ZIP must contain exactly one proc_ps.log.")
        val processPath = candidates.single()
        val prefix = processPath.removeSuffix("proc_ps.log")
        val header = entries[prefix + "header"] ?: fail("Missing header alongside proc_ps.log.")

        suspend fun text(entry: Entry): String {
            val local = entry.offset
            if (zip.u32(local) != 0x04034b50L ||
                zip.u16(local + 6) != entry.flags || zip.u16(local + 8) != entry.method
            ) fail("Invalid ZIP local entry.")
            val nameLength = zip.u16(local + 26)
            val extraLength = zip.u16(local + 28)
            if (zip.slice(local + 30, nameLength).decodeToString() != entry.name) {
                fail("ZIP entry names do not match.")
            }
            val dataOffset = local.toLong() + 30 + nameLength + extraLength
            if (dataOffset + entry.compressed > directoryOffset) fail("Invalid ZIP data bounds.")
            val packed = zip.slice(dataOffset.toInt(), entry.compressed)
            val unpacked = if (entry.method == 0) packed else inflateZipEntry(packed, entry.expanded)
            if (unpacked.size != entry.expanded || crc32(unpacked) != entry.crc) {
                fail("ZIP entry is damaged (size or checksum mismatch).")
            }
            return try {
                unpacked.decodeToString(throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                fail("Bootchart logs must be UTF-8 text.")
            }
        }
        return BootchartLogs(
            text(header), text(entries.getValue(processPath)),
            entries[prefix + "proc_stat.log"]?.let { text(it) } ?: "",
            entries[prefix + "proc_diskstats.log"]?.let { text(it) } ?: "",
        )
    }

    private data class Entry(
        val name: String, val method: Int, val flags: Int, val crc: Long,
        val compressed: Int, val expanded: Int, val offset: Int,
    )

    private class ZipBytes(private val bytes: ByteArray) {
        fun u16(offset: Int): Int =
            slice(offset, 2).let { (it[0].toInt() and 255) or ((it[1].toInt() and 255) shl 8) }
        fun u32(offset: Int): Long {
            if (offset < 0 || offset.toLong() + 4 > bytes.size) fail("Truncated ZIP archive.")
            var value = 0L
            repeat(4) { value = value or ((bytes[offset + it].toLong() and 255) shl (8 * it)) }
            return value
        }
        fun slice(offset: Int, size: Int): ByteArray {
            if (offset < 0 || size < 0 || offset.toLong() + size > bytes.size) fail("Truncated ZIP archive.")
            return bytes.copyOfRange(offset, offset + size)
        }
    }

    private suspend fun crc32(bytes: ByteArray): Long {
        var crc = -1
        bytes.forEachIndexed { index, byte ->
            if (index % 16384 == 0) yield()
            crc = crc xor (byte.toInt() and 255)
            repeat(8) { crc = (crc ushr 1) xor (if (crc and 1 != 0) 0xedb88320.toInt() else 0) }
        }
        return (crc xor -1).toLong() and 0xffffffffL
    }
}

private fun fail(message: String): Nothing = throw BootchartImportException(message)
