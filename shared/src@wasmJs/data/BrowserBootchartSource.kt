package com.droid.bootchart.data

import com.droid.bootchart.domain.BootchartImportException
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString
import kotlinx.coroutines.await
import kotlinx.coroutines.yield

internal actual fun createBootchartSource(): BootchartSource = BrowserBootchartSource()

private class BrowserBootchartSource : BootchartSource {
    override suspend fun select(): SelectedArchive? {
        val result = pickArchive(MAX_ARCHIVE_BYTES).await() ?: return null
        checkError(result)
        return SelectedArchive(property(result, "name").toString(), decodeBytes(property(result, "bytes")))
    }
}

internal actual suspend fun inflateZipEntry(bytes: ByteArray, expectedSize: Int): ByteArray {
    val binary = buildString(bytes.size) {
        bytes.forEach { append((it.toInt() and 255).toChar()) }
    }
    val result = inflate(binary.toJsString(), expectedSize).await()
    checkError(result)
    return decodeBytes(property(result, "bytes"))
}

private fun checkError(result: JsAny) {
    val error = property(result, "error").toString()
    if (error.isNotEmpty()) throw BootchartImportException(error)
}

private suspend fun decodeBytes(value: JsString): ByteArray {
    val binary = value.toString()
    val bytes = ByteArray(binary.length)
    for (index in binary.indices) {
        if (index % 16384 == 0) yield()
        bytes[index] = binary[index].code.toByte()
    }
    return bytes
}

@JsFun("(value, key) => value[key] || ''")
private external fun property(value: JsAny, key: String): JsString

@JsFun("""(limit) => new Promise(resolve => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.zip,application/zip,application/x-zip-compressed';
    input.style.display = 'none';
    const finish = value => { input.remove(); resolve(value); };
    input.addEventListener('cancel', () => finish(null), { once: true });
    input.addEventListener('change', async () => {
        const file = input.files[0];
        if (!file) { finish(null); return; }
        if (file.size > limit) {
            finish({ error: 'ZIP exceeds the 32 MiB import limit.' }); return;
        }
        try {
            const data = new Uint8Array(await file.arrayBuffer());
            const chunks = [];
            for (let i = 0; i < data.length; i += 8192) {
                chunks.push(String.fromCharCode(...data.subarray(i, i + 8192)));
            }
            finish({ name: file.name, bytes: chunks.join('') });
        } catch (error) {
            finish({ error: 'Cannot read the selected file. Please select it again.' });
        }
    }, { once: true });
    document.body.appendChild(input);
    input.click();
})""")
private external fun pickArchive(limit: Int): Promise<JsAny?>

@JsFun("""async (binary, limit) => {
    if (typeof DecompressionStream === 'undefined') {
        return { error: 'This browser cannot decompress ZIP files. Please use a current browser or the desktop app.' };
    }
    try {
        const data = Uint8Array.from(binary, c => c.charCodeAt(0));
        const reader = new Blob([data]).stream()
            .pipeThrough(new DecompressionStream('deflate-raw')).getReader();
        let length = 0;
        const chunks = [];
        while (true) {
            const next = await reader.read();
            if (next.done) break;
            length += next.value.length;
            if (length > limit) {
                await reader.cancel();
                return { error: 'ZIP entry expands beyond its declared size.' };
            }
            for (let i = 0; i < next.value.length; i += 8192) {
                chunks.push(String.fromCharCode(...next.value.subarray(i, i + 8192)));
            }
        }
        return { bytes: chunks.join('') };
    } catch (error) {
        return { error: 'Cannot decompress this ZIP. It may be damaged, or this browser may not support raw deflate. Try a current browser or the desktop app.' };
    }
}""")
private external fun inflate(binary: JsString, limit: Int): Promise<JsAny>
