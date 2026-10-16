package io.github.sergioflatness.photoorderfix

import android.content.ContentResolver
import android.net.Uri
import io.github.sergioflatness.photoorderfix.core.TakeoutEntry
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads photo dates from a Google Takeout export: the .zip files themselves, or .json files taken out of them.
 *
 * Each photo in Takeout comes with a .json file holding "title" (the file name) and
 * "photoTakenTime.timestamp" (seconds since 1970, UTC).
 */
object TakeoutReader {

    // Takeout metadata files are a few KB; anything bigger is not one of them.
    private const val MAX_JSON_BYTES = 256 * 1024

    fun read(resolver: ContentResolver, uris: List<Uri>): List<TakeoutEntry> {
        val result = ArrayList<TakeoutEntry>()
        for (uri in uris) {
            resolver.openInputStream(uri)?.buffered()?.use { input ->
                input.mark(4)
                val zip = ByteArray(4).let { input.read(it) == 4 && it[0] == 'P'.code.toByte() && it[1] == 'K'.code.toByte() }
                input.reset()
                if (zip) readZip(input, result) else readLimited(input)?.let { parse(it) }?.let(result::add)
            }
        }
        return result
    }

    private fun readZip(input: InputStream, out: MutableList<TakeoutEntry>) {
        val zip = ZipInputStream(input)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                readLimited(zip)?.let { parse(it) }?.let(out::add)
            }
        }
    }

    private fun readLimited(input: InputStream): String? {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            buf.write(chunk, 0, n)
            if (buf.size() > MAX_JSON_BYTES) return null
        }
        return buf.toString(Charsets.UTF_8.name())
    }

    private fun parse(text: String): TakeoutEntry? = runCatching {
        val json = JSONObject(text)
        val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null
        val seconds = json.optJSONObject("photoTakenTime")?.optString("timestamp")?.toLongOrNull() ?: return null
        TakeoutEntry(title, seconds * 1000)
    }.getOrNull()
}
