package io.github.sergioflatness.photoorderfix.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExifDateTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `date without a zone is read as local time`() {
        assertEquals(ms("2019-08-03T05:22:10Z"), ExifDate.parse("2019:08:03 14:22:10", null, seoul))
    }

    @Test
    fun `zone tag wins over the phone zone`() {
        assertEquals(ms("2019-08-03T14:22:10Z"), ExifDate.parse("2019:08:03 14:22:10", "+00:00", seoul))
    }

    @Test
    fun `blank or broken dates are ignored`() {
        assertNull(ExifDate.parse(null, null, seoul))
        assertNull(ExifDate.parse("    :  :     :  :  ", null, seoul))
        assertNull(ExifDate.parse("2019:13:40 99:00:00", null, seoul))
    }

    @Test
    fun `written date and zone read back to the same moment`() {
        val t = ms("2023-01-02T03:04:05Z")
        val date = ExifDate.formatDate(t, seoul)
        val offset = ExifDate.formatOffset(t, seoul)
        assertEquals("2023:01:02 12:04:05", date)
        assertEquals("+09:00", offset)
        assertEquals(t, ExifDate.parse(date, offset, ZoneId.of("UTC")))
        assertEquals("+00:00", ExifDate.formatOffset(t, ZoneId.of("UTC")))
    }
}
