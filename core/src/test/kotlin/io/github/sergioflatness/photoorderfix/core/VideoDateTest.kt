package io.github.sergioflatness.photoorderfix.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoDateTest {

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val body = parts.fold(ByteArray(0)) { acc, p -> acc + p }
        return ByteBuffer.allocate(8 + body.size).putInt(8 + body.size).put(type.toByteArray(Charsets.ISO_8859_1)).put(body).array()
    }

    private fun int(v: Int) = ByteBuffer.allocate(4).putInt(v).array()

    private fun read(file: ByteArray): Long? =
        VideoDate.read({ pos, buf, len ->
            if (pos >= file.size) -1 else {
                val n = minOf(len, file.size - pos.toInt())
                System.arraycopy(file, pos.toInt(), buf, 0, n)
                n
            }
        }, file.size.toLong())

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private val mdat = box("mdat", ByteArray(100))

    private fun mvhd(secs1904: Long) = box("mvhd", int(0), int(secs1904.toInt()), int(0), ByteArray(88))

    @Test
    fun `iPhone creationdate key is read even when moov comes after the media data`() {
        val key = "com.apple.quicktime.creationdate".toByteArray()
        val keys = box("keys", int(0), int(1), int(8 + key.size), "mdta".toByteArray(), key)
        val value = "2019-08-03T14:22:10+0900".toByteArray()
        val ilst = box("ilst", box("\u0000\u0000\u0000\u0001", box("data", int(1), int(0), value)))
        val meta = box("meta", box("hdlr", ByteArray(25)), keys, ilst)
        val file = box("ftyp", "qt  ".toByteArray()) + mdat + box("moov", mvhd(0), meta)
        assertEquals(ms("2019-08-03T05:22:10Z"), read(file))
    }

    @Test
    fun `quicktime day tag is used when there is no creationdate key`() {
        val text = "2020-01-02T03:04:05Z".toByteArray()
        val day = box("©day", byteArrayOf(0, text.size.toByte(), 0x15, 0xC7.toByte()), text)
        val file = mdat + box("moov", box("udta", day))
        assertEquals(ms("2020-01-02T03:04:05Z"), read(file))
    }

    @Test
    fun `movie header time is the last resort`() {
        // 2021-06-01T00:00:00Z in seconds since 1904.
        val secs = Instant.parse("2021-06-01T00:00:00Z").epochSecond + 2_082_844_800L
        val file = mdat + box("moov", mvhd(secs))
        assertEquals(ms("2021-06-01T00:00:00Z"), read(file))
    }

    @Test
    fun `no date and broken files give null`() {
        assertNull(read(mdat + box("moov", mvhd(0))))
        assertNull(read(ByteArray(0)))
        assertNull(read(byteArrayOf(0, 0, 0, 50, 'm'.code.toByte())))
    }

    @Test
    fun `iso offsets in both styles`() {
        assertEquals(ms("2019-08-03T05:22:10Z"), VideoDate.parseIso("2019-08-03T14:22:10+09:00"))
        assertEquals(ms("2019-08-03T05:22:10Z"), VideoDate.parseIso("2019-08-03T14:22:10+0900"))
        assertNull(VideoDate.parseIso("not a date"))
    }
}
