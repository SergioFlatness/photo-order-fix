package io.github.sergioflatness.photoorderfix.core

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateResolverTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val now = ms(2026, 10, 16, 12, 0, 0)

    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(seoul).toInstant().toEpochMilli()

    @Test
    fun `capture date wins when present`() {
        val taken = ms(2015, 5, 1, 9, 0, 0)
        val r = DateResolver.resolve(taken, "IMG_20240315_143022.jpg", 1_760_000_000, seoul, now)
        assertEquals(ResolvedDate(taken, DateSource.TAKEN), r)
    }

    @Test
    fun `falls back to the file name`() {
        val r = DateResolver.resolve(null, "IMG_20240315_143022.jpg", 1_760_000_000, seoul, now)
        assertEquals(ResolvedDate(ms(2024, 3, 15, 14, 30, 22), DateSource.FILENAME), r)
    }

    @Test
    fun `ignores implausible capture dates`() {
        val r = DateResolver.resolve(0L, "Screenshot_20230102-030405.png", 1_760_000_000, seoul, now)
        assertEquals(DateSource.FILENAME, r.source)
        assertEquals(ms(2023, 1, 2, 3, 4, 5), r.epochMs)
    }

    @Test
    fun `iPhone style names fall back to modified time`() {
        val r = DateResolver.resolve(null, "IMG_1234.PNG", 1_760_000_000, seoul, now)
        assertEquals(ResolvedDate(1_760_000_000_000, DateSource.MODIFIED), r)
    }

    @Test
    fun `parses common naming schemes`() {
        assertEquals(ms(2024, 3, 15, 14, 30, 22), DateResolver.parseFileName("20240315_143022.mp4", seoul))
        assertEquals(ms(2022, 12, 31, 23, 59, 58), DateResolver.parseFileName("PXL_20221231_235958123.jpg", seoul))
        assertEquals(ms(2021, 7, 4, 12, 0, 0), DateResolver.parseFileName("IMG-20210704-WA0007.jpg", seoul))
        assertEquals(ms(2020, 1, 2, 3, 4, 5), DateResolver.parseFileName("Screenshot 2020-01-02 at 03.04.05.png", seoul))
        assertEquals(1_700_000_000_000, DateResolver.parseFileName("received_1700000000000.jpeg", seoul))
    }

    @Test
    fun `rejects impossible dates`() {
        assertNull(DateResolver.parseFileName("IMG_20241345_999999.jpg", seoul))
        assertNull(DateResolver.parseFileName("IMG_1234.HEIC", seoul))
    }
}
