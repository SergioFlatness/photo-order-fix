package io.github.sergioflatness.photoorderfix.core

import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** Random access to a file's bytes, so the parser works on Android files and in tests. */
fun interface ByteSource {
    /** Reads up to [len] bytes at [pos] into [buf]; returns the count read, or -1 at the end. */
    fun read(pos: Long, buf: ByteArray, len: Int): Int
}

/**
 * Reads the capture date stored inside an MP4 / QuickTime (.mov) video.
 *
 * iPhones write it as the `com.apple.quicktime.creationdate` metadata key, which Android's
 * media scanner does not read, so the Gallery falls back to the file time. Also checks the
 * `©day` tag and the movie header (`mvhd`) creation time.
 */
object VideoDate {

    private const val SECONDS_1904_TO_1970 = 2_082_844_800L
    private const val MAX_BOX_READ = 1 shl 20

    private val isoPattern = Regex(
        """^\s*(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?\s*(Z|[+-]\d{2}:?\d{2})?"""
    )

    fun read(source: ByteSource, size: Long): Long? {
        val moov = findChild(source, 0, size, "moov") ?: return null
        val apple = findAppleCreationDate(source, moov)
        if (apple != null) return apple
        findChild(source, moov.bodyStart, moov.end, "udta")?.let { udta ->
            findChild(source, udta.bodyStart, udta.end, "©day")?.let { day ->
                readQuickTimeString(source, day)?.let { parseIso(it) }?.let { return it }
            }
        }
        return findChild(source, moov.bodyStart, moov.end, "mvhd")?.let { readMvhd(source, it) }
    }

    /** Parses ISO 8601 like `2019-08-03T14:22:10+0900`. A date without an offset is taken as UTC. */
    fun parseIso(text: String): Long? {
        val m = isoPattern.find(text) ?: return null
        val (y, mo, d, h, mi) = m.destructured
        val s = m.groupValues[6].ifEmpty { "0" }
        val offsetText = m.groupValues[7]
        return runCatching {
            val offset = when {
                offsetText.isEmpty() || offsetText == "Z" -> ZoneOffset.UTC
                else -> ZoneOffset.of(
                    if (offsetText.contains(':')) offsetText
                    else offsetText.substring(0, 3) + ":" + offsetText.substring(3)
                )
            }
            LocalDateTime.of(LocalDate.of(y.toInt(), mo.toInt(), d.toInt()), LocalTime.of(h.toInt(), mi.toInt(), s.toInt()))
                .toInstant(offset).toEpochMilli()
        }.getOrNull()
    }

    private class Box(val type: String, val start: Long, val bodyStart: Long, val end: Long)

    private fun readBytes(source: ByteSource, pos: Long, len: Int): ByteArray? {
        if (len < 0 || len > MAX_BOX_READ) return null
        val buf = ByteArray(len)
        var done = 0
        val chunk = ByteArray(minOf(len, 64 * 1024).coerceAtLeast(1))
        while (done < len) {
            val n = source.read(pos + done, chunk, minOf(chunk.size, len - done))
            if (n <= 0) return null
            System.arraycopy(chunk, 0, buf, done, n)
            done += n
        }
        return buf
    }

    private fun boxAt(source: ByteSource, pos: Long, limit: Long): Box? {
        if (limit - pos < 8) return null
        val head = readBytes(source, pos, 8) ?: return null
        val bb = ByteBuffer.wrap(head)
        var size = bb.int.toLong() and 0xFFFFFFFFL
        val type = String(head, 4, 4, Charsets.ISO_8859_1)
        var header = 8L
        if (size == 1L) {
            val large = readBytes(source, pos + 8, 8) ?: return null
            size = ByteBuffer.wrap(large).long
            header = 16
        } else if (size == 0L) {
            size = limit - pos
        }
        if (size < header || pos + size > limit) return null
        return Box(type, pos, pos + header, pos + size)
    }

    private fun children(source: ByteSource, from: Long, to: Long): Sequence<Box> = sequence {
        var pos = from
        while (true) {
            val box = boxAt(source, pos, to) ?: break
            yield(box)
            pos = box.end
        }
    }

    private fun findChild(source: ByteSource, from: Long, to: Long, type: String): Box? =
        children(source, from, to).firstOrNull { it.type == type }

    private fun findAppleCreationDate(source: ByteSource, moov: Box): Long? {
        val metas = buildList {
            findChild(source, moov.bodyStart, moov.end, "meta")?.let { add(it) }
            findChild(source, moov.bodyStart, moov.end, "udta")?.let { udta ->
                findChild(source, udta.bodyStart, udta.end, "meta")?.let { add(it) }
            }
        }
        for (meta in metas) {
            // QuickTime 'meta' has no version/flags; ISO 'meta' has 4 bytes of them.
            val body = listOf(meta.bodyStart, meta.bodyStart + 4).firstOrNull { start ->
                val first = boxAt(source, start, meta.end)
                first != null && first.type in setOf("hdlr", "keys", "ilst")
            } ?: continue
            val keys = findChild(source, body, meta.end, "keys") ?: continue
            val ilst = findChild(source, body, meta.end, "ilst") ?: continue
            val index = readKeys(source, keys).indexOf("com.apple.quicktime.creationdate")
            if (index < 0) continue
            for (item in children(source, ilst.bodyStart, ilst.end)) {
                val raw = item.type.toByteArray(Charsets.ISO_8859_1)
                if (ByteBuffer.wrap(raw).int != index + 1) continue
                val data = findChild(source, item.bodyStart, item.end, "data") ?: continue
                // data box: 4 bytes type, 4 bytes locale, then the value.
                val len = (data.end - data.bodyStart - 8).toInt()
                val value = readBytes(source, data.bodyStart + 8, len) ?: continue
                parseIso(String(value, Charsets.UTF_8))?.let { return it }
            }
        }
        return null
    }

    private fun readKeys(source: ByteSource, keys: Box): List<String> {
        val bytes = readBytes(source, keys.bodyStart, (keys.end - keys.bodyStart).toInt()) ?: return emptyList()
        val bb = ByteBuffer.wrap(bytes)
        if (bb.remaining() < 8) return emptyList()
        bb.int // version + flags
        val count = bb.int
        val result = ArrayList<String>()
        repeat(count) {
            if (bb.remaining() < 8) return result
            val keySize = bb.int
            if (keySize < 8 || keySize - 4 > bb.remaining()) return result
            bb.int // namespace, e.g. 'mdta'
            val value = ByteArray(keySize - 8)
            bb.get(value)
            result += String(value, Charsets.UTF_8)
        }
        return result
    }

    private fun readQuickTimeString(source: ByteSource, box: Box): String? {
        val bytes = readBytes(source, box.bodyStart, (box.end - box.bodyStart).toInt()) ?: return null
        if (bytes.size < 4) return null
        val len = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        if (4 + len > bytes.size) return null
        return String(bytes, 4, len, Charsets.UTF_8)
    }

    private fun readMvhd(source: ByteSource, box: Box): Long? {
        val bytes = readBytes(source, box.bodyStart, 12) ?: return null
        val bb = ByteBuffer.wrap(bytes)
        val version = bb.get().toInt()
        bb.position(4)
        val secs1904 = if (version == 1) bb.long else bb.int.toLong() and 0xFFFFFFFFL
        if (secs1904 <= 0) return null
        return (secs1904 - SECONDS_1904_TO_1970) * 1000
    }
}
