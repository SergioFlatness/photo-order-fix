package io.github.sergioflatness.photoorderfix.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Where a photo's "real" date came from, best source first. */
enum class DateSource { TAKEN, FILENAME, MODIFIED }

data class ResolvedDate(val epochMs: Long, val source: DateSource)

/**
 * Finds the moment a photo or video was actually captured.
 *
 * Order of trust:
 * 1. The capture date Android read from the file's own metadata (EXIF for photos,
 *    the container header for videos).
 * 2. A date written in the file name, such as `IMG_20240315_143022.jpg`.
 * 3. The file's last-modified time, which is often just the transfer time.
 */
object DateResolver {

    // 1990-01-01, anything earlier is almost certainly a bogus camera clock.
    private const val MIN_PLAUSIBLE_MS = 631_152_000_000L

    private val dateTimePattern = Regex(
        """(?<!\d)((?:19|20)\d{2})[-_.]?(\d{2})[-_.]?(\d{2})(?:[-_ T.]|\s+at\s+)?(\d{2})[-_.:]?(\d{2})[-_.:]?(\d{2})(?:\d{3})?(?!\d)"""
    )
    private val dateOnlyPattern = Regex("""(?<!\d)((?:19|20)\d{2})[-_.]?(\d{2})[-_.]?(\d{2})(?!\d)""")
    private val epochMsPattern = Regex("""(?<!\d)(1[3-9]\d{11})(?!\d)""")

    fun resolve(
        takenMs: Long?,
        fileName: String,
        modifiedSec: Long,
        zone: ZoneId,
        nowMs: Long = System.currentTimeMillis(),
    ): ResolvedDate {
        if (takenMs != null && isPlausible(takenMs, nowMs)) {
            return ResolvedDate(takenMs, DateSource.TAKEN)
        }
        parseFileName(fileName, zone)?.takeIf { isPlausible(it, nowMs) }?.let {
            return ResolvedDate(it, DateSource.FILENAME)
        }
        return ResolvedDate(modifiedSec * 1000, DateSource.MODIFIED)
    }

    fun parseFileName(fileName: String, zone: ZoneId): Long? {
        val base = fileName.substringBeforeLast('.')

        dateTimePattern.find(base)?.let { m ->
            val (y, mo, d, h, mi, s) = m.destructured
            toEpochMs(y, mo, d, h, mi, s, zone)?.let { return it }
        }
        epochMsPattern.find(base)?.let { m ->
            return m.groupValues[1].toLong()
        }
        dateOnlyPattern.find(base)?.let { m ->
            val (y, mo, d) = m.destructured
            // No time in the name: use noon so the photo lands in the middle of its day.
            toEpochMs(y, mo, d, "12", "00", "00", zone)?.let { return it }
        }
        return null
    }

    private fun toEpochMs(
        y: String, mo: String, d: String, h: String, mi: String, s: String, zone: ZoneId,
    ): Long? = runCatching {
        LocalDateTime.of(
            LocalDate.of(y.toInt(), mo.toInt(), d.toInt()),
            LocalTime.of(h.toInt(), mi.toInt(), s.toInt()),
        ).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    private fun isPlausible(ms: Long, nowMs: Long): Boolean =
        ms >= MIN_PLAUSIBLE_MS && ms <= nowMs + 2L * 24 * 60 * 60 * 1000
}
